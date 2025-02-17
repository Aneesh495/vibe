package com.vibe.delivery;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.event.MessageSentEvent;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.protocol.ErrorCode;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.protocol.payload.ErrorPayload;
import com.vibe.transport.nio.TransportConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryEngineTest {

    private static final AtomicLong CONN_ID_GEN = new AtomicLong(100);

    private DomainStateMachine stateMachine;
    private DeliveryEngine engine;

    @BeforeEach
    void setUp() {
        stateMachine = new DomainStateMachine();
        engine = new DeliveryEngine(stateMachine, 8);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    static class MockTransportConnection implements TransportConnection {
        private final long id = CONN_ID_GEN.incrementAndGet();
        private final BlockingQueue<Frame> receivedFrames = new LinkedBlockingQueue<>();
        private final AtomicBoolean open = new AtomicBoolean(true);
        private String userId;
        private String deviceId;
        private String username;

        @Override
        public long connectionId() {
            return id;
        }

        @Override
        public boolean send(Frame frame) {
            if (!open.get()) {
                return false;
            }
            return receivedFrames.offer(frame);
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public boolean isClosed() {
            return !open.get();
        }

        @Override
        public void close() {
            open.set(false);
        }

        @Override
        public String userId() {
            return userId;
        }

        @Override
        public void setUserId(String userId) {
            this.userId = userId;
        }

        @Override
        public String deviceId() {
            return deviceId;
        }

        @Override
        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        @Override
        public String username() {
            return username;
        }

        @Override
        public void setUsername(String username) {
            this.username = username;
        }

        public Frame pollFrame(long timeout, TimeUnit unit) throws InterruptedException {
            return receivedFrames.poll(timeout, unit);
        }

        public List<Frame> allFrames() {
            return new ArrayList<>(receivedFrames);
        }
    }

    @Test
    void testMonotonicFanoutToConversationMembers() throws Exception {
        // Register Alice and Bob
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "alice-id", "dev-1", "m1", "alice", "hash", "Alice", "", ""));
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "bob-id", "dev-2", "m2", "bob", "hash", "Bob", "", ""));

        // Create direct conversation
        UUID convId = UUID.randomUUID();
        stateMachine.apply(new CreateConversationCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "alice-id", "dev-1", "m3", convId, ConversationType.DIRECT, "Direct",
                List.of("alice-id", "bob-id")));

        // Connect Alice and Bob
        MockTransportConnection aliceConn = new MockTransportConnection();
        UserSession aliceSession = new UserSession("alice-id", "dev-1", aliceConn);
        engine.sessionRegistry().registerSession(aliceSession);

        MockTransportConnection bobConn = new MockTransportConnection();
        UserSession bobSession = new UserSession("bob-id", "dev-2", bobConn);
        engine.sessionRegistry().registerSession(bobSession);

        // Send two messages
        CommandExecutionResult res1 = stateMachine.apply(new SendMessageCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "alice-id", "dev-1", "msg-1", UUID.randomUUID(), convId, "Hello Bob!", null));
        assertThat(res1.isSuccess()).isTrue();
        engine.dispatchDomainEvent(res1.event());

        CommandExecutionResult res2 = stateMachine.apply(new SendMessageCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "bob-id", "dev-2", "msg-2", UUID.randomUUID(), convId, "Hey Alice!", null));
        assertThat(res2.isSuccess()).isTrue();
        engine.dispatchDomainEvent(res2.event());

        // Await delivery to Bob
        Frame bobFrame1 = bobConn.pollFrame(2, TimeUnit.SECONDS);
        assertThat(bobFrame1).isNotNull();
        assertThat(bobFrame1.type()).isEqualTo(FrameType.CONVERSATION_EVENT);
        ConversationEventPayload p1 = ConversationEventPayload.decode(bobFrame1.payload().duplicate());
        assertThat(p1.seqNumber()).isEqualTo(1L);
        assertThat(p1.senderUserId()).isEqualTo("alice-id");

        Frame bobFrame2 = bobConn.pollFrame(2, TimeUnit.SECONDS);
        assertThat(bobFrame2).isNotNull();
        assertThat(bobFrame2.type()).isEqualTo(FrameType.CONVERSATION_EVENT);
        ConversationEventPayload p2 = ConversationEventPayload.decode(bobFrame2.payload().duplicate());
        assertThat(p2.seqNumber()).isEqualTo(2L);
        assertThat(p2.senderUserId()).isEqualTo("bob-id");

        // Verify metrics
        assertThat(engine.metrics().totalDispatched()).isEqualTo(2L);
        assertThat(engine.metrics().totalDelivered()).isGreaterThanOrEqualTo(4L); // 2 frames x 2 users
    }

    @Test
    void testMultiDeviceSynchronization() throws Exception {
        // Register Alice and Bob
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "alice-id", "phone", "m1", "alice", "hash", "Alice", "", ""));
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "bob-id", "phone", "m2", "bob", "hash", "Bob", "", ""));

        UUID convId = UUID.randomUUID();
        stateMachine.apply(new CreateConversationCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "alice-id", "phone", "m3", convId, ConversationType.DIRECT, "Direct",
                List.of("alice-id", "bob-id")));

        // Alice connects from phone AND laptop
        MockTransportConnection alicePhone = new MockTransportConnection();
        UserSession alicePhoneSession = new UserSession("alice-id", "phone", alicePhone);
        engine.sessionRegistry().registerSession(alicePhoneSession);

        MockTransportConnection aliceLaptop = new MockTransportConnection();
        UserSession aliceLaptopSession = new UserSession("alice-id", "laptop", aliceLaptop);
        engine.sessionRegistry().registerSession(aliceLaptopSession);

        // Alice sends message from phone
        CommandExecutionResult res = stateMachine.apply(new SendMessageCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "alice-id", "phone", "phone-msg-1", UUID.randomUUID(), convId, "Sent from phone", null));
        engine.dispatchDomainEvent(res.event());

        // Alice's laptop must receive the synchronized event!
        Frame laptopFrame = aliceLaptop.pollFrame(2, TimeUnit.SECONDS);
        assertThat(laptopFrame).isNotNull();
        ConversationEventPayload laptopPayload = ConversationEventPayload.decode(laptopFrame.payload().duplicate());
        assertThat(laptopPayload.seqNumber()).isEqualTo(1L);
        assertThat(laptopPayload.senderUserId()).isEqualTo("alice-id");
    }

    @Test
    void testSlowConsumerDisconnectionWithCursor() throws Exception {
        MockTransportConnection slowConn = new MockTransportConnection();
        UserSession slowSession = new UserSession("slow-user", "dev-slow", slowConn);

        // Fill queue to capacity
        Frame dummyFrame = Frame.empty(FrameType.CONVERSATION_EVENT, 0L);
        for (int i = 0; i < DeliveryConstants.MAX_PENDING_DELIVERIES_PER_SESSION; i++) {
            slowSession.tryEnqueue(dummyFrame, true, engine.metrics());
        }

        // Now queue is full. One more durable frame triggers slow consumer eviction!
        boolean admitted = slowSession.tryEnqueue(dummyFrame, true, engine.metrics());
        assertThat(admitted).isFalse();
        assertThat(slowConn.isOpen()).isFalse();
        assertThat(engine.metrics().slowConsumersDisconnected()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void testReplayConversationResumption() throws Exception {
        UUID convId = UUID.randomUUID();
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "u1", "d1", "c1", "u1", "h", "U1", "", ""));
        stateMachine.apply(new CreateConversationCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "u1", "d1", "c2", convId, ConversationType.GROUP, "Notes", List.of("u1")));

        for (int i = 1; i <= 5; i++) {
            stateMachine.apply(new SendMessageCommand(UUID.randomUUID(), System.currentTimeMillis(),
                    "u1", "d1", "msg-" + i, UUID.randomUUID(), convId, "Note " + i, null));
        }

        MockTransportConnection conn = new MockTransportConnection();
        UserSession session = new UserSession("u1", "d1", conn);

        // Replay from seq 3
        engine.replayConversation(convId, 3L, 10, session);

        // Should receive notes 3, 4, and 5
        Frame f3 = conn.pollFrame(1, TimeUnit.SECONDS);
        assertThat(f3).isNotNull();
        ConversationEventPayload p3 = ConversationEventPayload.decode(f3.payload().duplicate());
        assertThat(p3.seqNumber()).isEqualTo(3L);

        Frame f4 = conn.pollFrame(1, TimeUnit.SECONDS);
        assertThat(f4).isNotNull();
        ConversationEventPayload p4 = ConversationEventPayload.decode(f4.payload().duplicate());
        assertThat(p4.seqNumber()).isEqualTo(4L);

        Frame f5 = conn.pollFrame(1, TimeUnit.SECONDS);
        assertThat(f5).isNotNull();
        ConversationEventPayload p5 = ConversationEventPayload.decode(f5.payload().duplicate());
        assertThat(p5.seqNumber()).isEqualTo(5L);
    }
}
