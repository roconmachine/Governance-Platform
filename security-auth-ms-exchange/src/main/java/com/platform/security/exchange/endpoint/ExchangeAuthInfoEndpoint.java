package com.platform.security.exchange.endpoint;

import com.platform.security.exchange.config.ExchangeAuthProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes configuration SHAPE, never secret material - not the client
 * secret, not the certificate password, not private key bytes. tenantId and
 * clientId are not secrets (Entra ID app registrations treat both as public
 * identifiers - the client SECRET is what's confidential), so they're safe
 * to show in full.
 */
@Endpoint(id = "exchangeAuth")
public class ExchangeAuthInfoEndpoint {

    private final ExchangeAuthProperties properties;

    public ExchangeAuthInfoEndpoint(ExchangeAuthProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> exchangeAuthPolicy() {
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("enabled", properties.isEnabled());
        auth.put("tenantId", properties.getTenantId());
        auth.put("clientId", properties.getClientId());
        auth.put("credentialType", credentialType());
        auth.put("scopes", properties.getScopes());
        auth.put("cacheEnabled", properties.isCacheEnabled());
        auth.put("authorityHost", properties.getAuthorityHost());

        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("maxSize", properties.getCache().getMaxSize());
        cache.put("expiryBufferSeconds", properties.getCache().getExpiryBufferSeconds());
        auth.put("cache", cache);

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("expectedAudienceConfigured",
                properties.getValidation().getExpectedAudience() != null
                        && !properties.getValidation().getExpectedAudience().isBlank());
        auth.put("validation", validation);

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("exchangeAuth", auth);
        policy.put("module", "security-auth-ms-exchange:1.0.0");
        return policy;
    }

    private String credentialType() {
        boolean hasSecret = properties.getClientSecret() != null && !properties.getClientSecret().isBlank();
        boolean hasCertificate = properties.getCertificate().getPath() != null
                && !properties.getCertificate().getPath().isBlank();
        if (hasSecret) return "CLIENT_SECRET";
        if (hasCertificate) return "CERTIFICATE";
        return "NOT_CONFIGURED";
    }
}
