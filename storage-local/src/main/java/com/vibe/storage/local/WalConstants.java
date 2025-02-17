package com.vibe.storage.local;

/**
 * Constants governing the segmented write-ahead log (WAL) and snapshot subsystem.
 */
public final class WalConstants {

    /** WAL Magic: "VIBEWAL1" */
    public static final long SEGMENT_MAGIC = 0x5649424557414C01L;

    /** Segment Header size: 64 bytes */
    public static final int SEGMENT_HEADER_SIZE = 64;

    /** Record record marker: 0xAA55 */
    public static final short RECORD_MARKER = (short) 0xAA55;

    /** Fixed size of record envelope preceding payload: 20 bytes (marker 2 + type 1 + version 1 + index 8 + len 4 + crc 4) */
    public static final int RECORD_HEADER_SIZE = 20;

    /** Default maximum segment file size: 64 MiB */
    public static final long DEFAULT_MAX_SEGMENT_SIZE = 64 * 1024 * 1024L;

    /** Default group commit sync interval: 5 ms */
    public static final long DEFAULT_COMMIT_INTERVAL_MS = 5L;

    /** Default max batch size before immediate fsync: 256 records */
    public static final int DEFAULT_MAX_BATCH_SIZE = 256;

    private WalConstants() {
    }
}
