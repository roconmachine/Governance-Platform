package com.platform.security.rbac.resolver;

import com.platform.security.rbac.config.SecurityRbacProperties;

import java.util.List;
import java.util.Set;

public class PropertiesPermissionResolver implements PermissionResolver {

    private final SecurityRbacProperties properties;

    public PropertiesPermissionResolver(SecurityRbacProperties properties) {
        this.properties = properties;
    }

    @Override
    public Set<String> permissionsForRole(String role) {
        List<String> permissions = properties.getRolePermissions().get(role);
        return permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
