package com.vibe.sdk.storage;

import java.util.UUID;

public record StoredMessage(
        UUID messageId,
        UUID conversationId,
        String senderId,
        long seqNumber,
        String content,
        long timestamp,
        MessageDeliveryStatus status,
        UUID attachmentId,
        String attachmentName,
        long attachmentSize
) {
}
