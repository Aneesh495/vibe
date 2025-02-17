package com.vibe.domain.command;

import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record EditMessageCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        UUID messageId,
        UUID conversationId,
        String newContent
) implements DomainCommand {

    public EditMessageCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(newContent, "newContent must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
    }

    @Override
    public CommandType type() {
        return CommandType.EDIT_MESSAGE;
    }

    @Override
    public int contentHash() {
        return Objects.hash(messageId, conversationId, newContent);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
                + 16 + 16
                + PayloadCodecUtil.stringByteLength(newContent);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort(type().code());
        PayloadCodecUtil.writeUUID(buf, commandId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeString(buf, callerUserId);
        PayloadCodecUtil.writeString(buf, callerDeviceId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeUUID(buf, messageId);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        PayloadCodecUtil.writeString(buf, newContent);
        buf.flip();
        return buf;
    }

    public static EditMessageCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        UUID messageId = PayloadCodecUtil.readUUID(src);
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        String newContent = PayloadCodecUtil.readString(src);
        return new EditMessageCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                messageId, conversationId, newContent
        );
    }
}
