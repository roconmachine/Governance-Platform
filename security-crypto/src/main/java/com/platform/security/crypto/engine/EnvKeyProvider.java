package com.platform.security.crypto.engine;

import com.platform.security.crypto.config.SecurityCryptoProperties;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads base64-encoded key material from configuration
 * ({@code security.crypto.keys.<keyId>}) or, if not configured there,
 * an environment variable named {@code <keyEnvPrefix><KEY_ID>} (uppercased).
 *
 * This is the zero-infra default for local development and tests - it is
 * NOT a substitute for real key management in any environment handling real
 * data. It deliberately does not fall back to a hardcoded or generated key
 * when a key id is missing: encryption must fail loudly and immediately
 * rather than silently write plaintext (or data encrypted with a key nobody
 * can reproduce) because a key lookup came back empty.
 */
public class EnvKeyProvider implements KeyProvider {

    private final SecurityCryptoProperties properties;
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public EnvKeyProvider(SecurityCryptoProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] getKeyBytes(String keyId) {
        String resolvedId = (keyId == null || keyId.isBlank()) ? properties.getDefaultKeyId() : keyId;
        return cache.computeIfAbsent(resolvedId, this::resolve);
    }

    private byte[] resolve(String keyId) {
        String configured = properties.getKeys().get(keyId);
        String base64Key = configured != null ? configured : System.getenv(envVarName(keyId));

        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "No encryption key configured for key id '" + keyId + "'. Set " +
                            "security.crypto.keys." + keyId + " or the " + envVarName(keyId) +
                            " environment variable to a base64-encoded key. Refusing to encrypt/decrypt " +
                            "without a real key rather than silently using a default one.");
        }
        return Base64.getDecoder().decode(base64Key);
    }

    private String envVarName(String keyId) {
        return properties.getKeyEnvPrefix() + keyId.toUpperCase().replace('-', '_');
    }
}
