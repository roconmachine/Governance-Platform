package com.roconmachine.governance.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InMemoryIdempotencyStore implements IdempotencyStore, AutoCloseable {

    private final Map<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    public InMemoryIdempotencyStore(Duration ttl) {
        this.ttl = ttl;
        // Periodic background eviction of expired keys
        this.cleanupExecutor.scheduleAtFixedRate(this::evictExpired, 1, 1, TimeUnit.MINUTES);
    }

    public InMemoryIdempotencyStore() {
        this(Duration.ofHours(24));
    }

    @Override
    public boolean acquireLock(String key, String requestFingerprint) {
        Instant now = Instant.now();
        evictIfExpired(key, now);

        IdempotencyRecord initialRecord = new IdempotencyRecord(
                key,
                requestFingerprint,
                State.PROCESSING,
                0,
                Map.of(),
                new byte[0],
                now
        );

        // Put only if Absent ensures atomic acquisition
        return store.putIfAbsent(key, initialRecord) == null;
    }

    @Override
    public Optional<IdempotencyRecord> getRecord(String key) {
        Instant now = Instant.now();
        evictIfExpired(key, now);
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void saveResponse(String key, String requestFingerprint, int status, Map<String, String> headers, byte[] body) {
        IdempotencyRecord completedRecord = new IdempotencyRecord(
                key,
                requestFingerprint,
                State.COMPLETED,
                status,
                Map.copyOf(headers),
                body,
                Instant.now()
        );
        store.put(key, completedRecord);
    }

    private void evictIfExpired(String key, Instant now) {
        IdempotencyRecord record = store.get(key);
        if (record != null && record.createdAt().plus(ttl).isBefore(now)) {
            store.remove(key, record);
        }
    }

    private void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(entry -> entry.getValue().createdAt().plus(ttl).isBefore(now));
    }

    @Override
    public void close() {
        cleanupExecutor.shutdown();
    }
}