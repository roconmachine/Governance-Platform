package com.roconmachine.security.crypto.engine;

import com.roconmachine.security.crypto.config.SecurityCryptoProperties;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptionServiceTest {

    private String randomBase64Key() {
        byte[] key = new byte[32]; // AES-256
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private AesGcmEncryptionService serviceWithKey(String keyId, String base64Key) {
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        properties.setDefaultKeyId(keyId);
        properties.getKeys().put(keyId, base64Key);
        return new AesGcmEncryptionService(new EnvKeyProvider(properties), properties);
    }

    @Test
    void encryptThenDecryptReturnsOriginalPlaintext() {
        AesGcmEncryptionService service = serviceWithKey("default", randomBase64Key());

        String plaintext = "4111111111111234";
        String ciphertext = service.encrypt(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(service.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void sameValueEncryptedTwiceProducesDifferentCiphertext() {
        // proves the IV is genuinely random per call, not reused - IV reuse under
        // GCM is a real key-recovery risk, not just a formatting nicety.
        AesGcmEncryptionService service = serviceWithKey("default", randomBase64Key());

        String plaintext = "4111111111111234";
        String first = service.encrypt(plaintext);
        String second = service.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo(plaintext);
        assertThat(service.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void tamperedCiphertextFailsToDecryptRatherThanReturningCorruptedPlaintext() {
        AesGcmEncryptionService service = serviceWithKey("default", randomBase64Key());
        String ciphertext = service.encrypt("4111111111111234");

        byte[] raw = Base64.getDecoder().decode(ciphertext);
        raw[raw.length - 1] ^= 0x01; // flip one bit in the GCM tag/ciphertext
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(EncryptionException.class);
    }

    @Test
    void decryptingWithTheWrongKeyFails() {
        AesGcmEncryptionService encryptingService = serviceWithKey("default", randomBase64Key());
        String ciphertext = encryptingService.encrypt("4111111111111234");

        AesGcmEncryptionService decryptingServiceWithDifferentKey = serviceWithKey("default", randomBase64Key());

        assertThatThrownBy(() -> decryptingServiceWithDifferentKey.decrypt(ciphertext))
                .isInstanceOf(EncryptionException.class);
    }

    @Test
    void nullPlaintextAndCiphertextPassThroughAsNull() {
        AesGcmEncryptionService service = serviceWithKey("default", randomBase64Key());
        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
    }
}
