package com.roconmachine.governance.idempotency;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface IdempotencyStore {
    
    enum State { PROCESSING, COMPLETED }

    record IdempotencyRecord(
        String key,
        String requestFingerprint,
        State state,
        int status,
        Map<String, String> headers,
        byte[] responseBody,
        Instant createdAt
    ) {}

    /**
     * Atomically acquires an idempotency lock for key if not present.
     * @return true if lock acquired (new request), false if key already exists.
     */
    boolean acquireLock(String key, String requestFingerprint);

    Optional<IdempotencyRecord> getRecord(String key);

    void saveResponse(String key, String requestFingerprint, int status, Map<String, String> headers, byte[] body);
}