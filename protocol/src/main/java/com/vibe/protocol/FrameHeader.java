package com.vibe.protocol;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Immutable 24-byte header of a Vibe binary frame.
 */
public record FrameHeader(
        int magic,
        byte version,
        FrameType type,
        short flags,
        long correlationId,
        int payloadLength,
        int headerCrc
) {

    public FrameHeader {
        Objects.requireNonNull(type, "FrameType must not be null");
        if (payloadLength < 0 || payloadLength > ProtocolConstants.MAX_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException("Payload length out of bounds: " + payloadLength);
        }
    }

    /**
     * Constructs a new FrameHeader, computing the header CRC32C automatically.
     */
    public static FrameHeader create(
            byte version,
            FrameType type,
            short flags,
            long correlationId,
            int payloadLength
    ) {
        int crc = computeHeaderCrc(ProtocolConstants.MAGIC, version, type.code(), flags, correlationId, payloadLength);
        return new FrameHeader(ProtocolConstants.MAGIC, version, type, flags, correlationId, payloadLength, crc);
    }

    /**
     * Constructs a FrameHeader with default version 1.
     */
    public static FrameHeader create(
            FrameType type,
            short flags,
            long correlationId,
            int payloadLength
    ) {
        return create(ProtocolConstants.VERSION_1, type, flags, correlationId, payloadLength);
    }

    /**
     * Encodes this 24-byte header into the destination buffer.
     */
    public void encode(ByteBuffer dst) {
        if (dst.remaining() < ProtocolConstants.HEADER_SIZE) {
            throw new IllegalArgumentException("Destination buffer too small for header: " + dst.remaining());
        }
        dst.putInt(magic);
        dst.put(version);
        dst.put(type.code());
        dst.putShort(flags);
        dst.putLong(correlationId);
        dst.putInt(payloadLength);
        dst.putInt(headerCrc);
    }

    /**
     * Decodes and validates a FrameHeader from the source buffer.
     * Requires at least 24 bytes remaining in the buffer.
     */
    public static FrameHeader decode(ByteBuffer src) {
        if (src.remaining() < ProtocolConstants.HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer underflow: expected at least 24 bytes, got " + src.remaining());
        }

        int headerStart = src.position();
        int magic = src.getInt();
        if (magic != ProtocolConstants.MAGIC) {
            throw new ProtocolException(ErrorCode.PROTOCOL_MALFORMED_HEADER,
                    String.format("Invalid magic 0x%08X; expected 0x%08X", magic, ProtocolConstants.MAGIC));
        }

        byte version = src.get();
        if (version != ProtocolConstants.VERSION_1) {
            throw new ProtocolException(ErrorCode.PROTOCOL_UNSUPPORTED_VERSION,
                    "Unsupported protocol version: " + version);
        }

        byte typeCode = src.get();
        FrameType type = FrameType.fromCode(typeCode);
        if (type == null) {
            throw new ProtocolException(ErrorCode.PROTOCOL_MALFORMED_HEADER,
                    "Unknown frame type code: " + (typeCode & 0xFF));
        }

        short flags = src.getShort();
        long correlationId = src.getLong();
        int payloadLength = src.getInt();
        if (payloadLength < 0 || payloadLength > ProtocolConstants.MAX_PAYLOAD_LENGTH) {
            throw new ProtocolException(ErrorCode.PROTOCOL_FRAME_TOO_LARGE,
                    "Payload length exceeds maximum allowed: " + payloadLength);
        }

        int expectedCrc = src.getInt();
        int computedCrc = CRC32CUtil.compute(src, headerStart, 20);
        if (expectedCrc != computedCrc) {
            throw new ProtocolException(ErrorCode.PROTOCOL_CHECKSUM_MISMATCH,
                    String.format("Header CRC mismatch: expected 0x%08X, computed 0x%08X", expectedCrc, computedCrc));
        }

        return new FrameHeader(magic, version, type, flags, correlationId, payloadLength, expectedCrc);
    }

    public static int computeHeaderCrc(int magic, byte version, byte type, short flags, long correlationId, int payloadLength) {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.putInt(magic);
        buf.put(version);
        buf.put(type);
        buf.putShort(flags);
        buf.putLong(correlationId);
        buf.putInt(payloadLength);
        return CRC32CUtil.compute(buf.array(), 0, 20);
    }

    public boolean hasFlag(short flag) {
        return FrameFlags.hasFlag(flags, flag);
    }
}
