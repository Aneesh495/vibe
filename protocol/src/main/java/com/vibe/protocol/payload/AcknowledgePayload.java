package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload for ACKNOWLEDGE frame (0x0A) for delivery and read receipts.
 */
public record AcknowledgePayload(
        UUID conversationId,
        byte ackType,
        long upToSeq,
        long ackTimestamp
) {

    public static final byte ACK_DELIVERED = 0x01;
    public static final byte ACK_READ = 0x02;

    public AcknowledgePayload {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
    }

    public static AcknowledgePayload delivered(UUID conversationId, long upToSeq) {
        return new AcknowledgePayload(conversationId, ACK_DELIVERED, upToSeq, System.currentTimeMillis());
    }

    public static AcknowledgePayload read(UUID conversationId, long upToSeq) {
        return new AcknowledgePayload(conversationId, ACK_READ, upToSeq, System.currentTimeMillis());
    }

    public ByteBuffer encode() {
        ByteBuffer buf = ByteBuffer.allocate(16 + 1 + 8 + 8);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        buf.put(ackType);
        buf.putLong(upToSeq);
        buf.putLong(ackTimestamp);
        buf.flip();
        return buf;
    }

    public static AcknowledgePayload decode(ByteBuffer src) {
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        byte ackType = src.get();
        long upToSeq = src.getLong();
        long ackTimestamp = src.getLong();
        return new AcknowledgePayload(conversationId, ackType, upToSeq, ackTimestamp);
    }
}
