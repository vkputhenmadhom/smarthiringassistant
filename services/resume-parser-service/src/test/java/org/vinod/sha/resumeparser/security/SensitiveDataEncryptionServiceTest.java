package org.vinod.sha.resumeparser.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.vinod.sha.resumeparser.security.SensitiveDataEncryptionService.KeyMatch;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataEncryptionServiceTest {

    static final String OLD_KEY = SensitiveDataEncryptionService.RETIRED_DEFAULT_KEY;
    static final String NEW_KEY = "new-key-0123456789-0123456789-0123456789-abcd";

    static SensitiveDataEncryptionService service(String key, String previousKeys) {
        SensitiveDataEncryptionService service = new SensitiveDataEncryptionService();
        ReflectionTestUtils.setField(service, "encryptionKey", key);
        ReflectionTestUtils.setField(service, "previousEncryptionKeys", previousKeys);
        service.init();
        return service;
    }

    /** Data written the way the old release wrote it: with the public default key. */
    static String encryptedUnderOldKey(String plain) {
        SensitiveDataEncryptionService old = new SensitiveDataEncryptionService();
        ReflectionTestUtils.setField(old, "encryptionKey", OLD_KEY);
        ReflectionTestUtils.setField(old, "previousEncryptionKeys", "");
        // bypass the startup guard: this reproduces data written by earlier releases
        ReflectionTestUtils.setField(old, "currentKey",
                ReflectionTestUtils.invokeMethod(SensitiveDataEncryptionService.class, "deriveKey", OLD_KEY));
        return old.encryptString(plain);
    }

    @Test
    void refusesMissingShortOrPublicDefaultKeys() {
        assertThrows(IllegalStateException.class, () -> service(null, ""));
        assertThrows(IllegalStateException.class, () -> service("too-short", ""));
        assertThrows(IllegalStateException.class, () -> service(OLD_KEY, ""));
    }

    @Test
    void roundTripsWithTheCurrentKey() {
        SensitiveDataEncryptionService service = service(NEW_KEY, "");
        String stored = service.encryptString("jane@example.com");

        assertNotEquals("jane@example.com", stored);
        assertEquals("jane@example.com", service.decryptString(stored));
    }

    @Test
    void readsDataWrittenUnderAPreviousKey() {
        String stored = encryptedUnderOldKey("Jane Doe");
        SensitiveDataEncryptionService service = service(NEW_KEY, OLD_KEY);

        assertEquals("Jane Doe", service.decryptString(stored));
        assertEquals(KeyMatch.PREVIOUS, service.decrypt(java.util.Base64.getDecoder().decode(stored)).match());
    }

    @Test
    void neverEncryptsWithAPreviousKey() {
        SensitiveDataEncryptionService rotated = service(NEW_KEY, OLD_KEY);
        String stored = rotated.encryptString("Jane Doe");

        // readable with the new key alone, so it was not written with the old one
        assertEquals("Jane Doe", service(NEW_KEY, "").decryptString(stored));
    }

    @Test
    void withoutThePreviousKeyOldDataIsReportedUnreadableNotTurnedIntoGarbage() {
        String stored = encryptedUnderOldKey("Jane Doe");
        SensitiveDataEncryptionService service = service(NEW_KEY, "");

        assertEquals(KeyMatch.NONE, service.decrypt(java.util.Base64.getDecoder().decode(stored)).match());
        assertEquals(stored, service.decryptString(stored));
    }

    @Test
    void returnsLegacyPlaintextUnchanged() {
        SensitiveDataEncryptionService service = service(NEW_KEY, "");

        assertEquals("Pune, India", service.decryptString("Pune, India"));
        byte[] shortBytes = "abc".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(shortBytes, service.decryptBytes(shortBytes));
    }
}
