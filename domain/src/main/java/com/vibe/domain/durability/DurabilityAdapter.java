package com.vibe.domain.durability;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Unified durability interface abstraction shared between the Standalone Local WAL
 * and the Replicated Apache Ratis Quorum Commit cluster.
 *
 * <p>Guarantees that a returned {@link CompletableFuture} completes only after the command
 * has achieved durable commit under the configured mode (fsync in local mode, Raft quorum commit
 * in replicated mode), and has been deterministically applied to the state machine.
 */
public interface DurabilityAdapter extends Closeable {

    /**
     * Starts the durability engine, executing any recovery, snapshot restoration,
     * or cluster election handshakes.
     */
    void start() throws Exception;

    /**
     * Submits a domain command for durable consensus/logging and deterministic state machine application.
     *
     * @param command Domain mutation command
     * @param sync whether to await fsync (local mode) or quorum flush
     * @return Future resolving to the execution result once durably committed
     */
    CompletableFuture<CommandExecutionResult> executeCommand(DomainCommand command, boolean sync);

    /**
     * Returns the underlying deterministic state machine.
     */
    DomainStateMachine stateMachine();

    /**
     * Returns the highest committed log index applied to the state machine.
     */
    long lastCommittedIndex();

    /**
     * Returns true if this node is currently authoritative (always true in standalone,
     * leader in Raft cluster).
     */
    boolean isLeader();

    @Override
    void close() throws IOException;
}
