package com.roconmachine.security.auth.key;

import com.roconmachine.security.auth.config.SecurityAuthProperties;
import com.roconmachine.security.auth.model.TokenIssuerException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads base64-encoded key material from {@code security.token-issuer.keys}.
 * The zero-infra default for local development and tests - NOT a
 * substitute for real key management (KMS/Vault) in any environment
 * handling real data. Replace this bean entirely (via
 * {@code @ConditionalOnMissingBean}) for that.
 */
public class PropertiesSigningKeyProvider implements SigningKeyProvider {

    private final SecurityAuthProperties properties;
    private final Map<String, SecretKey> cache = new ConcurrentHashMap<>();

    public PropertiesSigningKeyProvider(SecurityAuthProperties properties) {
        String activeKeyId = properties.getActiveKeyId();
        if (activeKeyId == null || activeKeyId.isBlank()) {
            throw new TokenIssuerException(
                    "security.token-issuer.active-key-id is not configured - refusing to start " +
                            "an issuer with no designated signing key rather than guessing one.");
        }
        if (!properties.getKeys().containsKey(activeKeyId)) {
            throw new TokenIssuerException(
                    "security.token-issuer.active-key-id ('" + activeKeyId + "') has no matching " +
                            "entry under security.token-issuer.keys - refusing to start.");
        }
        this.properties = properties;
    }

    @Override
    public String currentKeyId() {
        return properties.getActiveKeyId();
    }

    @Override
    public SecretKey resolveKey(String keyId) {
        SecretKey cached = cache.get(keyId);
        if (cached != null) {
            return cached;
        }
        String base64Key = properties.getKeys().get(keyId);
        if (base64Key == null || base64Key.isBlank()) {
            throw new TokenIssuerException(
                    "No signing key configured for key id '" + keyId + "' under " +
                            "security.token-issuer.keys - cannot verify/refresh a token signed with " +
                            "an unknown or retired key id.");
        }
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), properties.getAlgorithm());
        cache.put(keyId, key);
        return key;
    }
}
