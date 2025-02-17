package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for HANDSHAKE frame (0x01).
 */
public record HandshakePayload(
        int clientVersion,
        String clientId,
        String deviceId,
        int capabilities
) {

    public HandshakePayload {
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
    }

    public ByteBuffer encode() {
        int size = 4 + PayloadCodecUtil.stringByteLength(clientId) + PayloadCodecUtil.stringByteLength(deviceId) + 4;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(clientVersion);
        PayloadCodecUtil.writeString(buf, clientId);
        PayloadCodecUtil.writeString(buf, deviceId);
        buf.putInt(capabilities);
        buf.flip();
        return buf;
    }

    public static HandshakePayload decode(ByteBuffer src) {
        int clientVersion = src.getInt();
        String clientId = PayloadCodecUtil.readString(src);
        String deviceId = PayloadCodecUtil.readString(src);
        int capabilities = src.getInt();
        return new HandshakePayload(clientVersion, clientId, deviceId, capabilities);
    }
}
