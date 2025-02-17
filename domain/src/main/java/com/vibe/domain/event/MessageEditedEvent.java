package com.vibe.domain.event;

import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record MessageEditedEvent(
        UUID eventId,
        UUID conversationId,
        long seqNumber,
        String senderUserId,
        long timestamp,
        UUID messageId,
        String newContent
) implements DomainEvent {

    public MessageEditedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(senderUserId, "senderUserId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(newContent, "newContent must not be null");
    }

    @Override
    public short eventType() {
        return ConversationEventPayload.EVENT_MESSAGE_EDITED;
    }

    @Override
    public ByteBuffer encodePayload() {
        int size = 16 + PayloadCodecUtil.stringByteLength(newContent);
        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeUUID(buf, messageId);
        PayloadCodecUtil.writeString(buf, newContent);
        buf.flip();
        return buf;
    }

    public static MessageEditedEvent decode(
            UUID eventId,
            UUID conversationId,
            long seqNumber,
            String senderUserId,
            long timestamp,
            ByteBuffer payloadBuf
    ) {
        UUID messageId = PayloadCodecUtil.readUUID(payloadBuf);
        String newContent = PayloadCodecUtil.readString(payloadBuf);
        return new MessageEditedEvent(eventId, conversationId, seqNumber, senderUserId, timestamp, messageId, newContent);
    }
}
