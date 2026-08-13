package com.platform.security.rbac.endpoint;

import com.platform.security.rbac.config.SecurityRbacProperties;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unlike security-crypto/security-auth's endpoints, there's no secret to
 * withhold here - the role-permission map IS the access policy, and being
 * able to see it in full via a curl call is exactly the point: "which roles
 * can approve a transfer over $10k" should be answerable without grepping
 * source code.
 */
@Endpoint(id = "securityRbac")
public class SecurityRbacInfoEndpoint {

    private final SecurityRbacProperties properties;

    public SecurityRbacInfoEndpoint(SecurityRbacProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public Map<String, Object> rbacPolicy() {
        Map<String, Object> rbac = new LinkedHashMap<>();
        rbac.put("enabled", properties.isEnabled());
        rbac.put("denyByDefault", properties.isDenyByDefault());
        rbac.put("rolePermissions", properties.getRolePermissions());

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("rbac", rbac);
        policy.put("module", "security-rbac:0.1.0-SNAPSHOT");
        return policy;
    }
}
