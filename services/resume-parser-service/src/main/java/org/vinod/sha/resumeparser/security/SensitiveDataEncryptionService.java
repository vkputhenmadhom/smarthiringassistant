package org.vinod.sha.resumeparser.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * AES-GCM encryption of sensitive resume data.
 * <p>
 * The current key ({@code RESUME_ENCRYPTION_KEY}) encrypts everything. Optional previous keys
 * ({@code RESUME_ENCRYPTION_PREVIOUS_KEYS}, comma-separated) are only used to decrypt, so data written
 * under an old key stays readable until {@link EncryptionKeyRotationRunner} re-encrypts it.
 */
@Slf4j
@Service
public class SensitiveDataEncryptionService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_SIZE = 12;
    private static final int MIN_KEY_LENGTH = 32;
    /** The value that used to be the built-in default; it is public, so it may only be a previous key. */
    static final String RETIRED_DEFAULT_KEY = "change-me-with-32-plus-char-secret";

    /** How a stored value was read: with the current key, a previous key, or not at all (legacy plaintext). */
    public enum KeyMatch { CURRENT, PREVIOUS, NONE }

    public record Decrypted(byte[] plain, KeyMatch match) {
    }

    private static SensitiveDataEncryptionService instance;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey currentKey;
    private List<SecretKey> previousKeys = List.of();

    @Value("${security.encryption.key}")
    private String encryptionKey;

    @Value("${security.encryption.previous-keys:}")
    private String previousEncryptionKeys;

    @PostConstruct
    void init() {
        if (encryptionKey == null || encryptionKey.length() < MIN_KEY_LENGTH || RETIRED_DEFAULT_KEY.equals(encryptionKey)) {
            throw new IllegalStateException("RESUME_ENCRYPTION_KEY must be a secret of at least " + MIN_KEY_LENGTH
                    + " characters (and not the old public default); generate one with `openssl rand -base64 48`");
        }
        this.currentKey = deriveKey(encryptionKey);
        List<SecretKey> previous = new ArrayList<>();
        Arrays.stream(previousEncryptionKeys == null ? new String[0] : previousEncryptionKeys.split(","))
                .map(String::trim)
                .filter(key -> !key.isEmpty() && !key.equals(encryptionKey))
                .forEach(key -> previous.add(deriveKey(key)));
        this.previousKeys = List.copyOf(previous);
        instance = this;
        if (!previousKeys.isEmpty()) {
            log.info("Resume encryption: {} previous key(s) configured for decryption and re-encryption", previousKeys.size());
        }
    }

    public static SensitiveDataEncryptionService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SensitiveDataEncryptionService is not initialized");
        }
        return instance;
    }

    public boolean hasPreviousKeys() {
        return !previousKeys.isEmpty();
    }

    public String encryptString(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        return Base64.getEncoder().encodeToString(encryptBytes(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    public String decryptString(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return encryptedText;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptedText);
        } catch (IllegalArgumentException ex) {
            // Not Base64: a value stored before encryption was introduced.
            return encryptedText;
        }
        Decrypted result = decrypt(decoded);
        return result.match() == KeyMatch.NONE ? encryptedText : new String(result.plain(), StandardCharsets.UTF_8);
    }

    public byte[] encryptBytes(byte[] plain) {
        if (plain == null || plain.length == 0) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            return ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt sensitive data", ex);
        }
    }

    public byte[] decryptBytes(byte[] encrypted) {
        if (encrypted == null || encrypted.length == 0) {
            return encrypted;
        }
        Decrypted result = decrypt(encrypted);
        return result.match() == KeyMatch.NONE ? encrypted : result.plain();
    }

    /**
     * Tries the current key, then each previous key. {@link KeyMatch#NONE} means no key fits: either a
     * value stored before encryption existed, or data written under a key that is no longer configured.
     */
    public Decrypted decrypt(byte[] encrypted) {
        if (encrypted == null || encrypted.length <= IV_SIZE) {
            return new Decrypted(encrypted, KeyMatch.NONE);
        }
        byte[] plain = tryDecrypt(encrypted, currentKey);
        if (plain != null) {
            return new Decrypted(plain, KeyMatch.CURRENT);
        }
        for (SecretKey key : previousKeys) {
            plain = tryDecrypt(encrypted, key);
            if (plain != null) {
                return new Decrypted(plain, KeyMatch.PREVIOUS);
            }
        }
        log.warn("Sensitive value could not be decrypted with any configured key; returning it unchanged. "
                + "If it was encrypted under an older key, add that key to RESUME_ENCRYPTION_PREVIOUS_KEYS.");
        return new Decrypted(encrypted, KeyMatch.NONE);
    }

    private byte[] tryDecrypt(byte[] encrypted, SecretKey key) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_SIZE];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (AEADBadTagException ex) {
            return null; // wrong key (or not ciphertext at all)
        } catch (Exception ex) {
            log.debug("Decryption attempt failed: {}", ex.getMessage());
            return null;
        }
    }

    private static SecretKey deriveKey(String secret) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to derive encryption key", ex);
        }
    }
}
