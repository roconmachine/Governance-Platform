package com.platform.security.exchange.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.platform.security.exchange.config.ExchangeAuthProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * Caches acquired access tokens so a hot middleware endpoint doesn't hit
 * Entra ID's token endpoint on every request. The detail worth being
 * deliberate about: Entra-issued tokens don't all share one fixed lifetime
 * (client-credentials tokens are typically ~60-90 minutes, but this is not
 * guaranteed and can change), so a cache with a single hardcoded TTL either
 * expires tokens too early (wasted acquisitions) or - worse - serves a token
 * past its actual expiry. This cache instead ties each entry's expiry to
 * the specific {@code expiresOn} timestamp MSAL4J returned for THAT token,
 * minus a configurable safety buffer (default 5 minutes) so a token is
 * never handed to a caller close enough to its real expiry to risk expiring
 * mid-flight during a slow downstream call.
 *
 * Thread-safe: Caffeine's Cache is safe for concurrent get/put from
 * multiple request threads without any external synchronization.
 */
public class TokenCacheManager {

    private final Cache<String, CachedToken> cache;
    private final Duration expiryBuffer;

    public TokenCacheManager(ExchangeAuthProperties properties) {
        this.expiryBuffer = Duration.ofSeconds(properties.getCache().getExpiryBufferSeconds());
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfter(new PerTokenExpiry())
                .build();
    }

    public String getIfPresent(String cacheKey) {
        CachedToken cached = cache.getIfPresent(cacheKey);
        return cached == null ? null : cached.accessToken();
    }

    public void put(String cacheKey, String accessToken, Instant expiresAt) {
        cache.put(cacheKey, new CachedToken(accessToken, expiresAt));
    }

    public void invalidate(String cacheKey) {
        cache.invalidate(cacheKey);
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    /**
     * Computes remaining nanoseconds-until-expiry from each entry's OWN
     * expiresAt, applied once at insertion (expireAfterCreate) and
     * recomputed if the same key is ever overwritten (expireAfterUpdate - a
     * fresh acquisition replacing a stale cache entry). Deliberately does
     * NOT extend expiry on read ({@code expireAfterRead} returns the
     * unchanged remaining duration): a token's validity is a property of
     * the token itself, not of how recently it happened to be read from
     * cache.
     */
    private final class PerTokenExpiry implements Expiry<String, CachedToken> {
        @Override
        public long expireAfterCreate(String key, CachedToken value, long currentTime) {
            Duration remaining = Duration.between(Instant.now(), value.expiresAt()).minus(expiryBuffer);
            return Math.max(remaining.toNanos(), 0L);
        }

        @Override
        public long expireAfterUpdate(String key, CachedToken value, long currentTime, long currentDuration) {
            return expireAfterCreate(key, value, currentTime);
        }

        @Override
        public long expireAfterRead(String key, CachedToken value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
