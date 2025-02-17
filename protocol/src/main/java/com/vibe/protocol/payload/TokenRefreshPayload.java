package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for TOKEN_REFRESH frame (0x05).
 */
public record TokenRefreshPayload(
        String currentToken,
        String deviceId
) {

    public TokenRefreshPayload {
        Objects.requireNonNull(currentToken, "currentToken must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
    }

    public ByteBuffer encode() {
        int size = PayloadCodecUtil.stringByteLength(currentToken) + PayloadCodecUtil.stringByteLength(deviceId);
        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeString(buf, currentToken);
        PayloadCodecUtil.writeString(buf, deviceId);
        buf.flip();
        return buf;
    }

    public static TokenRefreshPayload decode(ByteBuffer src) {
        String currentToken = PayloadCodecUtil.readString(src);
        String deviceId = PayloadCodecUtil.readString(src);
        return new TokenRefreshPayload(currentToken, deviceId);
    }
}
