package com.vibe.delivery;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance lock-free delivery metrics.
 */
public final class DeliveryMetrics {

    private final AtomicLong totalDispatched = new AtomicLong(0L);
    private final AtomicLong totalDelivered = new AtomicLong(0L);
    private final AtomicLong droppedSignals = new AtomicLong(0L);
    private final AtomicLong slowConsumersDisconnected = new AtomicLong(0L);
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final AtomicInteger activeLanes = new AtomicInteger(0);

    public void incrementDispatched() {
        totalDispatched.incrementAndGet();
    }

    public void incrementDelivered() {
        totalDelivered.incrementAndGet();
    }

    public void incrementDroppedSignals() {
        droppedSignals.incrementAndGet();
    }

    public void incrementSlowConsumersDisconnected() {
        slowConsumersDisconnected.incrementAndGet();
    }

    public void sessionConnected() {
        activeSessions.incrementAndGet();
    }

    public void sessionDisconnected() {
        activeSessions.decrementAndGet();
    }

    public void setActiveLanes(int lanes) {
        activeLanes.set(lanes);
    }

    public long totalDispatched() {
        return totalDispatched.get();
    }

    public long totalDelivered() {
        return totalDelivered.get();
    }

    public long droppedSignals() {
        return droppedSignals.get();
    }

    public long slowConsumersDisconnected() {
        return slowConsumersDisconnected.get();
    }

    public int activeSessions() {
        return activeSessions.get();
    }

    public int activeLanes() {
        return activeLanes.get();
    }

    public DeliveryMetricsSnapshot snapshot() {
        return new DeliveryMetricsSnapshot(
                totalDispatched.get(),
                totalDelivered.get(),
                droppedSignals.get(),
                slowConsumersDisconnected.get(),
                activeSessions.get(),
                activeLanes.get()
        );
    }

    public record DeliveryMetricsSnapshot(
            long totalDispatched,
            long totalDelivered,
            long droppedSignals,
            long slowConsumersDisconnected,
            int activeSessions,
            int activeLanes
    ) {}
}
