package com.vibe.domain.idempotency;

import java.util.Objects;

/**
 * Unique compound key identifying an idempotent request submission.
 */
public record IdempotencyKey(
        String userId,
        String deviceId,
        String clientMessageId
) {

    public IdempotencyKey {
        Objects.requireNonNull(userId, "userId must not be null");
        deviceId = deviceId != null ? deviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isIdempotencyEnabled() {
        return !clientMessageId.isEmpty();
    }
}
