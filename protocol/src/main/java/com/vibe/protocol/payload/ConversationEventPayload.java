package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload for CONVERSATION_EVENT frame (0x09) fanning out durable mutations.
 */
public record ConversationEventPayload(
        UUID conversationId,
        long seqNumber,
        UUID eventId,
        short eventType,
        String senderUserId,
        long timestamp,
        byte[] payload
) {

    public static final short EVENT_MESSAGE_SENT = 0x0001;
    public static final short EVENT_MESSAGE_EDITED = 0x0002;
    public static final short EVENT_MESSAGE_DELETED = 0x0003;
    public static final short EVENT_REACTION_ADDED = 0x0004;
    public static final short EVENT_REACTION_REMOVED = 0x0005;
    public static final short EVENT_MEMBER_JOINED = 0x0006;
    public static final short EVENT_MEMBER_LEFT = 0x0007;
    public static final short EVENT_TYPING = 0x0008;
    public static final short EVENT_PRESENCE = 0x0009;

    public ConversationEventPayload {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        senderUserId = senderUserId != null ? senderUserId : "";
        payload = payload != null ? payload : new byte[0];
    }

    public ByteBuffer encode() {
        int size = 16 + 8 + 16 + 2 + PayloadCodecUtil.stringByteLength(senderUserId) + 8 + PayloadCodecUtil.bytesLength(payload);
        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        buf.putLong(seqNumber);
        PayloadCodecUtil.writeUUID(buf, eventId);
        buf.putShort(eventType);
        PayloadCodecUtil.writeString(buf, senderUserId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeBytes(buf, payload);
        buf.flip();
        return buf;
    }

    public static ConversationEventPayload decode(ByteBuffer src) {
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        long seqNumber = src.getLong();
        UUID eventId = PayloadCodecUtil.readUUID(src);
        short eventType = src.getShort();
        String senderUserId = PayloadCodecUtil.readString(src);
        long timestamp = src.getLong();
        byte[] payload = PayloadCodecUtil.readBytes(src);
        return new ConversationEventPayload(conversationId, seqNumber, eventId, eventType, senderUserId, timestamp, payload);
    }
}
