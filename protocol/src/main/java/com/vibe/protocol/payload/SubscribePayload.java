package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload for SUBSCRIBE frame (0x08).
 */
public record SubscribePayload(
        UUID conversationId,
        long lastKnownSeq,
        int limit
) {

    public SubscribePayload {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        if (limit <= 0) {
            limit = 100;
        }
    }

    public ByteBuffer encode() {
        ByteBuffer buf = ByteBuffer.allocate(16 + 8 + 4);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        buf.putLong(lastKnownSeq);
        buf.putInt(limit);
        buf.flip();
        return buf;
    }

    public static SubscribePayload decode(ByteBuffer src) {
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        long lastKnownSeq = src.getLong();
        int limit = src.getInt();
        return new SubscribePayload(conversationId, lastKnownSeq, limit);
    }
}
