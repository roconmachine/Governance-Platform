package com.roconmachine.securityauth.extension;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

/**
 * Bridges a validated JWT's subject and claims to a concrete {@link UserDetails} that
 * {@code JwtAuthenticationFilter} places on the {@code SecurityContextHolder}.
 *
 * <p>This is the one extension point most host applications <b>must</b> implement for
 * production use: the starter's {@code DefaultUserAuthenticationProvider} trusts token
 * claims with no user-store lookup (no revocation/ban/role-freshness check) and logs a
 * warning on every use — it exists only so the starter is runnable out of the box.
 */
public interface UserAuthenticationProvider {

    /**
     * Load or construct the authenticated principal for a validated token.
     *
     * @param subject the "sub" claim of the validated token
     * @param claims  all claims extracted from the validated token
     * @return a fully populated {@link UserDetails}, or {@code null} to leave the
     * request unauthenticated (e.g. user was deactivated since the token was issued)
     */
    UserDetails loadUser(String subject, Map<String, Object> claims);
}
