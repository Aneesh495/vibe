package com.vibe.delivery;

import com.vibe.domain.entity.Conversation;
import com.vibe.domain.entity.ConversationMember;
import com.vibe.domain.entity.Message;
import com.vibe.domain.event.DomainEvent;
import com.vibe.domain.event.MessageSentEvent;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.ConversationEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * High-throughput fanout and delivery engine.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Serialized ordered fanout per conversation lane</li>
 *   <li>Concurrent execution across independent conversation lanes</li>
 *   <li>Multi-device synchronization for senders and recipients</li>
 *   <li>Strict per-session backpressure and slow-consumer eviction with resumable cursors</li>
 *   <li>Signal coalescing for transient presence/typing updates under pressure</li>
 * </ul>
 */
public final class DeliveryEngine implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEngine.class);

    private final SessionRegistry sessionRegistry;
    private final DeliveryLanePool lanePool;
    private final SignalCoalescer signalCoalescer;
    private final DeliveryMetrics metrics;
    private final DomainStateMachine stateMachine;

    public DeliveryEngine(DomainStateMachine stateMachine, int laneCount) {
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine must not be null");
        this.metrics = new DeliveryMetrics();
        this.sessionRegistry = new SessionRegistry(metrics);
        this.lanePool = new DeliveryLanePool(laneCount > 0 ? laneCount : DeliveryConstants.DEFAULT_LANE_COUNT);
        this.signalCoalescer = new SignalCoalescer(metrics);
        this.metrics.setActiveLanes(lanePool.poolSize());
    }

    public SessionRegistry sessionRegistry() {
        return sessionRegistry;
    }

    public DeliveryMetrics metrics() {
        return metrics;
    }

    public SignalCoalescer signalCoalescer() {
        return signalCoalescer;
    }

    public DeliveryLanePool lanePool() {
        return lanePool;
    }

    /**
     * Dispatches a committed durable domain event into the appropriate serialized conversation lane.
     */
    public void dispatchDomainEvent(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        metrics.incrementDispatched();

        UUID conversationId = event.conversationId();
        DeliveryLane lane = lanePool.getLane(conversationId);

        lane.execute(() -> {
            try {
                Conversation conversation = stateMachine.getConversation(conversationId);
                if (conversation == null) {
                    log.warn("Cannot fan out event {}: conversation {} not found", event.eventId(), conversationId);
                    return;
                }

                // Convert domain event into wire frame once for all recipients
                Frame wireFrame = toWireFrame(event);

                for (ConversationMember member : conversation.members().values()) {
                    List<UserSession> sessions = sessionRegistry.getSessionsForUser(member.userId());
                    for (UserSession session : sessions) {
                        session.tryEnqueue(wireFrame, true, metrics);
                        session.updateCursor(conversationId, event.seqNumber());
                    }
                }
            } catch (Exception e) {
                log.error("Error during serialized fanout of event {} on conversation {}",
                        event.eventId(), conversationId, e);
            }
        });
    }

    /**
     * Dispatches a transient (non-durable) conversation signal (e.g. typing or presence).
     */
    public void dispatchTransientSignal(UUID conversationId, String senderUserId, short signalType, byte[] payload) {
        metrics.incrementDispatched();
        signalCoalescer.putSignal(conversationId, senderUserId, signalType, null);

        DeliveryLane lane = lanePool.getLane(conversationId);
        lane.execute(() -> {
            try {
                Conversation conversation = stateMachine.getConversation(conversationId);
                if (conversation == null) {
                    return;
                }

                ConversationEventPayload eventPayload = new ConversationEventPayload(
                        conversationId,
                        -1L, // Non-durable signals carry sequence -1
                        UUID.randomUUID(),
                        signalType,
                        senderUserId,
                        System.currentTimeMillis(),
                        payload
                );

                Frame frame = Frame.create(FrameType.CONVERSATION_EVENT, 0L, eventPayload.encode());

                for (ConversationMember member : conversation.members().values()) {
                    // Do not echo typing to the sender's own device
                    if (member.userId().equals(senderUserId)) {
                        continue;
                    }
                    List<UserSession> sessions = sessionRegistry.getSessionsForUser(member.userId());
                    for (UserSession session : sessions) {
                        session.tryEnqueue(frame, false, metrics);
                    }
                }
            } catch (Exception e) {
                log.warn("Error fanning out transient signal for conv {}", conversationId, e);
            }
        });
    }

    /**
     * Replays committed messages for a reconnecting client resuming from a cursor.
     */
    public void replayConversation(UUID conversationId, long fromSeq, int limit, UserSession session) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(session, "session must not be null");

        List<Message> messages = stateMachine.getMessages(conversationId, fromSeq, limit);
        log.info("Replaying {} committed messages for conversation {} from seq {} to user {}",
                messages.size(), conversationId, fromSeq, session.userId());

        for (Message msg : messages) {
            MessageSentEvent event = new MessageSentEvent(
                    UUID.randomUUID(),
                    conversationId,
                    msg.seq(),
                    msg.senderUserId(),
                    msg.sentAt(),
                    msg.messageId(),
                    msg.clientMessageId(),
                    msg.content(),
                    msg.attachment()
            );
            Frame frame = toWireFrame(event);
            session.tryEnqueue(frame, true, metrics);
            session.updateCursor(conversationId, msg.seq());
        }
    }

    private static Frame toWireFrame(DomainEvent event) {
        ByteBuffer payloadBuf = event.encodePayload();
        byte[] payloadBytes = new byte[payloadBuf.remaining()];
        payloadBuf.get(payloadBytes);

        ConversationEventPayload convPayload = new ConversationEventPayload(
                event.conversationId(),
                event.seqNumber(),
                event.eventId(),
                event.eventType(),
                event.senderUserId(),
                event.timestamp(),
                payloadBytes
        );

        return Frame.create(FrameType.CONVERSATION_EVENT, 0L, convPayload.encode());
    }

    @Override
    public void close() {
        log.info("Closing DeliveryEngine...");
        lanePool.close();
    }
}
