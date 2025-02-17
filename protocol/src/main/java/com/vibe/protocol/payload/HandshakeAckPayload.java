package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for HANDSHAKE_ACK frame (0x02).
 */
public record HandshakeAckPayload(
        int serverVersion,
        int heartbeatIntervalMs,
        int maxFrameSize,
        long serverEpochTimestamp,
        byte[] sessionSalt
) {

    public HandshakeAckPayload {
        Objects.requireNonNull(sessionSalt, "sessionSalt must not be null");
    }

    public ByteBuffer encode() {
        int size = 4 + 4 + 4 + 8 + PayloadCodecUtil.shortBytesLength(sessionSalt);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(serverVersion);
        buf.putInt(heartbeatIntervalMs);
        buf.putInt(maxFrameSize);
        buf.putLong(serverEpochTimestamp);
        PayloadCodecUtil.writeShortBytes(buf, sessionSalt);
        buf.flip();
        return buf;
    }

    public static HandshakeAckPayload decode(ByteBuffer src) {
        int serverVersion = src.getInt();
        int heartbeatIntervalMs = src.getInt();
        int maxFrameSize = src.getInt();
        long serverEpochTimestamp = src.getLong();
        byte[] sessionSalt = PayloadCodecUtil.readShortBytes(src);
        return new HandshakeAckPayload(serverVersion, heartbeatIntervalMs, maxFrameSize, serverEpochTimestamp, sessionSalt);
    }
}
