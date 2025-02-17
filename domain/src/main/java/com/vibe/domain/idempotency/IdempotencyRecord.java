package com.vibe.domain.idempotency;

import java.util.Objects;
import java.util.UUID;

/**
 * Cached result of an idempotently executed domain command.
 */
public record IdempotencyRecord(
        IdempotencyKey key,
        long assignedSeq,
        UUID eventId,
        int contentHash,
        byte[] resultPayload,
        long timestamp
) {

    public IdempotencyRecord {
        Objects.requireNonNull(key, "key must not be null");
        resultPayload = resultPayload != null ? resultPayload : new byte[0];
    }
}
