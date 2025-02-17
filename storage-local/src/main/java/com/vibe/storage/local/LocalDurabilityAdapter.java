package com.vibe.storage.local;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.DomainCommandCodec;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.domain.durability.DurabilityAdapter;
import com.vibe.domain.state.SnapshotCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Local Durability Adapter implementing standalone durable persistence.
 *
 * <p>Coordinates the Segmented WAL, periodic atomic snapshots, and sequential
 * state machine application upon fsync commitment.
 */
public final class LocalDurabilityAdapter implements DurabilityAdapter {

    private static final Logger log = LoggerFactory.getLogger(LocalDurabilityAdapter.class);

    private final Path baseDir;
    private final DomainStateMachine stateMachine;
    private final SegmentedWal wal;
    private final SnapshotManager snapshotManager;

    public LocalDurabilityAdapter(
            Path baseDir,
            DomainStateMachine stateMachine,
            long maxSegmentSize,
            long syncIntervalMs,
            int maxBatchSize
    ) {
        this.baseDir = Objects.requireNonNull(baseDir);
        this.stateMachine = Objects.requireNonNull(stateMachine);
        this.wal = new SegmentedWal(baseDir.resolve("wal"), maxSegmentSize, syncIntervalMs, maxBatchSize);
        this.snapshotManager = new SnapshotManager(baseDir.resolve("snapshots"));
    }

    public synchronized void start() throws Exception {
        log.info("Starting LocalDurabilityAdapter at {}", baseDir);

        // 1. Recover latest snapshot if one exists
        Optional<SnapshotCodec.DomainSnapshot> snapshotOpt = snapshotManager.loadLatestSnapshot();
        if (snapshotOpt.isPresent()) {
            SnapshotCodec.DomainSnapshot snap = snapshotOpt.get();
            stateMachine.restore(snap);
            log.info("Restored baseline state from snapshot at index {}", snap.lastAppliedIndex());
        }

        // 2. Start WAL and run recovery engine
        wal.start();

        // 3. Replay uncheckpointed WAL records from snapshot point forward
        replayCommittedRecords(stateMachine.lastAppliedIndex() + 1);

        log.info("LocalDurabilityAdapter ready (stateMachine appliedIndex={})", stateMachine.lastAppliedIndex());
    }

    private void replayCommittedRecords(long fromIndex) throws IOException {
        long targetIndex = fromIndex;
        long nextIndex = wal.nextLogIndex();

        for (WalSegment segment : wal.segments().values()) {
            if (segment.lastIndex() < targetIndex) {
                continue;
            }

            long offset = WalConstants.SEGMENT_HEADER_SIZE;
            while (offset < segment.writePosition()) {
                WalRecord record = segment.readRecord(offset);
                if (record == null) {
                    break;
                }

                if (record.logIndex() >= targetIndex) {
                    if (record.type() == WalRecordType.COMMAND) {
                        DomainCommand cmd = DomainCommandCodec.decode(ByteBuffer.wrap(record.payload()));
                        stateMachine.apply(cmd);
                        stateMachine.setLastAppliedIndex(record.logIndex());
                    }
                    targetIndex = record.logIndex() + 1;
                }
                offset += record.totalSize();
            }
        }
    }

    /**
     * Submits a command for durable commit. Acknowledgment completes only after WAL fsync!
     */
    public CompletableFuture<CommandExecutionResult> executeCommand(DomainCommand command, boolean sync) {
        return wal.append(command, sync).thenApply(committedIndex -> {
            // Fsync confirmed! Now apply deterministically to state machine
            CommandExecutionResult result = stateMachine.apply(command);
            stateMachine.setLastAppliedIndex(committedIndex);
            return result;
        });
    }

    /**
     * Takes an atomic snapshot of current state machine.
     */
    public synchronized Path takeSnapshot() throws Exception {
        long appliedIndex = stateMachine.lastAppliedIndex();
        return snapshotManager.createSnapshot(stateMachine, appliedIndex);
    }

    public DomainStateMachine stateMachine() {
        return stateMachine;
    }

    public SegmentedWal wal() {
        return wal;
    }

    public SnapshotManager snapshotManager() {
        return snapshotManager;
    }

    @Override
    public long lastCommittedIndex() {
        return stateMachine.lastAppliedIndex();
    }

    @Override
    public boolean isLeader() {
        return true;
    }

    @Override
    public synchronized void close() throws IOException {
        wal.close();
    }
}
