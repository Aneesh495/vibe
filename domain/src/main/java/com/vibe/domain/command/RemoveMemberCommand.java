package com.vibe.domain.command;

import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record RemoveMemberCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        UUID conversationId,
        String targetUserId
) implements DomainCommand {

    public RemoveMemberCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(targetUserId, "targetUserId must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
    }

    @Override
    public CommandType type() {
        return CommandType.REMOVE_MEMBER;
    }

    @Override
    public int contentHash() {
        return Objects.hash(conversationId, targetUserId);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
                + 16
                + PayloadCodecUtil.stringByteLength(targetUserId);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort(type().code());
        PayloadCodecUtil.writeUUID(buf, commandId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeString(buf, callerUserId);
        PayloadCodecUtil.writeString(buf, callerDeviceId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        PayloadCodecUtil.writeString(buf, targetUserId);
        buf.flip();
        return buf;
    }

    public static RemoveMemberCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        String targetUserId = PayloadCodecUtil.readString(src);
        return new RemoveMemberCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                conversationId, targetUserId
        );
    }
}
