package com.vibe.storage.local;

import com.vibe.protocol.CRC32CUtil;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Immutable 64-byte header of a WAL segment file.
 */
public record SegmentHeader(
        long magic,
        int version,
        long segmentId,
        long startIndex,
        long createdAt,
        int headerCrc
) {

    public static SegmentHeader create(long segmentId, long startIndex) {
        long now = System.currentTimeMillis();
        int crc = computeCrc(WalConstants.SEGMENT_MAGIC, 1, segmentId, startIndex, now);
        return new SegmentHeader(WalConstants.SEGMENT_MAGIC, 1, segmentId, startIndex, now, crc);
    }

    public void encode(ByteBuffer dst) {
        if (dst.remaining() < WalConstants.SEGMENT_HEADER_SIZE) {
            throw new IllegalArgumentException("Destination buffer too small for segment header");
        }
        dst.putLong(magic);
        dst.putInt(version);
        dst.putLong(segmentId);
        dst.putLong(startIndex);
        dst.putLong(createdAt);
        dst.putInt(headerCrc);
        // 24 reserved bytes zero-padded
        dst.put(new byte[24]);
    }

    public static SegmentHeader decode(ByteBuffer src) {
        if (src.remaining() < WalConstants.SEGMENT_HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer underflow reading segment header");
        }

        int startPos = src.position();
        long magic = src.getLong();
        if (magic != WalConstants.SEGMENT_MAGIC) {
            throw new StorageCorruptionException(String.format(
                    "Invalid WAL segment magic: 0x%016X; expected 0x%016X", magic, WalConstants.SEGMENT_MAGIC));
        }

        int version = src.getInt();
        if (version != 1) {
            throw new StorageCorruptionException("Unsupported WAL segment version: " + version);
        }

        long segmentId = src.getLong();
        long startIndex = src.getLong();
        long createdAt = src.getLong();
        int expectedCrc = src.getInt();

        // Skip 28 reserved bytes
        src.position(startPos + WalConstants.SEGMENT_HEADER_SIZE);

        int computedCrc = computeCrc(magic, version, segmentId, startIndex, createdAt);
        if (expectedCrc != computedCrc) {
            throw new StorageCorruptionException(String.format(
                    "Segment header CRC mismatch: expected 0x%08X, computed 0x%08X", expectedCrc, computedCrc));
        }

        return new SegmentHeader(magic, version, segmentId, startIndex, createdAt, expectedCrc);
    }

    private static int computeCrc(long magic, int version, long segmentId, long startIndex, long createdAt) {
        ByteBuffer buf = ByteBuffer.allocate(36);
        buf.putLong(magic);
        buf.putInt(version);
        buf.putLong(segmentId);
        buf.putLong(startIndex);
        buf.putLong(createdAt);
        return CRC32CUtil.compute(buf.array(), 0, 36);
    }
}
