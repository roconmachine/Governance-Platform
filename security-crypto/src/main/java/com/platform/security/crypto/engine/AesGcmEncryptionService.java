package com.platform.security.crypto.engine;

import com.platform.security.crypto.config.SecurityCryptoProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM: authenticated encryption, not just confidentiality - GCM's
 * tag detects ciphertext tampering (a bit-flip attack fails to decrypt
 * rather than silently producing corrupted plaintext), which matters for
 * financial data specifically. A random 12-byte IV is generated per call and
 * prepended to the ciphertext before base64 encoding, so callers never
 * manage IVs themselves and never accidentally reuse one (IV reuse under
 * GCM is a real key-recovery risk, not just a formatting concern).
 *
 * Output format: base64( IV(12 bytes) || ciphertext-with-GCM-tag ).
 */
public class AesGcmEncryptionService implements EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final KeyProvider keyProvider;
    private final SecurityCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionService(KeyProvider keyProvider, SecurityCryptoProperties properties) {
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    @Override
    public String encrypt(String plaintext) {
        return encrypt(plaintext, properties.getDefaultKeyId());
    }

    @Override
    public String encrypt(String plaintext, String keyId) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getSecretKey(keyId, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Failed to encrypt value with key '" + keyId + "'", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        return decrypt(ciphertext, properties.getDefaultKeyId());
    }

    @Override
    public String decrypt(String ciphertext, String keyId) {
        if (ciphertext == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length < IV_LENGTH_BYTES) {
                throw new EncryptionException("Ciphertext too short to contain an IV - not a value this service produced", null);
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] actualCiphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, IV_LENGTH_BYTES, actualCiphertext, 0, actualCiphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.getSecretKey(keyId, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(actualCiphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Deliberately vague message: a GCM auth-tag failure means either the wrong
            // key, corrupted data, or tampering - never distinguish these in the message,
            // since that itself would leak information useful to an attacker.
            throw new EncryptionException("Failed to decrypt value with key '" + keyId +
                    "' - wrong key, corrupted data, or tampering detected", e);
        }
    }
}
