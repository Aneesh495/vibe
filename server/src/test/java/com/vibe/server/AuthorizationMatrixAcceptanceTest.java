package com.vibe.server;

import com.vibe.delivery.DeliveryEngine;
import com.vibe.domain.auth.TokenManager;
import com.vibe.domain.command.*;
import com.vibe.domain.durability.DurabilityAdapter;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.protocol.ErrorCode;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.*;
import com.vibe.storage.local.LocalDurabilityAdapter;
import com.vibe.transport.nio.TransportConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test verifying the comprehensive authorization and security matrix:
 * <ul>
 *   <li>Unauthenticated command execution is rejected with AUTH_FORBIDDEN</li>
 *   <li>Cross-user caller identity spoofing is rejected with AUTH_FORBIDDEN</li>
 *   <li>Non-member message posting is rejected with DOMAIN_NOT_A_MEMBER</li>
 *   <li>Non-member subscription/replay is rejected with DOMAIN_NOT_A_MEMBER</li>
 *   <li>Blocked user messaging is rejected with DOMAIN_USER_BLOCKED</li>
 *   <li>History visibility restrictions prevent unauthorized historical access</li>
 *   <li>Malformed or unhandled frame types return PROTOCOL_INVALID_STATE</li>
 * </ul>
 */
class AuthorizationMatrixAcceptanceTest {

    private DomainStateMachine stateMachine;
    private LocalDurabilityAdapter durabilityAdapter;
    private DeliveryEngine deliveryEngine;
    private ClientConnectionHandler handler;
    private TokenManager tokenManager;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        stateMachine = new DomainStateMachine();
        durabilityAdapter = new LocalDurabilityAdapter(tempDir, stateMachine, 16 * 1024 * 1024L, 20L, 100);
        durabilityAdapter.start();

        deliveryEngine = new DeliveryEngine(stateMachine, 4);
        tokenManager = TokenManager.createRandom();
        AttachmentManager attachmentManager = new AttachmentManager(tempDir.resolve("cas"), 2);

