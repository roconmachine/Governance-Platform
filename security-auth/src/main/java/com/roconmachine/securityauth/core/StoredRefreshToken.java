package com.roconmachine.securityauth.core;

import java.time.Instant;

/**
 * Server-side record of an issued refresh token. Only the SHA-256 hash of the raw
 * token is ever stored — the raw value exists solely in the response given to the
 * client — so a compromised store cannot be used to forge or replay tokens.
 *
 * <p>{@code familyId} links every token descended from the same original login
 * together (each rotation produces a new token in the same family). Reusing an
 * already-rotated token is a strong signal of token theft: {@link RefreshTokenService}
 * responds by revoking the whole family, forcing re-authentication.
 *
 * @param tokenHash  SHA-256 hex digest of the raw refresh token (primary lookup key)
 * @param familyId   groups all tokens produced by successive rotations of one login
 * @param subject    the "sub" this token authenticates
 * @param issuedAt   when this specific token was issued
 * @param expiresAt  absolute expiry of this specific token
 * @param used       true once this token has been redeemed via rotation
 * @param usedAt     when it was redeemed, or null if still unused
 * @param revoked    true if this token (or its whole family) has been explicitly revoked
 */
public record StoredRefreshToken(
        String tokenHash,
        String familyId,
        String subject,
        Instant issuedAt,
        Instant expiresAt,
        boolean used,
        Instant usedAt,
        boolean revoked
) {

    public StoredRefreshToken markUsed(Instant when) {
        return new StoredRefreshToken(tokenHash, familyId, subject, issuedAt, expiresAt, true, when, revoked);
    }

    public StoredRefreshToken markRevoked() {
        return new StoredRefreshToken(tokenHash, familyId, subject, issuedAt, expiresAt, used, usedAt, true);
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
