package com.roconmachine.securityauth.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev-only fallback {@link RefreshTokenStore} backed by a {@link ConcurrentHashMap}.
 * Thread-safe for a single instance, but state is lost on restart and not shared
 * across multiple application instances — meaning rotation/reuse detection silently
 * stops working correctly the moment you scale horizontally. Register a real
 * {@code @Bean RefreshTokenStore} (Redis, a database table, etc.) before production.
 */
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRefreshTokenStore.class);

    private final Map<String, StoredRefreshToken> tokensByHash = new ConcurrentHashMap<>();
    private final Set<String> revokedFamilies = ConcurrentHashMap.newKeySet();

    public InMemoryRefreshTokenStore() {
        log.warn("Using InMemoryRefreshTokenStore - refresh token state will NOT survive a "
                + "restart and will NOT be shared across multiple instances. Register a "
                + "custom RefreshTokenStore bean (Redis/database-backed) before production.");
    }

    @Override
    public void save(StoredRefreshToken token) {
        tokensByHash.put(token.tokenHash(), token);
    }

    @Override
    public Optional<StoredRefreshToken> findByHash(String tokenHash) {
        return Optional.ofNullable(tokensByHash.get(tokenHash));
    }

    @Override
    public void update(StoredRefreshToken token) {
        tokensByHash.put(token.tokenHash(), token);
    }

    @Override
    public void revokeFamily(String familyId) {
        revokedFamilies.add(familyId);
        tokensByHash.replaceAll((hash, token) ->
                token.familyId().equals(familyId) ? token.markRevoked() : token);
    }

    @Override
    public boolean isFamilyRevoked(String familyId) {
        return revokedFamilies.contains(familyId);
    }
}
