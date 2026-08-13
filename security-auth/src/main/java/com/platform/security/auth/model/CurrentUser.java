package com.platform.security.auth.model;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Convenience accessor so application code reads
 * {@code CurrentUser.get()} instead of casting
 * {@code SecurityContextHolder.getContext().getAuthentication().getPrincipal()}
 * everywhere. A thin, stateless facade - not a static holder with a
 * lifecycle to manage (unlike EncryptedStringConverter's bridge in
 * security-crypto, which exists only because JPA leaves no other
 * option).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedPrincipal> get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}
