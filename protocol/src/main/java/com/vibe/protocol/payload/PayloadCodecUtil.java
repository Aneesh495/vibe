package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Utility helpers for schema-bound payload serialization and deserialization.
 */
public final class PayloadCodecUtil {

    private PayloadCodecUtil() {
    }

    public static void writeString(ByteBuffer dst, String value) {
        if (value == null) {
            dst.putShort((short) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) {
            throw new IllegalArgumentException("String exceeds maximum 65535 UTF-8 bytes: " + bytes.length);
        }
        dst.putShort((short) bytes.length);
        dst.put(bytes);
    }

    public static String readString(ByteBuffer src) {
        if (src.remaining() < 2) {
            throw new IllegalArgumentException("Buffer underflow reading string length prefix");
        }
        int length = src.getShort() & 0xFFFF;
        if (length == 0) {
            return "";
        }
        if (src.remaining() < length) {
            throw new IllegalArgumentException(String.format(
                    "Buffer underflow reading string content: expected %d bytes, got %d", length, src.remaining()));
        }
        byte[] bytes = new byte[length];
        src.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeUUID(ByteBuffer dst, UUID uuid) {
        Objects.requireNonNull(uuid, "UUID must not be null");
        dst.putLong(uuid.getMostSignificantBits());
        dst.putLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUUID(ByteBuffer src) {
        if (src.remaining() < 16) {
            throw new IllegalArgumentException("Buffer underflow reading UUID: expected 16 bytes, got " + src.remaining());
        }
        long mostSig = src.getLong();
        long leastSig = src.getLong();
        return new UUID(mostSig, leastSig);
    }

    public static void writeBytes(ByteBuffer dst, byte[] bytes) {
        if (bytes == null) {
            dst.putInt(0);
            return;
        }
        dst.putInt(bytes.length);
        dst.put(bytes);
    }

    public static byte[] readBytes(ByteBuffer src) {
        if (src.remaining() < 4) {
            throw new IllegalArgumentException("Buffer underflow reading byte array length");
        }
        int length = src.getInt();
        if (length < 0 || src.remaining() < length) {
            throw new IllegalArgumentException(String.format(
                    "Buffer underflow reading byte array: expected %d bytes, got %d", length, src.remaining()));
        }
        byte[] bytes = new byte[length];
        src.get(bytes);
        return bytes;
    }

    public static void writeShortBytes(ByteBuffer dst, byte[] bytes) {
        if (bytes == null) {
            dst.putShort((short) 0);
            return;
        }
        if (bytes.length > 65535) {
            throw new IllegalArgumentException("Byte array exceeds maximum 65535 bytes");
        }
        dst.putShort((short) bytes.length);
        dst.put(bytes);
    }

    public static byte[] readShortBytes(ByteBuffer src) {
        if (src.remaining() < 2) {
            throw new IllegalArgumentException("Buffer underflow reading short byte array length");
        }
        int length = src.getShort() & 0xFFFF;
        if (length == 0) {
            return new byte[0];
        }
        if (src.remaining() < length) {
            throw new IllegalArgumentException(String.format(
                    "Buffer underflow reading short byte array: expected %d bytes, got %d", length, src.remaining()));
        }
        byte[] bytes = new byte[length];
        src.get(bytes);
        return bytes;
    }

    public static int stringByteLength(String value) {
        return 2 + (value != null ? value.getBytes(StandardCharsets.UTF_8).length : 0);
    }

    public static int bytesLength(byte[] bytes) {
        return 4 + (bytes != null ? bytes.length : 0);
    }

    public static int shortBytesLength(byte[] bytes) {
        return 2 + (bytes != null ? bytes.length : 0);
    }
}
