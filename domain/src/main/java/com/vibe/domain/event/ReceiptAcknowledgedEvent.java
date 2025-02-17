package com.vibe.domain.event;

import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record ReceiptAcknowledgedEvent(
        UUID eventId,
        UUID conversationId,
        long seqNumber,
        String senderUserId,
        long timestamp,
        byte ackType, // 1: DELIVERED, 2: READ
        long upToSeq
) implements DomainEvent {

    public static final short EVENT_RECEIPT_ACK = 0x000A;

    public ReceiptAcknowledgedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(senderUserId, "senderUserId must not be null");
    }

    @Override
    public short eventType() {
        return EVENT_RECEIPT_ACK;
    }

    @Override
    public ByteBuffer encodePayload() {
        ByteBuffer buf = ByteBuffer.allocate(1 + 8);
        buf.put(ackType);
        buf.putLong(upToSeq);
        buf.flip();
        return buf;
    }

    public static ReceiptAcknowledgedEvent decode(
            UUID eventId,
            UUID conversationId,
            long seqNumber,
            String senderUserId,
            long timestamp,
            ByteBuffer payloadBuf
    ) {
        byte ackType = payloadBuf.get();
        long upToSeq = payloadBuf.getLong();
        return new ReceiptAcknowledgedEvent(eventId, conversationId, seqNumber, senderUserId, timestamp, ackType, upToSeq);
    }
}
