package com.platform.security.auth.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * What a validated JWT resolves to. Deliberately a plain, framework-agnostic
 * value type - services depend on this, not on {@code Jws<Claims>} or any
 * jjwt-specific type, so swapping the token library later doesn't ripple
 * into application code.
 */
public final class AuthenticatedPrincipal {

    private final String subject;
    private final Set<String> roles;
    private final Map<String, Object> claims;

    public AuthenticatedPrincipal(String subject, Set<String> roles, Map<String, Object> claims) {
        this.subject = subject;
        this.roles = roles == null ? Set.of() : Set.copyOf(roles);
        this.claims = claims == null ? Map.of() : Collections.unmodifiableMap(claims);
    }

    public String getSubject() { return subject; }
    public Set<String> getRoles() { return roles; }
    public Map<String, Object> getClaims() { return claims; }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return "AuthenticatedPrincipal{subject='" + subject + "', roles=" + roles + '}';
    }
}
