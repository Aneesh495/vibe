package com.vibe.domain.command;

import com.vibe.domain.entity.AttachmentInfo;
import com.vibe.protocol.payload.CommandType;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record SendMessageCommand(
        UUID commandId,
        long timestamp,
        String callerUserId,
        String callerDeviceId,
        String clientMessageId,
        UUID messageId,
        UUID conversationId,
        String content,
        AttachmentInfo attachment
) implements DomainCommand {

    public SendMessageCommand {
        Objects.requireNonNull(commandId, "commandId must not be null");
        Objects.requireNonNull(callerUserId, "callerUserId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        callerDeviceId = callerDeviceId != null ? callerDeviceId : "";
        clientMessageId = clientMessageId != null ? clientMessageId : "";
        content = content != null ? content : "";
    }

    @Override
    public CommandType type() {
        return CommandType.SEND_MESSAGE;
    }

    @Override
    public int contentHash() {
        return Objects.hash(conversationId, content, attachment != null ? attachment.attachmentId() : null);
    }

    @Override
    public ByteBuffer encode() {
        int size = 2 + 16 + 8
                + PayloadCodecUtil.stringByteLength(callerUserId)
                + PayloadCodecUtil.stringByteLength(callerDeviceId)
                + PayloadCodecUtil.stringByteLength(clientMessageId)
                + 16 + 16
                + PayloadCodecUtil.stringByteLength(content)
                + 1; // has attachment flag

        if (attachment != null) {
            size += 16
                    + PayloadCodecUtil.stringByteLength(attachment.fileName())
                    + PayloadCodecUtil.stringByteLength(attachment.mimeType())
                    + 8 + 4 + 1;
        }

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort(type().code());
        PayloadCodecUtil.writeUUID(buf, commandId);
        buf.putLong(timestamp);
        PayloadCodecUtil.writeString(buf, callerUserId);
        PayloadCodecUtil.writeString(buf, callerDeviceId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
        PayloadCodecUtil.writeUUID(buf, messageId);
        PayloadCodecUtil.writeUUID(buf, conversationId);
        PayloadCodecUtil.writeString(buf, content);

        if (attachment != null) {
            buf.put((byte) 1);
            PayloadCodecUtil.writeUUID(buf, attachment.attachmentId());
            PayloadCodecUtil.writeString(buf, attachment.fileName());
            PayloadCodecUtil.writeString(buf, attachment.mimeType());
            buf.putLong(attachment.sizeBytes());
            buf.putInt(attachment.checksumCRC32C());
            buf.put((byte) (attachment.completed() ? 1 : 0));
        } else {
            buf.put((byte) 0);
        }

        buf.flip();
        return buf;
    }

    public static SendMessageCommand decode(ByteBuffer src) {
        UUID commandId = PayloadCodecUtil.readUUID(src);
        long timestamp = src.getLong();
        String callerUserId = PayloadCodecUtil.readString(src);
        String callerDeviceId = PayloadCodecUtil.readString(src);
        String clientMessageId = PayloadCodecUtil.readString(src);
        UUID messageId = PayloadCodecUtil.readUUID(src);
        UUID conversationId = PayloadCodecUtil.readUUID(src);
        String content = PayloadCodecUtil.readString(src);

        AttachmentInfo attach = null;
        if (src.get() == 1) {
            UUID attachId = PayloadCodecUtil.readUUID(src);
            String fileName = PayloadCodecUtil.readString(src);
            String mimeType = PayloadCodecUtil.readString(src);
            long sizeBytes = src.getLong();
            int crc = src.getInt();
            boolean completed = src.get() == 1;
            attach = new AttachmentInfo(attachId, fileName, mimeType, sizeBytes, crc, completed);
        }

        return new SendMessageCommand(
                commandId, timestamp, callerUserId, callerDeviceId, clientMessageId,
                messageId, conversationId, content, attach
        );
    }
}
