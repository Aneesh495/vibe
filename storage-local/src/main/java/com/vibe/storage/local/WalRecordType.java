package com.vibe.storage.local;

/**
 * Types of records stored in WAL segments.
 */
public enum WalRecordType {
    COMMAND((byte) 0x01),
    COMMIT_BOUNDARY((byte) 0x02),
    CHECKPOINT((byte) 0x03);

    private final byte code;

    WalRecordType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    public static WalRecordType fromCode(byte code) {
        for (WalRecordType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return null;
    }
}
