package com.vibe.delivery;

import com.vibe.protocol.ErrorCode;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameHeader;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.ErrorPayload;
import com.vibe.transport.nio.TransportConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an authenticated active user session on a specific device and connection.
 *
 * <p>Enforces bounded queue capacity and backpressure. Coalesces or drops transient signals
 * under load, and terminates slow consumers with a resumable cursor when durable event queues
 * overflow.
 */
public final class UserSession {

    private static final Logger log = LoggerFactory.getLogger(UserSession.class);

    private final String userId;
    private final String deviceId;
    private final TransportConnection connection;
    private final long connectedAt;

    private final AtomicInteger pendingFrames = new AtomicInteger(0);
    private final AtomicLong pendingBytes = new AtomicLong(0L);
    private final AtomicBoolean isTerminated = new AtomicBoolean(false);

    // Tracks last acknowledged delivered sequence per conversation for replay resumption
    private final ConcurrentHashMap<UUID, Long> cursors = new ConcurrentHashMap<>();

    public UserSession(String userId, String deviceId, TransportConnection connection) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.connectedAt = System.currentTimeMillis();
    }

    public String userId() {
        return userId;
    }

    public String deviceId() {
        return deviceId;
    }

    public TransportConnection connection() {
        return connection;
    }

    public long connectedAt() {
        return connectedAt;
    }

    public long getCursor(UUID conversationId) {
        return cursors.getOrDefault(conversationId, 0L);
    }

    public void updateCursor(UUID conversationId, long seq) {
        cursors.compute(conversationId, (k, prev) -> prev == null || seq > prev ? seq : prev);
    }

    public int pendingFrames() {
        return pendingFrames.get();
    }

    public long pendingBytes() {
        return pendingBytes.get();
    }

    public boolean isConnected() {
        return connection.isOpen() && !isTerminated.get();
    }

    /**
     * Attempts to enqueue a frame for delivery to this session's connection with strict backpressure.
     *
     * @param frame The protocol frame to transmit
     * @param isDurable True if this is a durable message/mutation event that must not be silently dropped
     * @param metrics Delivery metrics collector
     * @return true if successfully admitted to the connection queue; false otherwise
     */
    public boolean tryEnqueue(Frame frame, boolean isDurable, DeliveryMetrics metrics) {
        if (!isConnected()) {
            return false;
        }

        int frameSize = frame.header().payloadLength();
        int currentPending = pendingFrames.get();

        if (currentPending >= DeliveryConstants.MAX_PENDING_DELIVERIES_PER_SESSION) {
            if (!isDurable) {
                // Non-durable signal (typing indicator, presence) is safely dropped under pressure
                metrics.incrementDroppedSignals();
                log.debug("Dropped transient signal for user {} device {} (queue full: {})",
                        userId, deviceId, currentPending);
                return false;
            } else {
                // Durable event queue overflow: Slow consumer! Disconnect with resumable cursor
                if (isTerminated.compareAndSet(false, true)) {
                    metrics.incrementSlowConsumersDisconnected();
                    log.warn("Slow consumer detected: user {} device {} exceeded queue capacity ({} frames). Disconnecting with cursor...",
                            userId, deviceId, currentPending);

                    // Send error frame notifying client to resume
                    ErrorPayload errorPayload = ErrorPayload.fromErrorCode(
                            ErrorCode.OVERLOAD_SLOW_CONSUMER,
                            "Client delivery queue overflow; reconnect and resume from cursor"
                    );
                    Frame errorFrame = Frame.create(FrameType.ERROR, frame.header().flags(), 0L, errorPayload.encode());
                    connection.send(errorFrame);
                    connection.close();
                }
                return false;
            }
        }

        pendingFrames.incrementAndGet();
        pendingBytes.addAndGet(frameSize);

        boolean enqueued = connection.send(frame);
        if (enqueued) {
            metrics.incrementDelivered();
            return true;
        } else {
            pendingFrames.decrementAndGet();
            pendingBytes.addAndGet(-frameSize);
            return false;
        }
    }

    public void onFrameDrained(int frameSize) {
        pendingFrames.decrementAndGet();
        pendingBytes.addAndGet(-frameSize);
    }

    public void close() {
        if (isTerminated.compareAndSet(false, true)) {
            connection.close();
        }
    }
}
