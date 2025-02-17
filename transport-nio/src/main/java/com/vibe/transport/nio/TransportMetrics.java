package com.vibe.transport.nio;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time operational metrics for the NIO transport layer.
 */
public final class TransportMetrics {

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalConnectionsAccepted = new AtomicLong(0);
    private final AtomicLong totalConnectionsClosed = new AtomicLong(0);

    private final AtomicLong bytesRead = new AtomicLong(0);
    private final AtomicLong bytesWritten = new AtomicLong(0);
    private final AtomicLong framesDecoded = new AtomicLong(0);
    private final AtomicLong framesEncoded = new AtomicLong(0);

    private final AtomicLong rejectedTasks = new AtomicLong(0);
    private final AtomicLong backpressureDrops = new AtomicLong(0);
    private final AtomicLong slowConsumerCloses = new AtomicLong(0);

    public TransportMetrics() {
    }

    public void onConnectionAccepted() {
        activeConnections.incrementAndGet();
        totalConnectionsAccepted.incrementAndGet();
    }

    public void onConnectionClosed() {
        activeConnections.decrementAndGet();
        totalConnectionsClosed.incrementAndGet();
    }

    public void addBytesRead(long bytes) {
        bytesRead.addAndGet(bytes);
    }

    public void addBytesWritten(long bytes) {
        bytesWritten.addAndGet(bytes);
    }

    public void incrementFramesDecoded() {
        framesDecoded.incrementAndGet();
    }

    public void incrementFramesEncoded() {
        framesEncoded.incrementAndGet();
    }

    public void recordRejectedTask() {
        rejectedTasks.incrementAndGet();
    }

    public void recordBackpressureDrop() {
        backpressureDrops.incrementAndGet();
    }

    public void recordSlowConsumerClose() {
        slowConsumerCloses.incrementAndGet();
    }

    public int activeConnections() {
        return activeConnections.get();
    }

    public long totalConnectionsAccepted() {
        return totalConnectionsAccepted.get();
    }

    public long totalConnectionsClosed() {
        return totalConnectionsClosed.get();
    }

    public long bytesRead() {
        return bytesRead.get();
    }

    public long bytesWritten() {
        return bytesWritten.get();
    }

    public long framesDecoded() {
        return framesDecoded.get();
    }

    public long framesEncoded() {
        return framesEncoded.get();
    }

    public long rejectedTasks() {
        return rejectedTasks.get();
    }

    public long backpressureDrops() {
        return backpressureDrops.get();
    }

    public long slowConsumerCloses() {
        return slowConsumerCloses.get();
    }
}
