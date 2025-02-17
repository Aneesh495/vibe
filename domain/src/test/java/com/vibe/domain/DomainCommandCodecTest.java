package com.vibe.domain;

import com.vibe.domain.command.*;
import com.vibe.domain.entity.AttachmentInfo;
import com.vibe.domain.entity.ConversationType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainCommandCodecTest {

    @Test
    void testRegisterUserCommandRoundtrip() {
        RegisterUserCommand cmd = new RegisterUserCommand(
                UUID.randomUUID(), 1000L, "u1", "d1", "c1", "userA", "hash123", "User A", "My bio", "avatar.png"
        );
        ByteBuffer encoded = DomainCommandCodec.encode(cmd);
        DomainCommand decoded = DomainCommandCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(RegisterUserCommand.class);
        RegisterUserCommand reg = (RegisterUserCommand) decoded;
        assertThat(reg.commandId()).isEqualTo(cmd.commandId());
        assertThat(reg.username()).isEqualTo(cmd.username());
        assertThat(reg.passwordHash()).isEqualTo(cmd.passwordHash());
    }

    @Test
    void testSendMessageCommandWithAttachmentRoundtrip() {
        AttachmentInfo attach = new AttachmentInfo(UUID.randomUUID(), "photo.jpg", "image/jpeg", 1024L, 999, true);
        SendMessageCommand cmd = new SendMessageCommand(
                UUID.randomUUID(), 2000L, "u1", "d1", "c1", UUID.randomUUID(), UUID.randomUUID(), "Hello world", attach
        );

        ByteBuffer encoded = DomainCommandCodec.encode(cmd);
        DomainCommand decoded = DomainCommandCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(SendMessageCommand.class);
        SendMessageCommand msg = (SendMessageCommand) decoded;
        assertThat(msg.messageId()).isEqualTo(cmd.messageId());
        assertThat(msg.conversationId()).isEqualTo(cmd.conversationId());
        assertThat(msg.content()).isEqualTo("Hello world");
        assertThat(msg.attachment()).isNotNull();
        assertThat(msg.attachment().fileName()).isEqualTo("photo.jpg");
    }

    @Test
    void testCreateConversationCommandRoundtrip() {
        CreateConversationCommand cmd = new CreateConversationCommand(
                UUID.randomUUID(), 3000L, "u1", "d1", "c1", UUID.randomUUID(),
                ConversationType.GROUP, "General Chat", List.of("u1", "u2", "u3")
        );

        ByteBuffer encoded = DomainCommandCodec.encode(cmd);
        DomainCommand decoded = DomainCommandCodec.decode(encoded);

        assertThat(decoded).isInstanceOf(CreateConversationCommand.class);
        CreateConversationCommand conv = (CreateConversationCommand) decoded;
        assertThat(conv.conversationId()).isEqualTo(cmd.conversationId());
        assertThat(conv.title()).isEqualTo("General Chat");
        assertThat(conv.memberUserIds()).containsExactly("u1", "u2", "u3");
    }
}
