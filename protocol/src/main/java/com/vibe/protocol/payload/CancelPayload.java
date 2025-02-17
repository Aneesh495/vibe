package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for CANCEL frame (0x0E).
 */
public record CancelPayload(
        long targetCorrelationId,
        String reason
) {

    public CancelPayload {
        Objects.requireNonNull(reason, "reason must not be null");
    }

    public ByteBuffer encode() {
        int size = 8 + PayloadCodecUtil.stringByteLength(reason);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putLong(targetCorrelationId);
        PayloadCodecUtil.writeString(buf, reason);
        buf.flip();
        return buf;
    }

    public static CancelPayload decode(ByteBuffer src) {
        long targetCorrelationId = src.getLong();
        String reason = PayloadCodecUtil.readString(src);
        return new CancelPayload(targetCorrelationId, reason);
    }
}
