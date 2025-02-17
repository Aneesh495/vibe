package com.vibe.sdk.storage;

import com.vibe.protocol.payload.CommandType;

import java.util.UUID;

public record OutboxItem(
        String clientMessageId,
        CommandType commandType,
        UUID conversationId,
        byte[] payload,
        long createdAt,
        int retryCount
) {
}
