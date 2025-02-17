package com.vibe.protocol;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Representation of a complete Vibe protocol frame comprising a 24-byte header and payload.
 */
public final class Frame {

    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0).asReadOnlyBuffer();

    private final FrameHeader header;
    private final ByteBuffer payload;

    public Frame(FrameHeader header, ByteBuffer payload) {
        this.header = Objects.requireNonNull(header, "FrameHeader must not be null");
        this.payload = payload != null ? payload : EMPTY_BUFFER;
        if (this.payload.remaining() != header.payloadLength()) {
            throw new IllegalArgumentException(String.format(
                    "Payload buffer remaining bytes (%d) does not match header payloadLength (%d)",
                    this.payload.remaining(), header.payloadLength()));
        }
    }

    public static Frame create(FrameType type, short flags, long correlationId, ByteBuffer payload) {
        int length = payload != null ? payload.remaining() : 0;
        FrameHeader header = FrameHeader.create(type, flags, correlationId, length);
        return new Frame(header, payload != null ? payload : EMPTY_BUFFER);
    }

    public static Frame create(FrameType type, long correlationId, ByteBuffer payload) {
        return create(type, FrameFlags.FLAG_NONE, correlationId, payload);
    }

    public static Frame empty(FrameType type, long correlationId) {
        return create(type, FrameFlags.FLAG_NONE, correlationId, EMPTY_BUFFER);
    }

    public static Frame heartbeat(long correlationId) {
        return empty(FrameType.HEARTBEAT, correlationId);
    }

    public FrameHeader header() {
        return header;
    }

    public FrameType type() {
        return header.type();
    }

    public short flags() {
        return header.flags();
    }

    public long correlationId() {
        return header.correlationId();
    }

    public int payloadLength() {
        return header.payloadLength();
    }

    /**
     * Returns a duplicate of the payload buffer ready for reading.
     */
    public ByteBuffer payload() {
        return payload.asReadOnlyBuffer();
    }

    /**
     * Total serialized length in bytes (24 bytes header + payloadLength).
     */
    public int totalSize() {
        return ProtocolConstants.HEADER_SIZE + header.payloadLength();
    }

    @Override
    public String toString() {
        return "Frame{" +
                "type=" + header.type() +
                ", flags=" + header.flags() +
                ", correlationId=" + header.correlationId() +
                ", payloadLength=" + header.payloadLength() +
                '}';
    }
}
