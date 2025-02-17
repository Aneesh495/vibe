package com.vibe.domain.command;

import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record RegisterUserCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        String username,
        String passwordHash,
        String displayName,
        String bio,
        String avatarUrl
) implements DomainCommand {

    public RegisterUserCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
        displayName = displayName != null ? displayName : username;
        bio = bio != null ? bio : "";
        avatarUrl = avatarUrl != null ? avatarUrl : "default.png";
    }

    @Override
    public CommandType type() {
        return CommandType.REGISTER_USER;
    }

    @Override
    public int contentHash() {
        return Objects.hash(username, displayName, bio, avatarUrl);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
                + PayloadCodecUtil.stringByteLength(username)
                + PayloadCodecUtil.stringByteLength(passwordHash)
                + PayloadCodecUtil.stringByteLength(displayName)
                + PayloadCodecUtil.stringByteLength(bio)
                + PayloadCodecUtil.stringByteLength(avatarUrl);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort(type().code());
        PayloadCodecUtil.writeUUID(buf, commandId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeString(buf, callerUserId);
        PayloadCodecUtil.writeString(buf, callerDeviceId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeString(buf, username);
        PayloadCodecUtil.writeString(buf, passwordHash);
        PayloadCodecUtil.writeString(buf, displayName);
        PayloadCodecUtil.writeString(buf, bio);
        PayloadCodecUtil.writeString(buf, avatarUrl);
        buf.flip();
        return buf;
    }

    public static RegisterUserCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        String username = PayloadCodecUtil.readString(src);
        String passwordHash = PayloadCodecUtil.readString(src);
        String displayName = PayloadCodecUtil.readString(src);
        String bio = PayloadCodecUtil.readString(src);
        String avatarUrl = PayloadCodecUtil.readString(src);
        return new RegisterUserCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                username, passwordHash, displayName, bio, avatarUrl
        );
    }
}
