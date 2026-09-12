package com.roconmachine.security.auth.endpoint;

import com.roconmachine.security.auth.config.SecurityAuthProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

@Endpoint(id = "securityAuth")
public class SecurityAuthInfoEndpoint {

    private final SecurityAuthProperties properties;

    public SecurityAuthInfoEndpoint(SecurityAuthProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> authPolicy() {
        Map<String, Object> auth = new LinkedHashMap<>();
        auth.put("enabled", properties.isEnabled());
        auth.put("issuer", properties.getIssuer());
        auth.put("audience", properties.getAudience());
        auth.put("rolesClaim", properties.getRolesClaim());
        auth.put("headerName", properties.getHeaderName());
        auth.put("clockSkewSeconds", properties.getClockSkewSeconds());
        auth.put("rejectInvalidToken", properties.isRejectInvalidToken());
        auth.put("hmacSecretConfigured", properties.getHmacSecret() != null && !properties.getHmacSecret().isBlank());

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("auth", auth);
        policy.put("module", "security-auth");
        return policy;
    }
}
