package com.platform.security.issuer.key;

import javax.crypto.SecretKey;

/**
 * Resolves signing/verification keys by id (the JWT header's {@code kid}),
 * and separately tracks which key id is CURRENT for signing new tokens.
 * This is the mechanism that makes key rotation possible without
 * invalidating every outstanding token at the cutover instant: rotate
 * {@link #currentKeyId()} to a new id, keep the old id's key resolvable via
 * {@link #resolveKey(String)} for as long as tokens signed with it might
 * still be presented, then retire it once its longest-lived token would
 * have expired anyway.
 *
 * Mirrors security-crypto's {@code KeyProvider} abstraction deliberately -
 * same shape, same swap-in-a-real-implementation-via-@ConditionalOnMissingBean
 * pattern, so a service already comfortable with that module's extension
 * point recognizes this one immediately instead of learning a second
 * bespoke key-lookup mechanism.
 */
public interface SigningKeyProvider {

    /** The key id new tokens should be signed with. */
    String currentKeyId();

    /** Resolves the key material for a given key id - current or retiring. */
    SecretKey resolveKey(String keyId);

    default SecretKey currentSigningKey() {
        return resolveKey(currentKeyId());
    }
}
