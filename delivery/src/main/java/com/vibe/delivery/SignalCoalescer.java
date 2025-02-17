package com.vibe.delivery;

import com.vibe.protocol.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coalesces rapid ephemeral signals (e.g. typing indicators and presence pulses).
 *
 * <p>Under high load, replaces pending transient signals with the freshest update
 * to prevent delivery queue saturation and conserve network bandwidth.
 */
public final class SignalCoalescer {

    private static final Logger log = LoggerFactory.getLogger(SignalCoalescer.class);

    public record SignalKey(UUID conversationId, String userId, int signalType) {
        public SignalKey {
            Objects.requireNonNull(conversationId, "conversationId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
        }
    }

    private final ConcurrentHashMap<SignalKey, Frame> pendingSignals = new ConcurrentHashMap<>();
    private final DeliveryMetrics metrics;

    public SignalCoalescer(DeliveryMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics);
    }

    /**
     * Puts a transient signal frame. If an older unconsumed signal exists for the same key,
     * it is overwritten and recorded as coalesced.
     */
    public void putSignal(UUID conversationId, String userId, int signalType, Frame frame) {
        SignalKey key = new SignalKey(conversationId, userId, signalType);
        Frame prev = pendingSignals.put(key, frame);
        if (prev != null) {
            metrics.incrementDroppedSignals();
        }
    }

    /**
     * Polls the pending signal for the given key, clearing it from the buffer.
     */
    public Frame pollSignal(UUID conversationId, String userId, int signalType) {
        SignalKey key = new SignalKey(conversationId, userId, signalType);
        return pendingSignals.remove(key);
    }

    public int pendingSignalCount() {
        return pendingSignals.size();
    }

    public void clear() {
        pendingSignals.clear();
    }
}
