package com.platform.security.rbac.resolver;

import java.util.Set;

/**
 * Resolves the set of permissions a role grants. Ships with
 * {@link PropertiesPermissionResolver} (config-driven); replace with a
 * database- or admin-UI-backed bean via {@code @ConditionalOnMissingBean}
 * when the mapping needs to change without a redeploy - the aspect enforcing
 * {@code @RequiresPermission} doesn't change either way.
 */
public interface PermissionResolver {

    /** All permissions granted by the given role. Empty set if the role is unknown/unmapped. */
    Set<String> permissionsForRole(String role);
}
