package com.roconmachine.securityauth.core;

import io.jsonwebtoken.Claims;

import java.util.Map;

/**
 * Core issuance/parsing contract for the starter. Implementations must be thread-safe
 * since a single Spring-managed instance serves every concurrent request.
 */
public interface JwtService {

    /**
     * Issue a signed access token for the given subject, merging in any extra claims
     * and running all registered {@code ClaimsCustomizer} beans.
     */
    String generateToken(String subject, Map<String, Object> extraClaims);

    /** Issue a signed refresh token (longer TTL, {@code token_type=refresh} claim). */
    String generateRefreshToken(String subject);

    /**
     * Parse and cryptographically verify a token, checking signature, issuer, and
     * expiration.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, mis-signed,
     *                                       expired, or has the wrong issuer
     */
    Claims parseClaims(String token);

    /** True if the token verifies, is unexpired, and its subject matches {@code expectedSubject}. */
    boolean isTokenValid(String token, String expectedSubject);

    /** Extract the "sub" claim without further validity checks beyond signature/issuer. */
    String extractSubject(String token);

    /** True if the token's "exp" claim is in the past (or the token is otherwise expired). */
    boolean isExpired(String token);
}
