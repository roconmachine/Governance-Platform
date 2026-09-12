package com.roconmachine.securityauth.core;

import java.util.Optional;

/**
 * Persistence abstraction for refresh-token state (needed for one-time-use rotation
 * and reuse/theft detection — unlike access tokens, refresh tokens are inherently
 * stateful). The starter registers {@link InMemoryRefreshTokenStore} by default;
 * override with a {@code @Bean RefreshTokenStore} backed by Redis or a database for
 * anything beyond a single-instance dev deployment, since the in-memory store does not
 * survive restarts or work across multiple application instances.
 */
public interface RefreshTokenStore {

    /** Persist a newly issued refresh token. */
    void save(StoredRefreshToken token);

    /** Look up a token by the SHA-256 hash of its raw value. */
    Optional<StoredRefreshToken> findByHash(String tokenHash);

    /** Replace the stored record for a token (e.g. after marking it used or revoked). */
    void update(StoredRefreshToken token);

    /**
     * Revoke every token sharing {@code familyId} — called on reuse/theft detection so
     * a stolen-and-already-rotated token can't be used again, and neither can any token
     * further down the same rotation chain.
     */
    void revokeFamily(String familyId);

    /** True if any token in the family has been explicitly revoked. */
    boolean isFamilyRevoked(String familyId);
}
