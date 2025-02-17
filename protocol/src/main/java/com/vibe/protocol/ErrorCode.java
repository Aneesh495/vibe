package com.vibe.protocol;

/**
 * Categorized 16-bit error codes for the Vibe protocol.
 */
public enum ErrorCode {
    // Protocol Errors (0x1000 - 0x1FFF)
    PROTOCOL_MALFORMED_HEADER(0x1001, "Malformed frame header or magic mismatch"),
    PROTOCOL_UNSUPPORTED_VERSION(0x1002, "Unsupported protocol version"),
    PROTOCOL_FRAME_TOO_LARGE(0x1003, "Frame exceeds maximum allowed size"),
    PROTOCOL_INVALID_STATE(0x1004, "Frame received in invalid connection state"),
    PROTOCOL_DECODING_FAILURE(0x1005, "Payload does not match expected schema"),
    PROTOCOL_CHECKSUM_MISMATCH(0x1006, "Header CRC32C checksum mismatch"),

    // Authorization Failures (0x2000 - 0x2FFF)
    AUTH_INVALID_CREDENTIALS(0x2001, "Invalid username or password"),
    AUTH_TOKEN_EXPIRED(0x2002, "Session token has expired"),
    AUTH_TOKEN_INVALID(0x2003, "Session token signature or format is invalid"),
    AUTH_FORBIDDEN(0x2004, "Access denied: insufficient permissions"),
    AUTH_DEVICE_REVOKED(0x2005, "Device session has been revoked"),

    // Overload & Backpressure (0x3000 - 0x3FFF)
    OVERLOAD_QUEUE_FULL(0x3001, "Server or connection mailbox queue is full"),
    OVERLOAD_RATE_LIMIT_EXCEEDED(0x3002, "Rate limit exceeded"),
    OVERLOAD_SLOW_CONSUMER(0x3003, "Client backpressure threshold exceeded; disconnected with resumable cursor"),

    // Durability & Consensus (0x4000 - 0x4FFF)
    DURABILITY_QUORUM_LOST(0x4001, "Consensus quorum lost"),
    DURABILITY_LEADER_STEPDOWN(0x4002, "Consensus leader stepped down; please retry"),
    DURABILITY_DISK_FULL(0x4003, "Storage engine cannot allocate WAL space"),
    DURABILITY_TIMEOUT(0x4004, "Storage commit timed out before reaching quorum"),

    // Domain & Business Rejections (0x5000 - 0x5FFF)
    DOMAIN_USER_NOT_FOUND(0x5001, "User not found"),
    DOMAIN_USER_ALREADY_EXISTS(0x5002, "User already exists"),
    DOMAIN_USER_BLOCKED(0x5003, "Operation blocked by user privacy settings"),
    DOMAIN_CONVERSATION_NOT_FOUND(0x5004, "Conversation not found"),
    DOMAIN_NOT_A_MEMBER(0x5005, "Caller is not a member of this conversation"),
    DOMAIN_IDEMPOTENCY_CONFLICT(0x5006, "Client message ID was previously submitted with conflicting content"),
    DOMAIN_MESSAGE_NOT_FOUND(0x5007, "Target message ID not found"),
    DOMAIN_INVALID_INPUT(0x5008, "Invalid input data or business constraint violation"),

    UNKNOWN_ERROR(0xFFFF, "Unknown internal error");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return UNKNOWN_ERROR;
    }
}
