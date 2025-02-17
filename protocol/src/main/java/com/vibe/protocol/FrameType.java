package com.vibe.protocol;

/**
 * Enumeration of all frame types supported by the Vibe wire protocol.
 */
public enum FrameType {
    HANDSHAKE((byte) 0x01),
    HANDSHAKE_ACK((byte) 0x02),
    AUTHENTICATE((byte) 0x03),
    AUTH_RESULT((byte) 0x04),
    TOKEN_REFRESH((byte) 0x05),
    COMMAND((byte) 0x06),
    COMMAND_RESULT((byte) 0x07),
    SUBSCRIBE((byte) 0x08),
    CONVERSATION_EVENT((byte) 0x09),
    ACKNOWLEDGE((byte) 0x0A),
    RESUME((byte) 0x0B),
    HEARTBEAT((byte) 0x0C),
    ATTACHMENT_CHUNK((byte) 0x0D),
    CANCEL((byte) 0x0E),
    ERROR((byte) 0x0F);

    private final byte code;

    FrameType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    private static final FrameType[] LOOKUP = new FrameType[256];

    static {
        for (FrameType type : values()) {
            LOOKUP[type.code & 0xFF] = type;
        }
    }

    /**
     * Resolves a FrameType from its raw byte code.
     *
     * @param code raw byte code
     * @return matching FrameType or null if unknown
     */
    public static FrameType fromCode(byte code) {
        return LOOKUP[code & 0xFF];
    }
}
