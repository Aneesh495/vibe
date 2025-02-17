package com.vibe.sdk.storage;

import com.vibe.domain.entity.ConversationType;

import java.util.UUID;

public record StoredConversation(
        UUID conversationId,
        ConversationType type,
        String title,
        long createdAt,
        long lastSeq,
        int unreadCount
) {
}
