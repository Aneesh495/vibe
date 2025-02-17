package com.vibe.domain;

import com.vibe.domain.auth.PasswordHasher;
import com.vibe.domain.auth.TokenManager;
import com.vibe.domain.command.*;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.entity.User;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.protocol.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainStateMachineTest {

    private DomainStateMachine stateMachine;
    private PasswordHasher passwordHasher;
    private TokenManager tokenManager;

    @BeforeEach
    void setUp() {
        stateMachine = new DomainStateMachine();
        passwordHasher = PasswordHasher.fastForTests();
        tokenManager = TokenManager.createRandom();
    }

    @Test
    void testUserRegistrationAndDuplicateRejection() {
        String passHash = passwordHasher.hash("pass123");
        RegisterUserCommand cmd1 = new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-1", "device-1", "c1",
                "Alice", passHash, "Alice Walker", "Engineer", "alice.png"
        );

        CommandExecutionResult res1 = stateMachine.apply(cmd1);
        assertThat(res1.isSuccess()).isTrue();

        User user = stateMachine.getUserById("user-1");
        assertThat(user).isNotNull();
        assertThat(user.username()).isEqualTo("Alice");
        assertThat(passwordHasher.verify("pass123", user.passwordHash())).isTrue();

        // Duplicate registration with same username (case-insensitive)
        RegisterUserCommand cmd2 = new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-2", "device-2", "c2",
                "alice", passHash, "Alice 2", "", ""
        );
        CommandExecutionResult res2 = stateMachine.apply(cmd2);
        assertThat(res2.isSuccess()).isFalse();
        assertThat(res2.errorCode()).isEqualTo(ErrorCode.DOMAIN_USER_ALREADY_EXISTS);
    }

    @Test
    void testTokenManagerLifecycle() {
        String token = tokenManager.generateToken("user-1", "device-1", 10_000L);
        TokenManager.TokenValidationResult val = tokenManager.validateToken(token);

        assertThat(val.isValid()).isTrue();
        assertThat(val.userId()).isEqualTo("user-1");
        assertThat(val.deviceId()).isEqualTo("device-1");

        // Expired token
        String expiredToken = tokenManager.generateToken("user-1", "device-1", -1000L);
        TokenManager.TokenValidationResult expVal = tokenManager.validateToken(expiredToken);
        assertThat(expVal.isValid()).isFalse();
        assertThat(expVal.isExpired()).isTrue();
    }

    @Test
    void testMonotonicSequenceAndIdempotency() {
        registerTestUser("alice", "user-alice");
        registerTestUser("bob", "user-bob");

        UUID convId = UUID.randomUUID();
        CreateConversationCommand createCmd = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-alice", "dev-1", "cmd-create",
                convId, ConversationType.DIRECT, "Direct", List.of("user-alice", "user-bob")
        );
        CommandExecutionResult createRes = stateMachine.apply(createCmd);
        assertThat(createRes.isSuccess()).isTrue();

        // Send Message 1
        UUID msgId1 = UUID.randomUUID();
        SendMessageCommand send1 = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-alice", "dev-1", "msg-client-1",
                msgId1, convId, "First Message", null
        );
        CommandExecutionResult res1 = stateMachine.apply(send1);
        assertThat(res1.isSuccess()).isTrue();
        assertThat(res1.assignedSeq()).isEqualTo(1L);

        // Send Message 2
        UUID msgId2 = UUID.randomUUID();
        SendMessageCommand send2 = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-bob", "dev-2", "msg-client-2",
                msgId2, convId, "Second Message", null
        );
        CommandExecutionResult res2 = stateMachine.apply(send2);
        assertThat(res2.isSuccess()).isTrue();
        assertThat(res2.assignedSeq()).isEqualTo(2L);

        // Idempotent retry of Message 1 (same clientMessageId and same content)
        CommandExecutionResult res1Retry = stateMachine.apply(send1);
        assertThat(res1Retry.isSuccess()).isTrue();
        assertThat(res1Retry.assignedSeq()).isEqualTo(1L); // returns original sequence number!

        // Idempotency conflict: same clientMessageId with different content
        SendMessageCommand send1Conflict = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-alice", "dev-1", "msg-client-1",
                UUID.randomUUID(), convId, "Tampered Message Content", null
        );
        CommandExecutionResult resConflict = stateMachine.apply(send1Conflict);
        assertThat(resConflict.isSuccess()).isFalse();
        assertThat(resConflict.errorCode()).isEqualTo(ErrorCode.DOMAIN_IDEMPOTENCY_CONFLICT);
    }

    @Test
    void testBlockEnforcementPreventsDirectMessaging() {
        registerTestUser("carol", "user-carol");
        registerTestUser("dave", "user-dave");

        // Carol blocks Dave
        BlockUserCommand blockCmd = new BlockUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-carol", "dev-1", "blk-1", "user-dave"
        );
        stateMachine.apply(blockCmd);

        // Attempting to create direct conversation with blocked user fails
        UUID convId = UUID.randomUUID();
        CreateConversationCommand createCmd = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-dave", "dev-2", "create-blocked",
                convId, ConversationType.DIRECT, "Direct", List.of("user-dave", "user-carol")
        );
        CommandExecutionResult res = stateMachine.apply(createCmd);
        assertThat(res.isSuccess()).isFalse();
        assertThat(res.errorCode()).isEqualTo(ErrorCode.DOMAIN_USER_BLOCKED);
    }

    private void registerTestUser(String username, String userId) {
        stateMachine.apply(new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(), userId, "dev", "reg-" + userId,
                username, passwordHasher.hash("pass"), username, "", ""
        ));
    }
}
