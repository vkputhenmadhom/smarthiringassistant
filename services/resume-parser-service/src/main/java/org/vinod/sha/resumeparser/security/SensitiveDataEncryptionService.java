package org.vinod.sha.resumeparser.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class SensitiveDataEncryptionService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_SIZE = 12;

    private static SensitiveDataEncryptionService instance;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    @Value("${security.encryption.key}")
    private String encryptionKey;

    @PostConstruct
    void init() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
            instance = this;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize sensitive data encryption", ex);
        }
    }

    public static SensitiveDataEncryptionService getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SensitiveDataEncryptionService is not initialized");
        }
        return instance;
    }

    public String encryptString(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        byte[] encrypted = encryptBytes(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decryptString(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return encryptedText;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] plain = decryptBytes(decoded);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // Backward compatibility for pre-encryption rows.
            return encryptedText;
        }
    }

    public byte[] encryptBytes(byte[] plain) {
        if (plain == null || plain.length == 0) {
            return plain;
        }

        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return buffer.array();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt sensitive data", ex);
        }
    }

    public byte[] decryptBytes(byte[] encrypted) {
        if (encrypted == null || encrypted.length == 0) {
            return encrypted;
        }

        try {
            if (encrypted.length <= IV_SIZE) {
                return encrypted;
            }

            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_SIZE];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(cipherText);
        } catch (Exception ex) {
            // Backward compatibility for unencrypted historical values.
            log.debug("Decrypt failed; returning original bytes for backward compatibility");
            return encrypted;
        }
    }
}

