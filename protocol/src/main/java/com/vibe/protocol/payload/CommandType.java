package com.vibe.protocol.payload;

/**
 * Enumeration of typed commands submitted via COMMAND frames (0x06).
 */
public enum CommandType {
    REGISTER_USER((short) 0x0001),
    LOGIN_USER((short) 0x0002),
    GET_USER((short) 0x0003),
    UPDATE_PROFILE((short) 0x0004),

    FRIEND_USER((short) 0x0010),
    UNFRIEND_USER((short) 0x0011),
    LIST_FRIENDS((short) 0x0012),
    BLOCK_USER((short) 0x0013),
    UNBLOCK_USER((short) 0x0014),
    LIST_BLOCKED((short) 0x0015),

    CREATE_CONVERSATION((short) 0x0020),
    GET_CONVERSATION((short) 0x0021),
    LIST_CONVERSATIONS((short) 0x0022),
    ADD_MEMBER((short) 0x0023),
    REMOVE_MEMBER((short) 0x0024),

    SEND_MESSAGE((short) 0x0030),
    EDIT_MESSAGE((short) 0x0031),
    DELETE_MESSAGE((short) 0x0032),
    ADD_REACTION((short) 0x0033),
    REMOVE_REACTION((short) 0x0034),
    GET_MESSAGES((short) 0x0035),

    SET_PRESENCE((short) 0x0040),
    INITIATE_ATTACHMENT((short) 0x0050),
    COMPLETE_ATTACHMENT((short) 0x0051);

    private final short code;

    CommandType(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static CommandType fromCode(short code) {
        for (CommandType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
