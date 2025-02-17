package com.vibe.domain.event;

import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record MessageDeletedEvent(
        UUID eventId,
        UUID conversationId,
        long seqNumber,
        String senderUserId,
        long timestamp,
        UUID messageId
) implements DomainEvent {

    public MessageDeletedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(senderUserId, "senderUserId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
    }

    @Override
    public short eventType() {
        return ConversationEventPayload.EVENT_MESSAGE_DELETED;
    }

    @Override
    public ByteBuffer encodePayload() {
        ByteBuffer buf = ByteBuffer.allocate(16);
        PayloadCodecUtil.writeUUID(buf, messageId);
        buf.flip();
        return buf;
    }

    public static MessageDeletedEvent decode(
            UUID eventId,
            UUID conversationId,
            long seqNumber,
            String senderUserId,
            long timestamp,
            ByteBuffer payloadBuf
    ) {
        UUID messageId = PayloadCodecUtil.readUUID(payloadBuf);
        return new MessageDeletedEvent(eventId, conversationId, seqNumber, senderUserId, timestamp, messageId);
    }
}
