package com.vibe.delivery;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.transport.nio.TransportConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test verifying scale behavior with 1,000 concurrent client sessions,
 * lane fanout, parallel message delivery, and reconnect churn.
 */
class ConcurrentClientsAcceptanceTest {

    private static final int CLIENT_COUNT = 1000;
    private DomainStateMachine stateMachine;
    private DeliveryEngine engine;

    @BeforeEach
    void setUp() {
        stateMachine = new DomainStateMachine();
        engine = new DeliveryEngine(stateMachine, 32);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    static class ScaledMockConnection implements TransportConnection {
        private static final AtomicLong ID_SEQ = new AtomicLong(10_000);
        private final long id = ID_SEQ.incrementAndGet();
        private final BlockingQueue<Frame> received = new LinkedBlockingQueue<>(500);
        private final AtomicBoolean open = new AtomicBoolean(true);
        private final String userId;
        private final String deviceId;

        ScaledMockConnection(String userId, String deviceId) {
            this.userId = userId;
            this.deviceId = deviceId;
        }

        @Override public long connectionId() { return id; }
        @Override public boolean send(Frame frame) {
            if (!open.get()) return false;
            return received.offer(frame);
        }
        @Override public boolean isOpen() { return open.get(); }
        @Override public boolean isClosed() { return !open.get(); }
        @Override public void close() { open.set(false); }
        @Override public String userId() { return userId; }
        @Override public void setUserId(String userId) {}
        @Override public String deviceId() { return deviceId; }
        @Override public void setDeviceId(String deviceId) {}
        @Override public String username() { return userId; }
        @Override public void setUsername(String username) {}

        Frame poll(long timeout, TimeUnit unit) throws InterruptedException {
            return received.poll(timeout, unit);
        }
    }

    @Test
    void testOneThousandConcurrentSessionsAndFanout() throws Exception {
        List<String> userIds = new ArrayList<>(CLIENT_COUNT);
        List<ScaledMockConnection> connections = new ArrayList<>(CLIENT_COUNT);
        List<UserSession> sessions = new ArrayList<>(CLIENT_COUNT);

        // 1. Register 1,000 users and establish active sessions
        for (int i = 0; i < CLIENT_COUNT; i++) {
            String uid = "user-" + i;
            String dev = "dev-" + i;
            userIds.add(uid);

            stateMachine.apply(new RegisterUserCommand(
                    UUID.randomUUID(), System.currentTimeMillis(),
                    uid, dev, "cmd-" + i, uid, "hash", "User " + i, "", ""
            ));

            ScaledMockConnection conn = new ScaledMockConnection(uid, dev);
            connections.add(conn);

            UserSession session = new UserSession(uid, dev, conn);
            sessions.add(session);
            engine.sessionRegistry().registerSession(session);
        }

        assertThat(engine.sessionRegistry().activeSessionCount()).isEqualTo(CLIENT_COUNT);

        // 2. Create 50 broadcast channels each containing 20 members
        int groupsCount = 50;
        int membersPerGroup = 20;
        List<UUID> groupIds = new ArrayList<>(groupsCount);

        for (int g = 0; g < groupsCount; g++) {
            UUID cid = UUID.randomUUID();
            groupIds.add(cid);

            List<String> members = new ArrayList<>(membersPerGroup);
            for (int m = 0; m < membersPerGroup; m++) {
                members.add(userIds.get(g * membersPerGroup + m));
            }

            stateMachine.apply(new CreateConversationCommand(
                    UUID.randomUUID(), System.currentTimeMillis(),
                    members.get(0), "dev-0", "create-" + g, cid,
                    ConversationType.GROUP, "Scale Channel " + g, members
            ));
        }

        // 3. Dispatch messages concurrently across all groups
        ExecutorService senderPool = Executors.newFixedThreadPool(16);
        List<Future<?>> sendFutures = new ArrayList<>();

        for (int g = 0; g < groupsCount; g++) {
            final int groupIdx = g;
            final UUID convId = groupIds.get(groupIdx);
            final String senderId = userIds.get(groupIdx * membersPerGroup);

            sendFutures.add(senderPool.submit(() -> {
                for (int msgIdx = 0; msgIdx < 5; msgIdx++) {
                    CommandExecutionResult res = stateMachine.apply(new SendMessageCommand(
                            UUID.randomUUID(), System.currentTimeMillis(),
                            senderId, "dev-0", "m-" + groupIdx + "-" + msgIdx,
                            UUID.randomUUID(), convId,
                            "Scale message payload " + msgIdx, null
                    ));
                    if (res.isSuccess()) {
                        engine.dispatchDomainEvent(res.event());
                    }
                }
            }));
        }

        for (Future<?> f : sendFutures) {
            f.get(15, TimeUnit.SECONDS);
        }
        senderPool.shutdown();

        // 4. Verify that each member received all 5 messages
        for (int g = 0; g < groupsCount; g++) {
            for (int m = 0; m < membersPerGroup; m++) {
                int clientIdx = g * membersPerGroup + m;
                ScaledMockConnection conn = connections.get(clientIdx);

                for (int k = 0; k < 5; k++) {
                    Frame frame = conn.poll(5, TimeUnit.SECONDS);
                    assertThat(frame)
                            .as("Client %d group %d message %d must be received", clientIdx, g, k)
                            .isNotNull();
                    assertThat(frame.type()).isEqualTo(FrameType.CONVERSATION_EVENT);
                }
            }
        }

        // 5. Simulate churn: Disconnect 100 clients, send more messages, then reconnect and replay
        int churnStart = 0;
        int churnCount = 100;
        for (int i = churnStart; i < churnStart + churnCount; i++) {
            connections.get(i).close();
            engine.sessionRegistry().unregisterSession(connections.get(i).connectionId());
        }

        assertThat(engine.sessionRegistry().activeSessionCount()).isEqualTo(CLIENT_COUNT - churnCount);

        // Send 1 more message to group 0 (whose first 20 members were churned)
        UUID conv0 = groupIds.get(0);
        CommandExecutionResult postChurnRes = stateMachine.apply(new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userIds.get(0), "dev-0", "churn-post-1",
                UUID.randomUUID(), conv0, "Message after churn", null
        ));
        assertThat(postChurnRes.isSuccess()).isTrue();
        engine.dispatchDomainEvent(postChurnRes.event());

        // Reconnect user 0 with a fresh connection and replay from seq 6
        ScaledMockConnection reconnectedConn = new ScaledMockConnection(userIds.get(0), "dev-0");
        UserSession reconnectedSession = new UserSession(userIds.get(0), "dev-0", reconnectedConn);
        engine.sessionRegistry().registerSession(reconnectedSession);

        engine.replayConversation(conv0, 6L, 10, reconnectedSession);

        Frame replayed = reconnectedConn.poll(3, TimeUnit.SECONDS);
        assertThat(replayed).isNotNull();
        ConversationEventPayload p = ConversationEventPayload.decode(replayed.payload().duplicate());
        assertThat(p.seqNumber()).isEqualTo(6L);
        com.vibe.domain.event.MessageSentEvent decodedMsg = com.vibe.domain.event.MessageSentEvent.decode(
                p.eventId(), p.conversationId(), p.seqNumber(), p.senderUserId(), p.timestamp(), java.nio.ByteBuffer.wrap(p.payload())
        );
        assertThat(decodedMsg.content()).isEqualTo("Message after churn");
    }
}
