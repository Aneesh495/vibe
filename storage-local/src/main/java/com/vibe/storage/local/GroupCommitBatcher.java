package com.vibe.storage.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance group commit batcher for the write-ahead log.
 *
 * <p>Coalesces concurrent append requests into a single FileChannel.force(false) fsync,
 * amortizing disk I/O latency across multiple concurrent transactions.
 */
public final class GroupCommitBatcher implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GroupCommitBatcher.class);

    public record PendingAppend(
            WalRecord record,
            CompletableFuture<Long> future,
            boolean requireImmediateSync
    ) {
    }

    private final SegmentedWal wal;
    private final long syncIntervalMs;
    private final int maxBatchSize;

    private final BlockingQueue<PendingAppend> queue = new LinkedBlockingQueue<>(50_000);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    public GroupCommitBatcher(SegmentedWal wal, long syncIntervalMs, int maxBatchSize) {
        this.wal = Objects.requireNonNull(wal);
        this.syncIntervalMs = syncIntervalMs > 0 ? syncIntervalMs : WalConstants.DEFAULT_COMMIT_INTERVAL_MS;
        this.maxBatchSize = maxBatchSize > 0 ? maxBatchSize : WalConstants.DEFAULT_MAX_BATCH_SIZE;
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, "vibe-wal-group-commit");
            thread.setDaemon(true);
            thread.start();
            log.info("Started WAL GroupCommitBatcher (syncInterval={}ms, maxBatch={})", syncIntervalMs, maxBatchSize);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(2000);
                } catch (InterruptedException ignored) {
                }
            }
            // Drain remaining entries
            flushRemaining();
            log.info("Stopped WAL GroupCommitBatcher");
        }
    }

    public CompletableFuture<Long> submit(WalRecord record, boolean sync) {
        if (!running.get()) {
            CompletableFuture<Long> f = new CompletableFuture<>();
            f.completeExceptionally(new StorageException("WAL batcher is not running"));
            return f;
        }

        CompletableFuture<Long> future = new CompletableFuture<>();
        if (!queue.offer(new PendingAppend(record, future, sync))) {
            future.completeExceptionally(new StorageException("WAL append queue is full"));
        }
        return future;
    }

    @Override
    public void run() {
        List<PendingAppend> batch = new ArrayList<>(maxBatchSize);

        while (running.get()) {
            try {
                PendingAppend first = queue.poll(syncIntervalMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    if (!first.requireImmediateSync()) {
                        queue.drainTo(batch, maxBatchSize - 1);
                    }
                    flushBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                if (!running.get()) {
                    break;
                }
            } catch (Exception e) {
                log.error("Fatal error in GroupCommitBatcher loop: {}", e.getMessage(), e);
            }
        }
    }

    private void flushBatch(List<PendingAppend> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            long lastCommittedIndex = -1L;
            for (PendingAppend item : batch) {
                wal.appendInternal(item.record());
                lastCommittedIndex = item.record().logIndex();
            }

            // Fsync to disk
            wal.forceActiveSegment();

            // Complete futures upon successful fsync
            for (PendingAppend item : batch) {
                item.future().complete(item.record().logIndex());
            }
        } catch (Throwable t) {
            log.error("Failed to commit WAL batch of size {}: {}", batch.size(), t.getMessage(), t);
            for (PendingAppend item : batch) {
                item.future().completeExceptionally(t);
            }
        }
    }

    private void flushRemaining() {
        List<PendingAppend> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        flushBatch(remaining);
    }
}
