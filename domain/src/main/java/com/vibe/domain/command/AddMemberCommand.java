package com.vibe.domain.command;

import com.vibe.domain.entity.MemberRole;
import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record AddMemberCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        UUID conversationId,
        String targetUserId,
        MemberRole role
) implements DomainCommand {

    public AddMemberCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(targetUserId, "targetUserId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
    }

    @Override
    public CommandType type() {
        return CommandType.ADD_MEMBER;
    }

    @Override
    public int contentHash() {
        return Objects.hash(conversationId, targetUserId, role);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
                + 16
                + PayloadCodecUtil.stringByteLength(targetUserId)
                + 1; // role ordinal
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort(type().code());
        PayloadCodecUtil.writeUUID(buf, commandId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeString(buf, callerUserId);
        PayloadCodecUtil.writeString(buf, callerDeviceId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        PayloadCodecUtil.writeString(buf, targetUserId);
        buf.put((byte) role.ordinal());
        buf.flip();
        return buf;
    }

    public static AddMemberCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        String targetUserId = PayloadCodecUtil.readString(src);
        MemberRole role = MemberRole.values()[src.get()];
        return new AddMemberCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                conversationId, targetUserId, role
        );
    }
}
