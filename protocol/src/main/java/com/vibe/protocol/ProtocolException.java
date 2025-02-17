package com.vibe.protocol;

import java.util.Objects;

/**
 * Exception thrown when a protocol validation, framing, or decoding error occurs.
 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public ProtocolException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "ErrorCode must not be null");
    }

    public ProtocolException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "ErrorCode must not be null");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
