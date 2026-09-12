package com.roconmachine.security.auth.key;

import javax.crypto.SecretKey;


public interface SigningKeyProvider {

    /** The key id new tokens should be signed with. */
    String currentKeyId();

    /** Resolves the key material for a given key id - current or retiring. */
    SecretKey resolveKey(String keyId);

    default SecretKey currentSigningKey() {
        return resolveKey(currentKeyId());
    }
}
