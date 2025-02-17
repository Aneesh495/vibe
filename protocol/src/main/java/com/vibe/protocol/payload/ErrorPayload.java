package com.vibe.protocol.payload;

import com.vibe.protocol.ErrorCode;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for ERROR frame (0x0F) returning typed error codes and diagnostics.
 */
public record ErrorPayload(
        int errorCode,
        String message,
        byte[] details
) {

    public ErrorPayload {
        message = message != null ? message : "";
        details = details != null ? details : new byte[0];
    }

    public static ErrorPayload fromErrorCode(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        return new ErrorPayload(errorCode.code(), message != null ? message : errorCode.defaultMessage(), new byte[0]);
    }

    public static ErrorPayload fromErrorCode(ErrorCode errorCode) {
        return fromErrorCode(errorCode, errorCode.defaultMessage());
    }

    public ErrorCode resolveErrorCode() {
        return ErrorCode.fromCode(errorCode);
    }

    public ByteBuffer encode() {
        int size = 2 + PayloadCodecUtil.stringByteLength(message) + PayloadCodecUtil.bytesLength(details);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort((short) errorCode);
        PayloadCodecUtil.writeString(buf, message);
        PayloadCodecUtil.writeBytes(buf, details);
        buf.flip();
        return buf;
    }

    public static ErrorPayload decode(ByteBuffer src) {
        int errorCode = src.getShort() & 0xFFFF;
        String message = PayloadCodecUtil.readString(src);
        byte[] details = PayloadCodecUtil.readBytes(src);
        return new ErrorPayload(errorCode, message, details);
    }
}
