package com.vibe.sdk.storage;

public enum MessageDeliveryStatus {
    PENDING_OUTBOX,
    COMMITTED,
    DELIVERED,
    READ,
    FAILED
}
