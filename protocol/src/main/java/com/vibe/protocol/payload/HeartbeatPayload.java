package com.vibe.protocol.payload;

import java.nio.ByteBuffer;

/**
 * Payload for HEARTBEAT frame (0x0C).
 */
public record HeartbeatPayload(
        long timestamp,
        boolean isPing
) {

    public static HeartbeatPayload ping() {
        return new HeartbeatPayload(System.currentTimeMillis(), true);
    }

    public static HeartbeatPayload pong(long originalTimestamp) {
        return new HeartbeatPayload(originalTimestamp, false);
    }

    public ByteBuffer encode() {
        ByteBuffer buf = ByteBuffer.allocate(8 + 1);
        buf.putLong(timestamp);
        buf.put((byte) (isPing ? 1 : 0));
        buf.flip();
        return buf;
    }

    public static HeartbeatPayload decode(ByteBuffer src) {
        long timestamp = src.getLong();
        boolean isPing = src.get() == 1;
        return new HeartbeatPayload(timestamp, isPing);
    }
}
