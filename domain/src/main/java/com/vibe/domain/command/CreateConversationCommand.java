package com.vibe.domain.command;

import com.vibe.domain.entity.ConversationType;
import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateConversationCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        UUID conversationId,
        ConversationType convType,
        String title,
        List<String> memberUserIds
) implements DomainCommand {

    public CreateConversationCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(convType, "convType must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
        title = title != null ? title : "";
        memberUserIds = memberUserIds != null ? memberUserIds : List.of();
    }

    @Override
    public CommandType type() {
        return CommandType.CREATE_CONVERSATION;
    }

    @Override
    public int contentHash() {
        return Objects.hash(conversationId, convType, title, memberUserIds);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
                + 16 + 1
                + PayloadCodecUtil.stringByteLength(title)
                + 2; // member count
        for (String m : memberUserIds) {
            size += PayloadCodecUtil.stringByteLength(m);
        }

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort(type().code());
        PayloadCodecUtil.writeUUID(buf, commandId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeString(buf, callerUserId);
        PayloadCodecUtil.writeString(buf, callerDeviceId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        buf.put((byte) convType.ordinal());
        PayloadCodecUtil.writeString(buf, title);

        buf.putShort((short) memberUserIds.size());
        for (String m : memberUserIds) {
            PayloadCodecUtil.writeString(buf, m);
        }

        buf.flip();
        return buf;
    }

    public static CreateConversationCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        ConversationType convType = ConversationType.values()[src.get()];
        String title = PayloadCodecUtil.readString(src);

        int memberCount = src.getShort() & 0xFFFF;
        List<String> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(PayloadCodecUtil.readString(src));
        }

        return new CreateConversationCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                conversationId, convType, title, members
        );
    }
}
