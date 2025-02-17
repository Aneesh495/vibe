package com.vibe.domain.event;

import com.vibe.domain.entity.MemberRole;
import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.protocol.payload.PayloadCodecUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

public record MemberJoinedEvent(
        UUID eventId,
        UUID conversationId,
        long seqNumber,
        String senderUserId,
        long timestamp,
        String joinedUserId,
        MemberRole role
) implements DomainEvent {

    public MemberJoinedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(senderUserId, "senderUserId must not be null");
        Objects.requireNonNull(joinedUserId, "joinedUserId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    @Override
    public short eventType() {
        return ConversationEventPayload.EVENT_MEMBER_JOINED;
    }

    @Override
    public ByteBuffer encodePayload() {
        int size = PayloadCodecUtil.stringByteLength(joinedUserId) + 1;
        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeString(buf, joinedUserId);
        buf.put((byte) role.ordinal());
        buf.flip();
        return buf;
    }

    public static MemberJoinedEvent decode(
            UUID eventId,
            UUID conversationId,
            long seqNumber,
            String senderUserId,
            long timestamp,
            ByteBuffer payloadBuf
    ) {
        String joinedUserId = PayloadCodecUtil.readString(payloadBuf);
        MemberRole role = MemberRole.values()[payloadBuf.get()];
        return new MemberJoinedEvent(eventId, conversationId, seqNumber, senderUserId, timestamp, joinedUserId, role);
    }
}
