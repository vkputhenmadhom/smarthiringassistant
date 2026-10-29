package org.vinod.sha.resumeparser.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.vinod.sha.resumeparser.security.SensitiveDataEncryptionServiceTest.*;

class EncryptionKeyRotationRunnerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SensitiveDataEncryptionService rotated = service(NEW_KEY, OLD_KEY);
    private final SensitiveDataEncryptionService newKeyOnly = service(NEW_KEY, "");
    private final EncryptionKeyRotationRunner runner =
            new EncryptionKeyRotationRunner(rotated, jdbc, mock(TransactionTemplate.class));

    @Test
    void reEncryptsResumeFilesWrittenUnderThePreviousKey() {
        byte[] oldCipher = java.util.Base64.getDecoder().decode(encryptedUnderOldKey("resume text"));
        byte[] currentCipher = rotated.encryptBytes("already current".getBytes(StandardCharsets.UTF_8));
        when(jdbc.queryForList(startsWith("SELECT id, file_content"))).thenReturn(List.of(
                row("id", 1L, "file_content", oldCipher),
                row("id", 2L, "file_content", currentCipher)));

        EncryptionKeyRotationRunner.Result result = runner.rotateResumeFiles();

        assertEquals(new EncryptionKeyRotationRunner.Result(1, 1, 0), result);
        ArgumentCaptor<Object> written = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("SET file_content = ?"), written.capture(), eq(1L));
        assertEquals("resume text", new String(newKeyOnly.decryptBytes((byte[]) written.getValue()), StandardCharsets.UTF_8));
    }

    @Test
    void reEncryptsOnlyTheParsedFieldsUnderThePreviousKey() {
        when(jdbc.queryForList(startsWith("SELECT id, full_name"))).thenReturn(List.of(row(
                "id", 5L,
                "full_name", encryptedUnderOldKey("Jane Doe"),
                "email", rotated.encryptString("jane@example.com"),
                "phone", null,
                "location", "Pune",            // legacy plaintext: left alone
                "summary", encryptedUnderOldKey("Backend engineer"))));

        EncryptionKeyRotationRunner.Result result = runner.rotateParsedData();

        assertEquals(2, result.reEncrypted());
        assertEquals(1, result.current());
        ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(eq("UPDATE " + EncryptionKeyRotationRunner.PARSED_DATA
                + " SET full_name = ?, summary = ? WHERE id = ?"), params.capture());
        Object[] values = params.getValue();
        assertEquals("Jane Doe", newKeyOnly.decryptString((String) values[0]));
        assertEquals("Backend engineer", newKeyOnly.decryptString((String) values[1]));
        assertEquals(5L, values[2]);
    }

    @Test
    void reportsDataNoConfiguredKeyCanRead() {
        String unknownKeyCipher = service("another-key-0123456789-0123456789-0123456789", "").encryptString("x y z");
        when(jdbc.queryForList(startsWith("SELECT id, full_name"))).thenReturn(List.of(row(
                "id", 6L, "full_name", unknownKeyCipher, "email", null, "phone", null, "location", null, "summary", null)));

        assertEquals(new EncryptionKeyRotationRunner.Result(0, 0, 1), runner.rotateParsedData());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }
}
