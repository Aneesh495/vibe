package com.vibe.protocol;

/**
 * Bitmask flags for modifying frame transmission and processing semantics.
 */
public final class FrameFlags {

    /**
     * Payload compressed with Zstandard or gzip.
     */
    public static final short FLAG_NONE = 0x0000;

    /**
     * Payload is compressed.
     */
    public static final short FLAG_COMPRESSED = 0x0001;

    /**
     * Payload is end-to-end encrypted.
     */
    public static final short FLAG_ENCRYPTED = 0x0002;

    /**
     * Terminal chunk in a chunked streaming transfer.
     */
    public static final short FLAG_LAST_CHUNK = 0x0004;

    /**
     * Urgent frame: bypasses lower priority delivery queues.
     */
    public static final short FLAG_URGENT = 0x0008;

    /**
     * Fire-and-forget: receiver must not return a response frame.
     */
    public static final short FLAG_ONE_WAY = 0x0010;

    /**
     * Event stream carries persistent durable sequence for resumption.
     */
    public static final short FLAG_RESUMABLE = 0x0020;

    /**
     * Request requires synchronous durability commit before acknowledgment.
     */
    public static final short FLAG_SYNC = 0x0040;

    private FrameFlags() {
    }

    public static boolean hasFlag(short flags, short flag) {
        return (flags & flag) == flag;
    }

    public static short setFlag(short flags, short flag) {
        return (short) (flags | flag);
    }

    public static short clearFlag(short flags, short flag) {
        return (short) (flags & ~flag);
    }
}
