package com.vibe.storage.ratis;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.DomainCommandCodec;
import com.vibe.domain.durability.DurabilityAdapter;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.CommandExecutionResultCodec;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.protocol.ErrorCode;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Replicated Durability Adapter backed by Apache Ratis consensus.
 *
 * <p>In replicated mode, Apache Ratis is the sole authoritative command log.
 * Commands achieve durability only upon consensus commit across the Raft quorum.
 */
public final class RatisDurabilityAdapter implements DurabilityAdapter {

    private static final Logger log = LoggerFactory.getLogger(RatisDurabilityAdapter.class);

    private final RatisClusterConfig config;
    private final DomainStateMachine stateMachine;
    private final VibeRatisStateMachine ratisStateMachine;

    private RaftServer server;
    private RaftClient client;
    private volatile boolean isRunning = false;

    public RatisDurabilityAdapter(RatisClusterConfig config, DomainStateMachine stateMachine) {
        this.config = Objects.requireNonNull(config);
        this.stateMachine = Objects.requireNonNull(stateMachine);
        this.ratisStateMachine = new VibeRatisStateMachine(stateMachine);
    }

    public static RatisDurabilityAdapter create(
            String nodeId,
            int raftPort,
            java.nio.file.Path storageDir,
            java.util.List<String> peerAddresses,
            DomainStateMachine stateMachine
    ) {
        java.util.List<org.apache.ratis.protocol.RaftPeer> peers = RatisClusterConfig.buildPeers(peerAddresses);
        RatisClusterConfig config = new RatisClusterConfig(
                nodeId,
                raftPort,
                storageDir,
                peers,
                RatisClusterConfig.DEFAULT_GROUP_ID
        );
        return new RatisDurabilityAdapter(config, stateMachine);
    }

    @Override
    public synchronized void start() throws Exception {
        startInternal(false);
    }

    /**
     * Starts this adapter in recovery mode, rejoining the cluster from persisted state
     * rather than formatting a fresh storage directory.
     */
    public synchronized void startRecover() throws Exception {
        startInternal(true);
    }

    private void startInternal(boolean recover) throws Exception {
        if (isRunning) {
            return;
        }

        log.info("Starting RatisDurabilityAdapter for peer {} on port {} (recover={})",
                config.peerId(), config.port(), recover);
        RaftProperties properties = config.createProperties();
        RaftGroup group = config.raftGroup();

        RaftServer.Builder builder = RaftServer.newBuilder()
                .setServerId(config.peerId())
                .setProperties(properties)
                .setStateMachine(ratisStateMachine);

        if (!recover) {
            builder.setGroup(group);
        }

        this.server = builder.build();
        server.start();

        this.client = RaftClient.newBuilder()
                .setProperties(properties)
                .setRaftGroup(group)
                .setClientId(ClientId.randomId())
                .build();

        isRunning = true;
        log.info("RatisDurabilityAdapter started for peer {}", config.peerId());
    }

    @Override
    public CompletableFuture<CommandExecutionResult> executeCommand(DomainCommand command, boolean sync) {
        if (!isRunning) {
            CompletableFuture<CommandExecutionResult> f = new CompletableFuture<>();
            f.complete(CommandExecutionResult.failure(ErrorCode.UNKNOWN_ERROR, "Ratis adapter is not running"));
            return f;
        }

        ByteBuffer encoded = DomainCommandCodec.encode(command);
        Message message = Message.valueOf(ByteString.copyFrom(encoded));

        return client.async().send(message).thenApply(reply -> {
            if (reply.isSuccess()) {
                byte[] replyBytes = reply.getMessage().getContent().toByteArray();
                return CommandExecutionResultCodec.decode(ByteBuffer.wrap(replyBytes));
            } else {
                Exception ex = reply.getException();
                String err = ex != null ? ex.getMessage() : "Raft consensus execution failed";
                log.warn("Raft commit failure: {}", err);
                return CommandExecutionResult.failure(ErrorCode.DURABILITY_LEADER_STEPDOWN, err);
            }
        }).exceptionally(ex -> {
            log.error("Raft communication error during command execution", ex);
            return CommandExecutionResult.failure(ErrorCode.DURABILITY_QUORUM_LOST, ex.getMessage());
        });
    }

    @Override
    public DomainStateMachine stateMachine() {
        return stateMachine;
    }

    @Override
    public long lastCommittedIndex() {
        return stateMachine.lastAppliedIndex();
    }

    @Override
    public boolean isLeader() {
        if (server == null || !isRunning) {
            return false;
        }
        try {
            return server.getDivision(config.groupId()).getInfo().isLeader();
        } catch (Exception e) {
            return false;
        }
    }

    public RaftServer server() {
        return server;
    }

    public RaftClient client() {
        return client;
    }

    public VibeRatisStateMachine ratisStateMachine() {
        return ratisStateMachine;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        log.info("Closing RatisDurabilityAdapter for peer {}", config.peerId());

        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing RaftClient: {}", e.getMessage());
            }
        }

        if (server != null) {
            try {
                server.close();
            } catch (Exception e) {
                log.warn("Error closing RaftServer: {}", e.getMessage());
            }
        }
    }
}
