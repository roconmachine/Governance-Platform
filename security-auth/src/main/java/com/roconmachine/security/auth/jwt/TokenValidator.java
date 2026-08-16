package com.roconmachine.security.auth.jwt;

import com.roconmachine.security.auth.model.AuthenticatedPrincipal;

/**
 * The extension point: ships with {@link JwtTokenValidator} (signed JWTs),
 * replace with your own bean (e.g. OAuth2 token introspection against an
 * authorization server, opaque tokens, mTLS-derived identity) via
 * {@code @ConditionalOnMissingBean} - JwtAuthenticationFilter doesn't change.
 */
public interface TokenValidator {

    /** Validates the raw token and resolves it to a principal. Throws {@link TokenValidationException} for any failure - expired, malformed, bad signature, wrong issuer/audience. */
    AuthenticatedPrincipal validate(String token) throws TokenValidationException;
}
