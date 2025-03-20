package com.vibe.delivery;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for bounded concurrency, backpressure, slow consumer eviction,
 * transient signal shedding, and resumable cursor recovery.
 */
class BackpressureAndSlowConsumerAcceptanceTest {

    private DomainStateMachine stateMachine;
    private DeliveryEngine engine;

    @BeforeEach
    void setUp() {
        stateMachine = new DomainStateMachine();
        engine = new DeliveryEngine(stateMachine, 16);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    static class InstrumentedConnection implements TransportConnection {
        private static final AtomicLong ID_GEN = new AtomicLong(1000);
        private final long id = ID_GEN.incrementAndGet();
        private final BlockingQueue<Frame> queue;
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicInteger sendCallCount = new AtomicInteger(0);
        private volatile boolean artificiallySlow = false;
        private volatile long artificialDelayMs = 0;

        InstrumentedConnection(int capacity) {
            this.queue = new LinkedBlockingQueue<>(capacity);
        }

        void setArtificiallySlow(long delayMs) {
            this.artificiallySlow = true;
            this.artificialDelayMs = delayMs;
        }

        @Override
        public long connectionId() {
            return id;
        }

        @Override
        public boolean send(Frame frame) {
            if (!open.get()) {
                return false;
            }
            sendCallCount.incrementAndGet();
            if (artificiallySlow && artificialDelayMs > 0) {
                try {
                    Thread.sleep(artificialDelayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            return queue.offer(frame);
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
        public String userId() { return "test-user"; }
        @Override
        public void setUserId(String userId) {}
        @Override
        public String deviceId() { return "test-dev"; }
        @Override
        public void setDeviceId(String deviceId) {}
        @Override
        public String username() { return "test"; }
        @Override
        public void setUsername(String username) {}

        Frame poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        int size() {
            return queue.size();
        }
    }

    @Test
    void testTransientSignalSheddingUnderLoad() {
        InstrumentedConnection conn = new InstrumentedConnection(5000);
        UserSession session = new UserSession("user-fast", "dev-1", conn);
        engine.sessionRegistry().registerSession(session);

        // Fill up to max pending capacity with dummy frames
        Frame dummyDurable = Frame.empty(FrameType.CONVERSATION_EVENT, 0L);
        for (int i = 0; i < DeliveryConstants.MAX_PENDING_DELIVERIES_PER_SESSION; i++) {
            boolean admitted = session.tryEnqueue(dummyDurable, true, engine.metrics());
            assertThat(admitted).isTrue();
        }

        assertThat(session.pendingFrames()).isEqualTo(DeliveryConstants.MAX_PENDING_DELIVERIES_PER_SESSION);

        // Now attempt to enqueue non-durable transient signal (typing indicator / heartbeat / presence)
        Frame typingSignal = Frame.empty(FrameType.HEARTBEAT, 0L);
        boolean signalAdmitted = session.tryEnqueue(typingSignal, false, engine.metrics());

        // Must be dropped without disconnecting the client!
        assertThat(signalAdmitted).isFalse();
        assertThat(conn.isOpen()).isTrue();
        assertThat(engine.metrics().droppedSignals()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void testSlowConsumerEvictionAndCleanCursorResume() throws Exception {
        UUID convId = UUID.randomUUID();
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "author", "dev-author", "c1", "author", "h", "Author", "", ""));
        stateMachine.apply(new RegisterUserCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "slowpoke", "dev-slow", "c2", "slowpoke", "h", "Slowpoke", "", ""));
        stateMachine.apply(new CreateConversationCommand(UUID.randomUUID(), System.currentTimeMillis(),
                "author", "dev-author", "c3", convId, ConversationType.DIRECT, "Channel",
                List.of("author", "slowpoke")));

        InstrumentedConnection slowConn = new InstrumentedConnection(10000);
        UserSession slowSession = new UserSession("slowpoke", "dev-slow", slowConn);
        engine.sessionRegistry().registerSession(slowSession);

        // Fill pending queue to capacity
        Frame dummyFrame = Frame.empty(FrameType.CONVERSATION_EVENT, 0L);
        for (int i = 0; i < DeliveryConstants.MAX_PENDING_DELIVERIES_PER_SESSION; i++) {
            slowSession.tryEnqueue(dummyFrame, true, engine.metrics());
        }

        // Author sends a message while slowpoke's queue is saturated
        CommandExecutionResult res = stateMachine.apply(new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "author", "dev-author", "m1", UUID.randomUUID(), convId,
                "Important message during saturation", null
        ));
        assertThat(res.isSuccess()).isTrue();

        // Dispatch domain event - slow consumer must be terminated cleanly
        engine.dispatchDomainEvent(res.event());

        // Give worker thread a moment to process the lane
        Thread.sleep(150);

        // Verify slow consumer was disconnected
        assertThat(slowConn.isOpen()).isFalse();
        assertThat(engine.metrics().slowConsumersDisconnected()).isGreaterThanOrEqualTo(1L);

        // Slow consumer reconnects with a fresh socket connection and replays from cursor
        InstrumentedConnection freshConn = new InstrumentedConnection(1000);
        UserSession reconnectedSession = new UserSession("slowpoke", "dev-slow", freshConn);
        engine.sessionRegistry().registerSession(reconnectedSession);

        // Replay from cursor 0 (or last acknowledged)
        engine.replayConversation(convId, 1L, 50, reconnectedSession);

        Frame replayed = freshConn.poll(2, TimeUnit.SECONDS);
        assertThat(replayed).isNotNull();
        assertThat(replayed.type()).isEqualTo(FrameType.CONVERSATION_EVENT);
        ConversationEventPayload payload = ConversationEventPayload.decode(replayed.payload().duplicate());
        assertThat(payload.seqNumber()).isEqualTo(1L);
        com.vibe.domain.event.MessageSentEvent decoded = com.vibe.domain.event.MessageSentEvent.decode(
                payload.eventId(), payload.conversationId(), payload.seqNumber(),
                payload.senderUserId(), payload.timestamp(), java.nio.ByteBuffer.wrap(payload.payload())
        );
        assertThat(decoded.content()).isEqualTo("Important message during saturation");
    }
}
