package com.vibe.sdk.client;

import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.transport.nio.ConnectionState;

import java.util.UUID;

/**
 * Reactive event listener for Vibe network, chat, presence, and status events.
 */
public interface VibeClientListener {

    default void onConnectionStateChanged(ConnectionState state) {}

    default void onAuthenticated(String userId, String token) {}

    default void onMessageReceived(ConversationEventPayload event) {}

    default void onReactionReceived(UUID conversationId, UUID messageId, String emoji, String userId) {}

    default void onReadReceipt(UUID conversationId, String userId, long readSeq) {}

    default void onPresenceUpdate(String userId, boolean online, long lastSeen) {}

    default void onTypingIndicator(UUID conversationId, String userId, boolean typing) {}

    default void onError(String errorCode, String errorMessage) {}
}
