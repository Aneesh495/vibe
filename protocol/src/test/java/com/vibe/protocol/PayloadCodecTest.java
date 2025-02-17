package com.vibe.protocol;

import com.vibe.protocol.payload.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadCodecTest {

    @Test
    void testHandshakeRoundtrip() {
        HandshakePayload original = new HandshakePayload(1, "desktop-client", "device-uuid-1234", 0x07);
        ByteBuffer encoded = original.encode();
        HandshakePayload decoded = HandshakePayload.decode(encoded);

        assertThat(decoded.clientVersion()).isEqualTo(original.clientVersion());
        assertThat(decoded.clientId()).isEqualTo(original.clientId());
        assertThat(decoded.deviceId()).isEqualTo(original.deviceId());
        assertThat(decoded.capabilities()).isEqualTo(original.capabilities());
    }

    @Test
    void testHandshakeAckRoundtrip() {
        byte[] salt = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        HandshakeAckPayload original = new HandshakeAckPayload(1, 15000, 16 * 1024 * 1024, 1720000000000L, salt);
        ByteBuffer encoded = original.encode();
        HandshakeAckPayload decoded = HandshakeAckPayload.decode(encoded);

        assertThat(decoded.serverVersion()).isEqualTo(original.serverVersion());
        assertThat(decoded.heartbeatIntervalMs()).isEqualTo(original.heartbeatIntervalMs());
        assertThat(decoded.maxFrameSize()).isEqualTo(original.maxFrameSize());
        assertThat(decoded.serverEpochTimestamp()).isEqualTo(original.serverEpochTimestamp());
        assertThat(decoded.sessionSalt()).isEqualTo(original.sessionSalt());
    }

    @Test
    void testAuthenticateRoundtrip() {
        AuthenticatePayload original = AuthenticatePayload.withPassword("alice", "secretPass!", "device-1");
        ByteBuffer encoded = original.encode();
        AuthenticatePayload decoded = AuthenticatePayload.decode(encoded);

        assertThat(decoded.authMethod()).isEqualTo(AuthenticatePayload.METHOD_PASSWORD);
        assertThat(decoded.username()).isEqualTo("alice");
        assertThat(decoded.credential()).isEqualTo("secretPass!");
        assertThat(decoded.deviceId()).isEqualTo("device-1");
    }

    @Test
    void testAuthResultRoundtrip() {
        AuthResultPayload original = AuthResultPayload.success("user-42", "jwt.token.string", 1800000000000L);
        ByteBuffer encoded = original.encode();
        AuthResultPayload decoded = AuthResultPayload.decode(encoded);

        assertThat(decoded.isSuccess()).isTrue();
        assertThat(decoded.userId()).isEqualTo("user-42");
        assertThat(decoded.token()).isEqualTo("jwt.token.string");
        assertThat(decoded.tokenExpiresAt()).isEqualTo(1800000000000L);
    }

    @Test
    void testCommandRoundtrip() {
        UUID convId = UUID.randomUUID();
        byte[] body = "Hello world!".getBytes(StandardCharsets.UTF_8);
        CommandPayload original = new CommandPayload(CommandType.SEND_MESSAGE, "msg-client-id-99", convId, body);
        ByteBuffer encoded = original.encode();
        CommandPayload decoded = CommandPayload.decode(encoded);

        assertThat(decoded.commandType()).isEqualTo(CommandType.SEND_MESSAGE);
        assertThat(decoded.clientMessageId()).isEqualTo("msg-client-id-99");
        assertThat(decoded.conversationId()).isEqualTo(convId);
        assertThat(decoded.body()).isEqualTo(body);
    }

    @Test
    void testCommandResultRoundtrip() {
        byte[] payload = "Committed data".getBytes(StandardCharsets.UTF_8);
        CommandResultPayload original = CommandResultPayload.success(42L, 1720000000000L, payload);
        ByteBuffer encoded = original.encode();
        CommandResultPayload decoded = CommandResultPayload.decode(encoded);

        assertThat(decoded.isSuccess()).isTrue();
        assertThat(decoded.assignedSeq()).isEqualTo(42L);
        assertThat(decoded.committedTimestamp()).isEqualTo(1720000000000L);
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    void testConversationEventRoundtrip() {
        UUID convId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        byte[] payload = "Message body".getBytes(StandardCharsets.UTF_8);
        ConversationEventPayload original = new ConversationEventPayload(
                convId, 100L, eventId, ConversationEventPayload.EVENT_MESSAGE_SENT, "alice", 1720000000000L, payload
        );
        ByteBuffer encoded = original.encode();
        ConversationEventPayload decoded = ConversationEventPayload.decode(encoded);

        assertThat(decoded.conversationId()).isEqualTo(convId);
        assertThat(decoded.seqNumber()).isEqualTo(100L);
        assertThat(decoded.eventId()).isEqualTo(eventId);
        assertThat(decoded.eventType()).isEqualTo(ConversationEventPayload.EVENT_MESSAGE_SENT);
        assertThat(decoded.senderUserId()).isEqualTo("alice");
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    void testAttachmentChunkRoundtrip() {
        UUID attachId = UUID.randomUUID();
        byte[] chunk = new byte[]{10, 20, 30, 40, 50};
        AttachmentChunkPayload original = AttachmentChunkPayload.create(attachId, 0, 5, chunk);
        ByteBuffer encoded = original.encode();
        AttachmentChunkPayload decoded = AttachmentChunkPayload.decode(encoded);

        assertThat(decoded.attachmentId()).isEqualTo(attachId);
        assertThat(decoded.chunkIndex()).isEqualTo(0);
        assertThat(decoded.totalChunks()).isEqualTo(5);
        assertThat(decoded.verifyChecksum()).isTrue();
        assertThat(decoded.chunkData()).isEqualTo(chunk);
    }

    @Test
    void testErrorPayloadRoundtrip() {
        ErrorPayload original = ErrorPayload.fromErrorCode(ErrorCode.DOMAIN_USER_BLOCKED, "Cannot contact user");
        ByteBuffer encoded = original.encode();
        ErrorPayload decoded = ErrorPayload.decode(encoded);

        assertThat(decoded.errorCode()).isEqualTo(ErrorCode.DOMAIN_USER_BLOCKED.code());
        assertThat(decoded.resolveErrorCode()).isEqualTo(ErrorCode.DOMAIN_USER_BLOCKED);
        assertThat(decoded.message()).isEqualTo("Cannot contact user");
    }
}
