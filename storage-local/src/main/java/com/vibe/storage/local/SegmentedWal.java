package com.vibe.storage.local;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.DomainCommandCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Segmented Write-Ahead Log orchestrating active and sealed segments, crash-safe rotation,
 * and group commit fsyncs.
 */
public final class SegmentedWal implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SegmentedWal.class);

    private final Path walDir;
    private final long maxSegmentSize;
    private final SparseIndex sparseIndex = new SparseIndex();
    private final GroupCommitBatcher batcher;

    private final AtomicLong nextLogIndex = new AtomicLong(1L);
    private final Map<Long, WalSegment> segments = new ConcurrentHashMap<>();
    private WalSegment activeSegment;
    private boolean isClosed = false;

    public SegmentedWal(Path walDir, long maxSegmentSize, long syncIntervalMs, int maxBatchSize) {
        this.walDir = Objects.requireNonNull(walDir);
        this.maxSegmentSize = maxSegmentSize > 0 ? maxSegmentSize : WalConstants.DEFAULT_MAX_SEGMENT_SIZE;
        try {
            Files.createDirectories(walDir);
        } catch (IOException e) {
            throw new StorageException("Failed to initialize WAL directory: " + walDir, e);
        }
        this.batcher = new GroupCommitBatcher(this, syncIntervalMs, maxBatchSize);
    }

    public synchronized void start() throws IOException {
        recoverOrInitialize();
        batcher.start();
        log.info("SegmentedWal started in {} (activeSegment={}, nextIndex={})",
                walDir, activeSegment != null ? activeSegment.segmentId() : -1, nextLogIndex.get());
    }

    private void recoverOrInitialize() throws IOException {
        List<Path> walFiles;
        try (var stream = Files.list(walDir)) {
            walFiles = stream.filter(p -> p.toString().endsWith(".wal"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
        }

        if (walFiles.isEmpty()) {
            // Brand new WAL: create segment 1
            Path firstSegmentPath = walDir.resolve(String.format("segment-%08d.wal", 1));
            activeSegment = WalSegment.createNew(firstSegmentPath, 1L, 1L);
            segments.put(1L, activeSegment);
            nextLogIndex.set(1L);
            return;
        }

        // Open existing segments and run recovery
        long highestSegmentId = 0L;
        for (Path p : walFiles) {
            WalSegment seg = WalSegment.openExisting(p);
            segments.put(seg.segmentId(), seg);
            if (seg.segmentId() > highestSegmentId) {
                highestSegmentId = seg.segmentId();
            }
        }

        activeSegment = segments.get(highestSegmentId);
        WalRecoveryEngine.recover(this, activeSegment, sparseIndex, nextLogIndex);
    }

    /**
     * Appends a domain command to the log and returns a future completing upon durable group fsync.
     */
    public CompletableFuture<Long> append(DomainCommand command, boolean sync) {
        if (isClosed) {
            CompletableFuture<Long> f = new CompletableFuture<>();
            f.completeExceptionally(new StorageException("WAL is closed"));
            return f;
        }

        ByteBuffer encodedCmd = DomainCommandCodec.encode(command);
        byte[] payload = new byte[encodedCmd.remaining()];
        encodedCmd.get(payload);

        long index = nextLogIndex.getAndIncrement();
        WalRecord record = WalRecord.create(WalRecordType.COMMAND, index, payload);

        return batcher.submit(record, sync);
    }

    /**
     * Internal append invoked by the GroupCommitBatcher thread.
     */
    synchronized void appendInternal(WalRecord record) throws IOException {
        rotateIfNeeded();
        long offset = activeSegment.append(record);
        sparseIndex.record(activeSegment.segmentId(), offset, record.logIndex());
    }

    /**
     * Rotates to a new segment if active segment exceeds maxSegmentSize.
     */
    private synchronized void rotateIfNeeded() throws IOException {
        if (activeSegment.writePosition() >= maxSegmentSize) {
            long nextSegmentId = activeSegment.segmentId() + 1;
            long nextStartIndex = activeSegment.lastIndex() + 1;
            activeSegment.force(true);

            Path nextPath = walDir.resolve(String.format("segment-%08d.wal", nextSegmentId));
            WalSegment newSegment = WalSegment.createNew(nextPath, nextSegmentId, nextStartIndex);
            segments.put(nextSegmentId, newSegment);
            activeSegment = newSegment;
            log.info("Rotated to new WAL segment {} (startIndex={})", nextPath.getFileName(), nextStartIndex);
        }
    }

    synchronized void forceActiveSegment() throws IOException {
        if (activeSegment != null) {
            activeSegment.force(false);
        }
    }

    public long nextLogIndex() {
        return nextLogIndex.get();
    }

    public SparseIndex sparseIndex() {
        return sparseIndex;
    }

    public Map<Long, WalSegment> segments() {
        return Collections.unmodifiableMap(segments);
    }

    public WalSegment activeSegment() {
        return activeSegment;
    }

    public void setActiveSegment(WalSegment segment) {
        this.activeSegment = segment;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
            batcher.stop();
            for (WalSegment seg : segments.values()) {
                seg.close();
            }
            log.info("SegmentedWal closed");
        }
    }
}