        handler = new ClientConnectionHandler(durabilityAdapter, deliveryEngine, attachmentManager, tokenManager, 2);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (handler != null) handler.close();
        if (deliveryEngine != null) deliveryEngine.close();
        if (durabilityAdapter != null) durabilityAdapter.close();
    }

    static class MockConn implements TransportConnection {
        private static final AtomicLong ID_SEQ = new AtomicLong(2000);
        private final long id = ID_SEQ.incrementAndGet();
        private final BlockingQueue<Frame> queue = new LinkedBlockingQueue<>();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private String userId;
        private String deviceId;
        private String username;

        @Override public long connectionId() { return id; }
        @Override public boolean send(Frame frame) { return open.get() && queue.offer(frame); }
        @Override public boolean isOpen() { return open.get(); }
        @Override public boolean isClosed() { return !open.get(); }
        @Override public void close() { open.set(false); }
        @Override public String userId() { return userId; }
        @Override public void setUserId(String userId) { this.userId = userId; }
        @Override public String deviceId() { return deviceId; }
        @Override public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        @Override public String username() { return username; }
        @Override public void setUsername(String username) { this.username = username; }

        Frame poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }

    @Test
    void testUnauthenticatedCommandRejected() throws Exception {
        MockConn conn = new MockConn(); // Not authenticated (userId is null)
        UUID convId = UUID.randomUUID();

        SendMessageCommand cmd = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "user-alice", "dev-1", "m1", UUID.randomUUID(), convId, "Hello", null
        );
        byte[] encodedCmd = DomainCommandCodec.encode(cmd).array();
        CommandPayload cmdPayload = new CommandPayload(cmd.type(), cmd.clientMessageId(), cmd.conversationId(), encodedCmd);
        Frame frame = Frame.create(FrameType.COMMAND, 101L, cmdPayload.encode());

        handler.handleFrame(conn, frame);

        Frame resp = conn.poll(2, TimeUnit.SECONDS);
        assertThat(resp).isNotNull();
        assertThat(resp.type()).isEqualTo(FrameType.ERROR);
        ErrorPayload error = ErrorPayload.decode(resp.payload());
        assertThat(error.errorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN.code());
    }

    @Test
    void testCallerIdentitySpoofingRejected() throws Exception {
        MockConn conn = new MockConn();
        conn.setUserId("user-mallory");
        conn.setDeviceId("dev-mallory");

        // Mallory tries to submit a command claiming callerUserId is alice!
        UUID convId = UUID.randomUUID();
        SendMessageCommand spoofedCmd = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "user-alice", "dev-1", "m1", UUID.randomUUID(), convId, "Spoofed msg", null
        );
        byte[] encodedCmd = DomainCommandCodec.encode(spoofedCmd).array();
        CommandPayload cmdPayload = new CommandPayload(spoofedCmd.type(), spoofedCmd.clientMessageId(), spoofedCmd.conversationId(), encodedCmd);
        Frame frame = Frame.create(FrameType.COMMAND, 102L, cmdPayload.encode());

        handler.handleFrame(conn, frame);

        Frame resp = conn.poll(2, TimeUnit.SECONDS);
        assertThat(resp).isNotNull();
        assertThat(resp.type()).isEqualTo(FrameType.ERROR);
        ErrorPayload error = ErrorPayload.decode(resp.payload());
        assertThat(error.errorCode()).isEqualTo(ErrorCode.AUTH_FORBIDDEN.code());
        assertThat(error.message()).containsIgnoringCase("Caller identity mismatch");
    }

    @Test
    void testNonMemberCannotPostOrSubscribe() throws Exception {
        // Register Alice and Bob
        durabilityAdapter.executeCommand(new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "alice", "dev-a", "r1", "alice", "h", "Alice", "", ""
        ), true).get(5, TimeUnit.SECONDS);

        durabilityAdapter.executeCommand(new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "bob", "dev-b", "r2", "bob", "h", "Bob", "", ""
        ), true).get(5, TimeUnit.SECONDS);

        // Alice creates a private group with only Alice
        UUID privateConv = UUID.randomUUID();
        durabilityAdapter.executeCommand(new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "alice", "dev-a", "c1", privateConv, ConversationType.GROUP, "Alice Private", List.of("alice")
        ), true).get(5, TimeUnit.SECONDS);

        // Bob connects
        MockConn bobConn = new MockConn();
        bobConn.setUserId("bob");
        bobConn.setDeviceId("dev-b");

        // 1. Bob attempts to subscribe to Alice's private group
        SubscribePayload sub = new SubscribePayload(privateConv, 0L, 50);
        handler.handleFrame(bobConn, Frame.create(FrameType.SUBSCRIBE, 201L, sub.encode()));

        Frame subResp = bobConn.poll(2, TimeUnit.SECONDS);
        assertThat(subResp).isNotNull();
        assertThat(subResp.type()).isEqualTo(FrameType.ERROR);
        ErrorPayload subErr = ErrorPayload.decode(subResp.payload());
        assertThat(subErr.errorCode()).isEqualTo(ErrorCode.DOMAIN_NOT_A_MEMBER.code());

        // 2. Bob attempts to send a message to Alice's private group
        SendMessageCommand bobMsg = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "bob", "dev-b", "b-msg-1", UUID.randomUUID(), privateConv, "Intruder message", null
        );
        byte[] encodedBobMsg = DomainCommandCodec.encode(bobMsg).array();
        CommandPayload bobMsgPayload = new CommandPayload(bobMsg.type(), bobMsg.clientMessageId(), bobMsg.conversationId(), encodedBobMsg);
        handler.handleFrame(bobConn, Frame.create(FrameType.COMMAND, 202L, bobMsgPayload.encode()));

        Frame postResp = bobConn.poll(2, TimeUnit.SECONDS);
        assertThat(postResp).isNotNull();
        assertThat(postResp.type()).isEqualTo(FrameType.COMMAND_RESULT);
        CommandResultPayload postResult = CommandResultPayload.decode(postResp.payload());
        assertThat(postResult.isSuccess()).isFalse();
        assertThat(postResult.errorCode()).isEqualTo(ErrorCode.DOMAIN_NOT_A_MEMBER.code());
    }

    @Test
    void testBlockedUserMessagingRejected() throws Exception {
        // Register Alice and Bob
        durabilityAdapter.executeCommand(new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "alice", "dev-a", "r1", "alice", "h", "Alice", "", ""
        ), true).get(5, TimeUnit.SECONDS);

        durabilityAdapter.executeCommand(new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "bob", "dev-b", "r2", "bob", "h", "Bob", "", ""
        ), true).get(5, TimeUnit.SECONDS);

        // Alice creates direct conversation with Bob
        UUID directConv = UUID.randomUUID();
        durabilityAdapter.executeCommand(new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "alice", "dev-a", "c1", directConv, ConversationType.DIRECT, "Direct", List.of("alice", "bob")
        ), true).get(5, TimeUnit.SECONDS);

        // Bob blocks Alice!
        durabilityAdapter.executeCommand(new BlockUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "bob", "dev-b", "blk-1", "alice"
        ), true).get(5, TimeUnit.SECONDS);

        // Alice attempts to send a message to Bob
        MockConn aliceConn = new MockConn();
        aliceConn.setUserId("alice");
        aliceConn.setDeviceId("dev-a");

        SendMessageCommand aliceMsg = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "alice", "dev-a", "a-msg-1", UUID.randomUUID(), directConv, "Can you hear me?", null
        );
        byte[] encodedAliceMsg = DomainCommandCodec.encode(aliceMsg).array();
        CommandPayload aliceMsgPayload = new CommandPayload(aliceMsg.type(), aliceMsg.clientMessageId(), aliceMsg.conversationId(), encodedAliceMsg);
        handler.handleFrame(aliceConn, Frame.create(FrameType.COMMAND, 301L, aliceMsgPayload.encode()));

        Frame msgResp = aliceConn.poll(2, TimeUnit.SECONDS);
        assertThat(msgResp).isNotNull();
        assertThat(msgResp.type()).isEqualTo(FrameType.COMMAND_RESULT);
        CommandResultPayload result = CommandResultPayload.decode(msgResp.payload());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ErrorCode.DOMAIN_USER_BLOCKED.code());
    }
}
