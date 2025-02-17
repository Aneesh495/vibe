package com.vibe.storage.ratis;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.DomainCommandCodec;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.CommandExecutionResultCodec;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.domain.state.SnapshotCodec;
import com.vibe.protocol.ErrorCode;
import org.apache.ratis.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.protocol.TermIndex;
import org.apache.ratis.server.raftlog.RaftLog;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.server.storage.FileInfo;
import org.apache.ratis.statemachine.SnapshotInfo;
import org.apache.ratis.statemachine.StateMachineStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.apache.ratis.statemachine.impl.SimpleStateMachineStorage;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Apache Ratis StateMachine implementation wrapping Vibe's deterministic {@link DomainStateMachine}.
 *
 * <p>Translates Raft log entry commits into deterministic state transitions.
 * Guarantees that commands are executed in strict log order and returns the serialized
 * {@link CommandExecutionResult} back to the Raft client.
 */
public class VibeRatisStateMachine extends BaseStateMachine {

    private static final Logger log = LoggerFactory.getLogger(VibeRatisStateMachine.class);

    private final DomainStateMachine domainStateMachine;
    private final SimpleStateMachineStorage storage = new SimpleStateMachineStorage();

    public VibeRatisStateMachine(DomainStateMachine domainStateMachine) {
        this.domainStateMachine = Objects.requireNonNull(domainStateMachine);
    }

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage raftStorage) throws IOException {
        super.initialize(server, groupId, raftStorage);
        this.storage.init(raftStorage);
        loadSnapshot();
    }

    private void loadSnapshot() {
        try {
            SnapshotInfo snapshot = storage.getLatestSnapshot();
            if (snapshot == null || snapshot.getFiles().isEmpty()) {
                log.info("No prior Ratis snapshot found for group; starting from baseline state");
                return;
            }

            TermIndex termIndex = snapshot.getTermIndex();
            log.info("Loading Ratis snapshot at termIndex: {}", termIndex);
            FileInfo fileInfo = snapshot.getFiles().get(0);
            Path snapshotPath = fileInfo.getPath();
            byte[] bytes = Files.readAllBytes(snapshotPath);

            SnapshotCodec.DomainSnapshot domainSnapshot = SnapshotCodec.deserialize(bytes);
            domainStateMachine.restore(domainSnapshot);
            setLastAppliedTermIndex(termIndex);
            log.info("Restored domain state machine to lastAppliedIndex={}", domainStateMachine.lastAppliedIndex());
        } catch (Exception e) {
            log.error("Failed to load Ratis snapshot", e);
            throw new RuntimeException("Could not restore snapshot", e);
        }
    }

    @Override
    public StateMachineStorage getStateMachineStorage() {
        return storage;
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        LogEntryProto entry = trx.getLogEntry();
        long index = entry.getIndex();
        long term = entry.getTerm();
        ByteString data = entry.getStateMachineLogEntry().getLogData();

        try {
            DomainCommand command = DomainCommandCodec.decode(ByteBuffer.wrap(data.toByteArray()));
            CommandExecutionResult result = domainStateMachine.apply(command);
            domainStateMachine.setLastAppliedIndex(index);
            updateLastAppliedTermIndex(term, index);

            byte[] encodedResult = CommandExecutionResultCodec.encode(result);
            return CompletableFuture.completedFuture(Message.valueOf(ByteString.copyFrom(encodedResult)));
        } catch (Exception e) {
            log.error("Failed to apply transaction at term={}, index={}", term, index, e);
            CommandExecutionResult failure = CommandExecutionResult.failure(
                    ErrorCode.UNKNOWN_ERROR,
                    "StateMachine application error: " + e.getMessage()
            );
            byte[] encodedResult = CommandExecutionResultCodec.encode(failure);
            return CompletableFuture.completedFuture(Message.valueOf(ByteString.copyFrom(encodedResult)));
        }
    }

    @Override
    public long takeSnapshot() throws IOException {
        TermIndex termIndex = getLastAppliedTermIndex();
        if (termIndex == null || termIndex.getIndex() <= 0) {
            return RaftLog.INVALID_LOG_INDEX;
        }

        long index = termIndex.getIndex();
        long term = termIndex.getTerm();
        log.info("Taking Ratis snapshot at term={}, index={}", term, index);

        try {
            byte[] snapshotBytes = SnapshotCodec.serialize(domainStateMachine);
            File snapshotFile = storage.getSnapshotFile(term, index);
            File tempFile = new File(snapshotFile.getParentFile(), snapshotFile.getName() + ".tmp");

            Files.write(tempFile.toPath(), snapshotBytes);
            Files.move(tempFile.toPath(), snapshotFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

            log.info("Ratis snapshot written to {} (size={} bytes)", snapshotFile.getName(), snapshotBytes.length);
            return index;
        } catch (Exception e) {
            log.error("Failed to take snapshot at term={}, index={}", term, index, e);
            throw new IOException("Failed to take snapshot", e);
        }
    }

    @Override
    public CompletableFuture<Message> query(Message request) {
        return CompletableFuture.completedFuture(Message.valueOf(
                ByteString.copyFromUtf8("lastAppliedIndex:" + domainStateMachine.lastAppliedIndex())
        ));
    }

    public DomainStateMachine domainStateMachine() {
        return domainStateMachine;
    }
}
