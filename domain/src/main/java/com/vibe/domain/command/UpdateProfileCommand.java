package com.vibe.domain.command;

import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record UpdateProfileCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        String displayName,
        String bio,
        String avatarUrl
) implements DomainCommand {

    public UpdateProfileCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
        displayName = displayName != null ? displayName : "";
        bio = bio != null ? bio : "";
        avatarUrl = avatarUrl != null ? avatarUrl : "";
    }

    @Override
    public CommandType type() {
        return CommandType.UPDATE_PROFILE;
    }

    @Override
    public int contentHash() {
        return Objects.hash(displayName, bio, avatarUrl);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
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
        PayloadCodecUtil.writeString(buf, displayName);
        PayloadCodecUtil.writeString(buf, bio);
        PayloadCodecUtil.writeString(buf, avatarUrl);
        buf.flip();
        return buf;
    }

    public static UpdateProfileCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        String displayName = PayloadCodecUtil.readString(src);
        String bio = PayloadCodecUtil.readString(src);
        String avatarUrl = PayloadCodecUtil.readString(src);
        return new UpdateProfileCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                displayName, bio, avatarUrl
        );
    }
}
