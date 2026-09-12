package com.roconmachine.securityauth.core;

import javax.crypto.SecretKey;

/**
 * Supplies the {@link SecretKey} used to sign and verify tokens. The starter registers
 * {@link DefaultKeyProvider} (property-based HMAC key) when no other bean of this type
 * exists — host applications wanting a KMS-, Vault-, or rotating-key-backed source
 * should provide their own {@code @Bean KeyProvider} to override it.
 */
public interface AuthKeyProvider {

    /**
     * @return the HMAC-SHA256 signing key. Implementations must be thread-safe; the
     * returned key is reused concurrently across all request threads.
     */
    SecretKey getSigningKey();
}
