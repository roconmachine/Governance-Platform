package com.platform.security.crypto.endpoint;

import com.platform.security.crypto.config.SecurityCryptoProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes which keys are CONFIGURED (by id only) and which algorithm is in
 * use - deliberately never the key material itself, even partially. An
 * auditor can confirm "yes, key rotation policy X is in effect" without this
 * endpoint becoming something worth attacking.
 * call /actuator/security-encryption to get below info
 * configure properties :: management.endpoints.web.exposure.include=security-encryption
 */
@Endpoint(id = "securityEncryption")
public class SecurityCryptoInfoEndpoint {

    private final SecurityCryptoProperties properties;

    public SecurityCryptoInfoEndpoint(SecurityCryptoProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> encryptionPolicy() {
        Map<String, Object> encryption = new LinkedHashMap<>();
        encryption.put("enabled", properties.isEnabled());
        encryption.put("algorithm", "AES/GCM/NoPadding");
        encryption.put("defaultKeyId", properties.getDefaultKeyId());
        // key IDs only - never values
        encryption.put("configuredKeyIds", properties.getKeys().keySet());
        encryption.put("keyEnvPrefix", properties.getKeyEnvPrefix());

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("encryption", encryption);
        policy.put("module", "security-crypto");
        return policy;
    }
}
