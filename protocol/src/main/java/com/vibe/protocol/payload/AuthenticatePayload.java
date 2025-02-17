package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Payload for AUTHENTICATE frame (0x03).
 */
public record AuthenticatePayload(
        byte authMethod,
        String username,
        String credential,
        String deviceId
) {

    public static final byte METHOD_PASSWORD = 0x01;
    public static final byte METHOD_TOKEN = 0x02;

    public AuthenticatePayload {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(credential, "credential must not be null");
        Objects.requireNonNull(deviceId, "deviceId must not be null");
    }

    public static AuthenticatePayload withPassword(String username, String password, String deviceId) {
        return new AuthenticatePayload(METHOD_PASSWORD, username, password, deviceId);
    }

    public static AuthenticatePayload withToken(String username, String token, String deviceId) {
        return new AuthenticatePayload(METHOD_TOKEN, username, token, deviceId);
    }

    public ByteBuffer encode() {
        int size = 1 + PayloadCodecUtil.stringByteLength(username)
                + PayloadCodecUtil.stringByteLength(credential)
                + PayloadCodecUtil.stringByteLength(deviceId);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(authMethod);
        PayloadCodecUtil.writeString(buf, username);
        PayloadCodecUtil.writeString(buf, credential);
        PayloadCodecUtil.writeString(buf, deviceId);
        buf.flip();
        return buf;
    }

    public static AuthenticatePayload decode(ByteBuffer src) {
        byte authMethod = src.get();
        String username = PayloadCodecUtil.readString(src);
        String credential = PayloadCodecUtil.readString(src);
        String deviceId = PayloadCodecUtil.readString(src);
        return new AuthenticatePayload(authMethod, username, credential, deviceId);
    }
}
