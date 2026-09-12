package com.roconmachine.securityauth.core;

/**
 * Manages the access/refresh token lifecycle: initial issuance at login, one-time-use
 * rotation on refresh, and revocation. Refresh tokens are opaque, cryptographically
 * random, server-tracked strings (not JWTs) — unlike access tokens, they must be
 * revocable and individually trackable for rotation to work, which a stateless JWT
 * cannot provide without an additional blacklist.
 */
public interface RefreshTokenService {

    /**
     * Start a new session: issues an access token and the first refresh token of a new
     * rotation family.
     */
    TokenPair issue(String subject);

    /**
     * Redeem a refresh token for a new access/refresh pair. The presented token is
     * atomically marked used and a new token in the same family is issued — the old
     * token can never be redeemed again.
     *
     * @throws com.roconmachine.securityauth.exception.InvalidRefreshTokenException if the
     *         token is unknown, expired, or its family was already revoked
     * @throws com.roconmachine.securityauth.exception.TokenReuseDetectedException  if the
     *         token was already used once before (the whole family is revoked as a
     *         side effect of this call, before the exception is thrown)
     */
    TokenPair rotate(String rawRefreshToken);

    /** Revoke a single session by its current refresh token (e.g. explicit logout). */
    void revoke(String rawRefreshToken);
}
