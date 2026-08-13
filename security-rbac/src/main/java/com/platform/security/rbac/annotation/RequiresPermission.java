package com.platform.security.rbac.annotation;

import java.lang.annotation.*;

/**
 * Enforces that the caller's roles resolve (via the configured
 * {@link com.platform.security.rbac.resolver.PermissionResolver}) to at
 * least one of the given permissions. This is the recommended annotation
 * over {@link RequiresRole} for business-facing methods: the
 * role-to-permission mapping lives in one place
 * (security.rbac.role-permissions), so "which roles can approve a transfer"
 * is a config change and a `/actuator/securityRbac` lookup, not a grep
 * across every {@code @RequiresRole} call site in the codebase.
 *
 * <pre>{@code
 * @RequiresPermission("payment:approve")
 * public void approveTransfer(String transferId) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface RequiresPermission {

    /** Any of these permissions satisfies the check (logical OR), unless {@link #requireAll()} is true. */
    String[] value();

    boolean requireAll() default false;
}
