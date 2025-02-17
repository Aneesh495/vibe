package com.vibe.protocol;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Encodes Vibe protocol frames into destination ByteBuffers for transmission over channels.
 */
public final class FrameEncoder {

    private FrameEncoder() {
    }

    /**
     * Encodes a frame into a newly allocated heap ByteBuffer.
     */
    public static ByteBuffer encode(Frame frame) {
        Objects.requireNonNull(frame, "Frame must not be null");
        ByteBuffer buffer = ByteBuffer.allocate(frame.totalSize());
        encode(frame, buffer);
        buffer.flip();
        return buffer;
    }

    /**
     * Encodes a frame into an existing destination ByteBuffer.
     */
    public static void encode(Frame frame, ByteBuffer dst) {
        Objects.requireNonNull(frame, "Frame must not be null");
        Objects.requireNonNull(dst, "Destination buffer must not be null");

        frame.header().encode(dst);
        if (frame.payloadLength() > 0) {
            ByteBuffer payload = frame.payload();
            dst.put(payload);
        }
    }
}
