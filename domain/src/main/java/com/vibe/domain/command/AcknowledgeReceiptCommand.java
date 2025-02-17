package com.vibe.domain.command;

import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record AcknowledgeReceiptCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        UUID conversationId,
        byte ackType, // 1: DELIVERED, 2: READ
        long upToSeq
) implements DomainCommand {

    public AcknowledgeReceiptCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
    }

    @Override
    public CommandType type() {
        return CommandType.fromCode((short) 0x0036); // ack command code or internal
    }

    @Override
    public int contentHash() {
        return Objects.hash(conversationId, ackType, upToSeq);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
                + 16 + 1 + 8;
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort((short) 0x0036);
        PayloadCodecUtil.writeUUID(buf, commandId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeString(buf, callerUserId);
        PayloadCodecUtil.writeString(buf, callerDeviceId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        buf.put(ackType);
        buf.putLong(upToSeq);
        buf.flip();
        return buf;
    }

    public static AcknowledgeReceiptCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        byte ackType = src.get();
        long upToSeq = src.getLong();
        return new AcknowledgeReceiptCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                conversationId, ackType, upToSeq
        );
    }
}
