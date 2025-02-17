package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for AUTH_RESULT frame (0x04).
 */
public record AuthResultPayload(
        byte status,
        String userId,
        String token,
        long tokenExpiresAt,
        int errorCode,
        String errorMessage
) {

    public static final byte STATUS_SUCCESS = 0x00;
    public static final byte STATUS_FAILURE = 0x01;

    public AuthResultPayload {
        userId = userId != null ? userId : "";
        token = token != null ? token : "";
        errorMessage = errorMessage != null ? errorMessage : "";
    }

    public static AuthResultPayload success(String userId, String token, long expiresAt) {
        return new AuthResultPayload(STATUS_SUCCESS, userId, token, expiresAt, 0, "");
    }

    public static AuthResultPayload failure(int errorCode, String errorMessage) {
        return new AuthResultPayload(STATUS_FAILURE, "", "", 0L, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return status == STATUS_SUCCESS;
    }

    public ByteBuffer encode() {
        int size = 1 + PayloadCodecUtil.stringByteLength(userId)
                + PayloadCodecUtil.stringByteLength(token)
                + 8
                + 2
                + PayloadCodecUtil.stringByteLength(errorMessage);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(status);
        PayloadCodecUtil.writeString(buf, userId);
        PayloadCodecUtil.writeString(buf, token);
        buf.putLong(tokenExpiresAt);
        buf.putShort((short) errorCode);
        PayloadCodecUtil.writeString(buf, errorMessage);
        buf.flip();
        return buf;
    }

    public static AuthResultPayload decode(ByteBuffer src) {
        byte status = src.get();
        String userId = PayloadCodecUtil.readString(src);
        String token = PayloadCodecUtil.readString(src);
        long tokenExpiresAt = src.getLong();
        int errorCode = src.getShort() & 0xFFFF;
        String errorMessage = PayloadCodecUtil.readString(src);
        return new AuthResultPayload(status, userId, token, tokenExpiresAt, errorCode, errorMessage);
    }
}
