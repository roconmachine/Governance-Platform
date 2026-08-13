package com.platform.security.rbac.annotation;

import java.lang.annotation.*;

/**
 * Enforces that the caller (resolved from Spring Security's
 * SecurityContext - by security-auth or any other mechanism that populates
 * it) holds at least one of the given roles. Roles are matched against
 * granted authorities as {@code ROLE_<role>} - the same convention
 * security-auth's JwtAuthenticationFilter uses and
 * {@code hasRole()}/{@code @PreAuthorize} expect.
 *
 * <pre>{@code
 * @RequiresRole({"PAYMENT_ADMIN", "PAYMENT_SUPERVISOR"})
 * public void approveTransfer(String transferId) { ... }
 * }</pre>
 *
 * For finer-grained checks than "has this role", prefer
 * {@link RequiresPermission}, which maps roles to specific permissions via
 * configuration rather than hardcoding the mapping into every call site.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface RequiresRole {

    /** Any of these roles satisfies the check (logical OR), unless {@link #requireAll()} is true. */
    String[] value();

    /** If true, the caller must hold ALL listed roles rather than any one of them. */
    boolean requireAll() default false;
}
