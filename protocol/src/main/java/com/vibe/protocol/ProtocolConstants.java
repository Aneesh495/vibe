package com.vibe.protocol;

/**
 * Core wire protocol constants for the Vibe binary transport.
 */
public final class ProtocolConstants {

    /**
     * Protocol Magic: ASCII "VIBE" (0x56494245).
     */
    public static final int MAGIC = 0x56494245;

    /**
     * Current protocol major version.
     */
    public static final byte VERSION_1 = 0x01;

    /**
     * Fixed size in bytes of a Vibe binary frame header.
     * 4 (Magic) + 1 (Version) + 1 (Type) + 2 (Flags) + 8 (CorrelationId) + 4 (PayloadLength) + 4 (HeaderCRC) = 24.
     */
    public static final int HEADER_SIZE = 24;

    /**
     * Absolute maximum allowed frame payload length: 16 MiB.
     */
    public static final int MAX_PAYLOAD_LENGTH = 16 * 1024 * 1024;

    /**
     * Standard chunk size for attachment transfers: 64 KiB.
     */
    public static final int ATTACHMENT_CHUNK_SIZE = 64 * 1024;

    /**
     * Default heartbeat interval in milliseconds: 15,000 ms (15s).
     */
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 15_000;

    /**
     * Default heartbeat timeout before dropping connection: 45,000 ms (45s).
     */
    public static final int DEFAULT_HEARTBEAT_TIMEOUT_MS = 45_000;

    private ProtocolConstants() {
        // Prevent instantiation
    }
}
