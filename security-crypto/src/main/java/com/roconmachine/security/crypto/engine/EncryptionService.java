package com.roconmachine.security.crypto.engine;

/**
 * The API application code and the JPA converter both call. Framework
 * agnostic on purpose - use it directly in a service method just as easily
 * as through the JPA converter:
 *
 * <pre>{@code
 * String encrypted = encryptionService.encrypt(cardNumber);
 * // ... later
 * String cardNumber = encryptionService.decrypt(encrypted);
 * }</pre>
 */
public interface EncryptionService {

    /** Encrypts plaintext using the default key, returning a self-contained, base64-encoded ciphertext. */
    String encrypt(String plaintext);

    /** Encrypts plaintext using a specific key id. */
    String encrypt(String plaintext, String keyId);

    /** Decrypts a value produced by {@link #encrypt}. */
    String decrypt(String ciphertext);

    /** Decrypts a value produced by {@link #encrypt(String, String)} with the same key id. */
    String decrypt(String ciphertext, String keyId);
}
