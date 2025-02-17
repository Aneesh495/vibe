package com.vibe.storage.local;

import com.vibe.protocol.CRC32CUtil;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Record written to a WAL segment.
 */
public record WalRecord(
        WalRecordType type,
        byte version,
        long logIndex,
        int checksumCRC32C,
        byte[] payload
) {

    public WalRecord {
        Objects.requireNonNull(type, "WalRecordType must not be null");
        payload = payload != null ? payload : new byte[0];
    }

    public static WalRecord create(WalRecordType type, long logIndex, byte[] payload) {
        int crc = computeCrc(type.code(), (byte) 1, logIndex, payload);
        return new WalRecord(type, (byte) 1, logIndex, crc, payload);
    }

    public int totalSize() {
        return WalConstants.RECORD_HEADER_SIZE + payload.length;
    }

    public void encode(ByteBuffer dst) {
        if (dst.remaining() < totalSize()) {
            throw new IllegalArgumentException("Destination buffer too small for WAL record");
        }
        dst.putShort(WalConstants.RECORD_MARKER);
        dst.put(type.code());
        dst.put(version);
        dst.putLong(logIndex);
        dst.putInt(payload.length);
        dst.putInt(checksumCRC32C);
        dst.put(payload);
    }

    public static WalRecord decode(ByteBuffer src) {
        if (src.remaining() < WalConstants.RECORD_HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer underflow reading WAL record header");
        }

        short marker = src.getShort();
        if (marker != WalConstants.RECORD_MARKER) {
            throw new StorageCorruptionException(String.format(
                    "Invalid record marker 0x%04X; expected 0x%04X", marker, WalConstants.RECORD_MARKER));
        }

        byte typeCode = src.get();
        WalRecordType type = WalRecordType.fromCode(typeCode);
        if (type == null) {
            throw new StorageCorruptionException("Unknown WAL record type code: " + typeCode);
        }

        byte version = src.get();
        long logIndex = src.getLong();
        int payloadLen = src.getInt();
        if (payloadLen < 0 || src.remaining() < payloadLen + 4) {
            throw new IllegalArgumentException("Buffer underflow reading record payload");
        }

        int expectedCrc = src.getInt();
        byte[] payload = new byte[payloadLen];
        src.get(payload);

        int computedCrc = computeCrc(typeCode, version, logIndex, payload);
        if (expectedCrc != computedCrc) {
            throw new StorageCorruptionException(String.format(
                    "Record CRC mismatch at logIndex %d: expected 0x%08X, computed 0x%08X",
                    logIndex, expectedCrc, computedCrc));
        }

        return new WalRecord(type, version, logIndex, expectedCrc, payload);
    }

    private static int computeCrc(byte typeCode, byte version, long logIndex, byte[] payload) {
        ByteBuffer buf = ByteBuffer.allocate(14 + (payload != null ? payload.length : 0));
        buf.put(typeCode);
        buf.put(version);
        buf.putLong(logIndex);
        buf.putInt(payload != null ? payload.length : 0);
        if (payload != null && payload.length > 0) {
            buf.put(payload);
        }
        return CRC32CUtil.compute(buf.array(), 0, buf.position());
    }
}
