package com.vibe.protocol.payload;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload for COMMAND frame (0x06).
 */
public record CommandPayload(
        CommandType commandType,
        String clientMessageId,
        UUID conversationId,
        byte[] body
) {

    public static final UUID ZERO_UUID = new UUID(0L, 0L);

    public CommandPayload {
        Objects.requireNonNull(commandType, "commandType must not be null");
        clientMessageId = clientMessageId != null ? clientMessageId : "";
        conversationId = conversationId != null ? conversationId : ZERO_UUID;
        body = body != null ? body : new byte[0];
    }

    public ByteBuffer encode() {
        int size = 2 + PayloadCodecUtil.stringByteLength(clientMessageId) + 16 + PayloadCodecUtil.bytesLength(body);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort(commandType.code());
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        PayloadCodecUtil.writeBytes(buf, body);
        buf.flip();
        return buf;
    }

    public static CommandPayload decode(ByteBuffer src) {
        short code = src.getShort();
        CommandType type = CommandType.fromCode(code);
        if (type == null) {
            throw new IllegalArgumentException("Unknown command type code: " + code);
        }
        String clientMessageId = PayloadCodecUtil.readString(src);
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        byte[] body = PayloadCodecUtil.readBytes(src);
        return new CommandPayload(type, clientMessageId, conversationId, body);
    }
}
