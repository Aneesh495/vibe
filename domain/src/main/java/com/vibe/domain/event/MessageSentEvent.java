package com.vibe.domain.event;

import com.vibe.domain.entity.AttachmentInfo;
import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record MessageSentEvent(
        UUID eventId,
        UUID conversationId,
        long seqNumber,
        String senderUserId,
        long timestamp,
        UUID messageId,
        String clientMessageId,
        String content,
        AttachmentInfo attachment
) implements DomainEvent {

    public MessageSentEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(senderUserId, "senderUserId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        clientMessageId = clientMessageId != null ? clientMessageId : "";
        content = content != null ? content : "";
    }

    @Override
    public short eventType() {
        return ConversationEventPayload.EVENT_MESSAGE_SENT;
    }

    @Override
    public ByteBuffer encodePayload() {
        int size = 16 + PayloadCodecUtil.stringByteLength(clientMessageId) + PayloadCodecUtil.stringByteLength(content) + 1;
        if (attachment != null) {
            size += 16 + PayloadCodecUtil.stringByteLength(attachment.fileName())
                    + PayloadCodecUtil.stringByteLength(attachment.mimeType())
                    + 8 + 4 + 1;
        }

        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeUUID(buf, messageId);
        PayloadCodecUtil.writeString(buf, clientMessageId);
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

    public static MessageSentEvent decode(
            UUID eventId,
            UUID conversationId,
            long seqNumber,
            String senderUserId,
            long timestamp,
            ByteBuffer payloadBuf
    ) {
        UUID messageId = PayloadCodecUtil.readUUID(payloadBuf);
        String clientMessageId = PayloadCodecUtil.readString(payloadBuf);
        String content = PayloadCodecUtil.readString(payloadBuf);

        AttachmentInfo attach = null;
        if (payloadBuf.get() == 1) {
            UUID attachId = PayloadCodecUtil.readUUID(payloadBuf);
            String fileName = PayloadCodecUtil.readString(payloadBuf);
            String mimeType = PayloadCodecUtil.readString(payloadBuf);
            long sizeBytes = payloadBuf.getLong();
            int crc = payloadBuf.getInt();
            boolean completed = payloadBuf.get() == 1;
            attach = new AttachmentInfo(attachId, fileName, mimeType, sizeBytes, crc, completed);
        }

        return new MessageSentEvent(
                eventId, conversationId, seqNumber, senderUserId, timestamp,
                messageId, clientMessageId, content, attach
        );
    }
}
