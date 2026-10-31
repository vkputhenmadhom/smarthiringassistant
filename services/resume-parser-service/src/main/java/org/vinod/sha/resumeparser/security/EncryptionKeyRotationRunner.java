package org.vinod.sha.resumeparser.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.vinod.sha.resumeparser.security.SensitiveDataEncryptionService.Decrypted;
import org.vinod.sha.resumeparser.security.SensitiveDataEncryptionService.KeyMatch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-encrypts data written under a previous key with the current key, at startup, whenever previous keys
 * are configured. It works on the raw columns: JPA would not rewrite them, because the decrypted values
 * it compares are unchanged. Safe to run repeatedly; once it reports nothing left to re-encrypt, the
 * previous keys can be removed.
 */
@Slf4j
@Component
public class EncryptionKeyRotationRunner implements ApplicationRunner {

    static final String RESUMES = "hiring.resume_parser_resumes";
    static final String PARSED_DATA = "hiring.resume_parser_parsed_data";
    static final List<String> PARSED_COLUMNS = List.of("full_name", "email", "phone", "location", "summary", "preferred_locations");

    private final SensitiveDataEncryptionService encryption;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public EncryptionKeyRotationRunner(SensitiveDataEncryptionService encryption, JdbcTemplate jdbc,
                                       TransactionTemplate transaction) {
        this.encryption = encryption;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public record Result(int reEncrypted, int current, int unreadable) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!encryption.hasPreviousKeys()) {
            return;
        }
        Result resumes = transaction.execute(status -> rotateResumeFiles());
        Result parsed = transaction.execute(status -> rotateParsedData());
        log.info("Resume encryption key rotation: files {} | parsed fields {}", resumes, parsed);
        if (resumes.reEncrypted() + parsed.reEncrypted() == 0 && resumes.unreadable() + parsed.unreadable() == 0) {
            log.info("Nothing is encrypted under a previous key any more; RESUME_ENCRYPTION_PREVIOUS_KEYS can be removed");
        }
    }

    Result rotateResumeFiles() {
        int reEncrypted = 0, current = 0, unreadable = 0;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, file_content FROM " + RESUMES);
        for (Map<String, Object> row : rows) {
            Decrypted decrypted = encryption.decrypt((byte[]) row.get("file_content"));
            switch (decrypted.match()) {
                case CURRENT -> current++;
                case NONE -> unreadable++;
                case PREVIOUS -> {
                    jdbc.update("UPDATE " + RESUMES + " SET file_content = ? WHERE id = ?",
                            encryption.encryptBytes(decrypted.plain()), row.get("id"));
                    reEncrypted++;
                }
            }
        }
        return new Result(reEncrypted, current, unreadable);
    }

    Result rotateParsedData() {
        int reEncrypted = 0, current = 0, unreadable = 0;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, " + String.join(", ", PARSED_COLUMNS) + " FROM " + PARSED_DATA);
        for (Map<String, Object> row : rows) {
            Map<String, String> updates = new LinkedHashMap<>();
            for (String column : PARSED_COLUMNS) {
                String stored = (String) row.get(column);
                if (stored == null || stored.isBlank()) {
                    continue;
                }
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(stored);
                } catch (IllegalArgumentException notEncrypted) {
                    continue; // plaintext from before encryption existed
                }
                Decrypted decrypted = encryption.decrypt(bytes);
                switch (decrypted.match()) {
                    case CURRENT -> current++;
                    case NONE -> unreadable++;
                    case PREVIOUS -> updates.put(column,
                            encryption.encryptString(new String(decrypted.plain(), StandardCharsets.UTF_8)));
                }
            }
            if (!updates.isEmpty()) {
                List<Object> params = new ArrayList<>(updates.values());
                params.add(row.get("id"));
                String assignments = String.join(", ", updates.keySet().stream().map(c -> c + " = ?").toList());
                jdbc.update("UPDATE " + PARSED_DATA + " SET " + assignments + " WHERE id = ?", params.toArray());
                reEncrypted += updates.size();
            }
        }
        return new Result(reEncrypted, current, unreadable);
    }
}
