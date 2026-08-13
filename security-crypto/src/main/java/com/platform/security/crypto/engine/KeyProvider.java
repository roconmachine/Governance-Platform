package com.platform.security.crypto.engine;

import javax.crypto.spec.SecretKeySpec;

/**
 * Resolves the raw key material for a given key id. This is the seam a real
 * deployment swaps: ship with {@link EnvKeyProvider} for local/dev, replace
 * with a bean backed by AWS KMS / HashiCorp Vault / Azure Key Vault in any
 * environment that needs real key management, via
 * {@code @ConditionalOnMissingBean} - no call site (EncryptionService,
 * EncryptedStringConverter) changes when you do.
 */
public interface KeyProvider {

    /** Raw key bytes for the given key id. Must always return the same bytes for the same id within a key's lifetime. */
    byte[] getKeyBytes(String keyId);

    default SecretKeySpec getSecretKey(String keyId, String algorithm) {
        return new SecretKeySpec(getKeyBytes(keyId), algorithm);
    }
}
