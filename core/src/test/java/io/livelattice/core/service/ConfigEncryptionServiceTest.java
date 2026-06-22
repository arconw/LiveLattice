package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConfigEncryptionServiceTest {

    @Test
    void encryptAndDecrypt_shouldBeReversible() throws Exception {
        ConfigEncryptionService service = new ConfigEncryptionService();
        service.init();

        String plainText = "my-secret-password";
        String encrypted = service.encrypt(plainText);
        String decrypted = service.decrypt(encrypted);

        assertNotEquals(plainText, encrypted);
        assertEquals(plainText, decrypted);
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertexts() throws Exception {
        ConfigEncryptionService service = new ConfigEncryptionService();
        service.init();

        String plainText = "secret";
        String encrypted1 = service.encrypt(plainText);
        String encrypted2 = service.encrypt(plainText);

        assertNotEquals(encrypted1, encrypted2);
    }
}
