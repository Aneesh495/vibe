package com.vibe.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A serialized execution lane for conversation event fanout.
 *
 * <p>Ensures that all delivery operations for conversations assigned to this lane
 * execute strictly sequentially in monotonic sequence order, eliminating fanout race conditions.
 */
public final class DeliveryLane implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DeliveryLane.class);

    private final int laneId;
    private final ExecutorService executor;

    public DeliveryLane(int laneId) {
        this.laneId = laneId;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "vibe-delivery-lane-" + laneId + "-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }

    public int laneId() {
        return laneId;
    }

    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
