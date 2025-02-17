package com.vibe.protocol;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

/**
 * High-performance hardware-accelerated CRC32C checksum utility.
 */
public final class CRC32CUtil {

    private CRC32CUtil() {
    }

    /**
     * Calculates the CRC32C value of a byte array.
     */
    public static int compute(byte[] data, int offset, int length) {
        CRC32C crc = new CRC32C();
        crc.update(data, offset, length);
        return (int) crc.getValue();
    }

    /**
     * Calculates the CRC32C value from a ByteBuffer range without modifying the buffer's position.
     */
    public static int compute(ByteBuffer buffer, int offset, int length) {
        CRC32C crc = new CRC32C();
        int originalPos = buffer.position();
        int originalLimit = buffer.limit();
        try {
            buffer.position(offset);
            buffer.limit(offset + length);
            crc.update(buffer);
            return (int) crc.getValue();
        } finally {
            buffer.limit(originalLimit);
            buffer.position(originalPos);
        }
    }
}
