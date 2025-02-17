package com.vibe.domain.event;

import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record ReactionAddedEvent(
        UUID eventId,
        UUID conversationId,
        long seqNumber,
        String senderUserId,
        long timestamp,
        UUID messageId,
        String emoji
) implements DomainEvent {

    public ReactionAddedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(senderUserId, "senderUserId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(emoji, "emoji must not be null");
    }

    @Override
    public short eventType() {
        return ConversationEventPayload.EVENT_REACTION_ADDED;
    }

    @Override
    public ByteBuffer encodePayload() {
        int size = 16 + PayloadCodecUtil.stringByteLength(emoji);
        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeUUID(buf, messageId);
        PayloadCodecUtil.writeString(buf, emoji);
        buf.flip();
        return buf;
    }

    public static ReactionAddedEvent decode(
            UUID eventId,
            UUID conversationId,
            long seqNumber,
            String senderUserId,
            long timestamp,
            ByteBuffer payloadBuf
    ) {
        UUID messageId = PayloadCodecUtil.readUUID(payloadBuf);
        String emoji = PayloadCodecUtil.readString(payloadBuf);
        return new ReactionAddedEvent(eventId, conversationId, seqNumber, senderUserId, timestamp, messageId, emoji);
    }
}
