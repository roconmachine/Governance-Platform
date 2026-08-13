package com.platform.security.rbac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "security.rbac")
public class SecurityRbacProperties {

    /** Master switch. Defaults to true. */
    private boolean enabled = true;

    /**
     * Role -> granted permissions. This is the actual access policy,
     * expressed as configuration instead of scattered across code - review
     * a diff of this map in a PR and you've reviewed the entire permission
     * change, instead of grepping for hasRole() calls.
     *
     * Example:
     * security.rbac.role-permissions.PAYMENT_ADMIN[0]=payment:read
     * security.rbac.role-permissions.PAYMENT_ADMIN[1]=payment:approve
     */
    private Map<String, List<String>> rolePermissions = new HashMap<>();

    /**
     * If true (default), a role with no entry in rolePermissions grants NO
     * permissions - fail closed. Setting this false is almost never what you
     * want in a fintech context; it exists for services migrating
     * incrementally from ad-hoc hasRole() checks.
     */
    private boolean denyByDefault = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Map<String, List<String>> getRolePermissions() { return rolePermissions; }
    public void setRolePermissions(Map<String, List<String>> rolePermissions) { this.rolePermissions = rolePermissions; }

    public boolean isDenyByDefault() { return denyByDefault; }
    public void setDenyByDefault(boolean denyByDefault) { this.denyByDefault = denyByDefault; }
}
