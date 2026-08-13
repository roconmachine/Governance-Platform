package com.platform.security.exchange.cache;

import com.platform.security.exchange.config.ExchangeAuthProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TokenCacheManagerTest {

    private ExchangeAuthProperties propertiesWithBuffer(long bufferSeconds) {
        ExchangeAuthProperties properties = new ExchangeAuthProperties();
        properties.getCache().setExpiryBufferSeconds(bufferSeconds);
        properties.getCache().setMaxSize(100);
        return properties;
    }

    @Test
    void tokenFarFromExpiryIsImmediatelyRetrievable() {
        TokenCacheManager cache = new TokenCacheManager(propertiesWithBuffer(300));
        cache.put("key-1", "token-value", Instant.now().plusSeconds(3600));

        assertThat(cache.getIfPresent("key-1")).isEqualTo("token-value");
    }

    @Test
    void tokenWithinTheExpiryBufferIsTreatedAsAlreadyExpired() {
        // expiresAt is only 1 second out, but the buffer is 300s (5 min) -
        // this token should never be considered fresh at all.
        TokenCacheManager cache = new TokenCacheManager(propertiesWithBuffer(300));
        cache.put("key-2", "token-value", Instant.now().plusSeconds(1));

        // Caffeine's expiry check happens lazily on access, and our
        // expireAfterCreate already computed a negative/zero duration for
        // this entry, so it should read as absent right away.
        assertThat(cache.getIfPresent("key-2")).isNull();
    }

    @Test
    void tokenExpiresShortlyAfterItsRealExpiryMinusBuffer() {
        // No buffer, very short real lifetime - proves expiry is tied to
        // the token's OWN expiresAt, not some fixed cache-wide TTL.
        TokenCacheManager cache = new TokenCacheManager(propertiesWithBuffer(0));
        cache.put("key-3", "token-value", Instant.now().plusMillis(200));

        assertThat(cache.getIfPresent("key-3")).isEqualTo("token-value");

        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(cache.getIfPresent("key-3")).isNull());
    }

    @Test
    void invalidateRemovesAnEntryImmediately() {
        TokenCacheManager cache = new TokenCacheManager(propertiesWithBuffer(300));
        cache.put("key-4", "token-value", Instant.now().plusSeconds(3600));
        assertThat(cache.getIfPresent("key-4")).isEqualTo("token-value");

        cache.invalidate("key-4");

        assertThat(cache.getIfPresent("key-4")).isNull();
    }
}
