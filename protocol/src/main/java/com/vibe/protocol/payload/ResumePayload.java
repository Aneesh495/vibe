package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for RESUME frame (0x0B) to resume disconnected sessions.
 */
public record ResumePayload(
        String sessionToken,
        String deviceId,
        long lastReceivedEventSeq
) {

    public ResumePayload {
        Objects.requireNonNull(sessionToken, "sessionToken must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
    }

    public ByteBuffer encode() {
        int size = PayloadCodecUtil.stringByteLength(sessionToken) + PayloadCodecUtil.stringByteLength(deviceId) + 8;
        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeString(buf, sessionToken);
        PayloadCodecUtil.writeString(buf, deviceId);
        buf.putLong(lastReceivedEventSeq);
        buf.flip();
        return buf;
    }

    public static ResumePayload decode(ByteBuffer src) {
        String sessionToken = PayloadCodecUtil.readString(src);
        String deviceId = PayloadCodecUtil.readString(src);
        long lastReceivedEventSeq = src.getLong();
        return new ResumePayload(sessionToken, deviceId, lastReceivedEventSeq);
    }
}
