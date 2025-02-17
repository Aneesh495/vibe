package com.vibe.storage.ratis;

import com.vibe.domain.state.DomainStateMachine;
import org.apache.ratis.protocol.RaftPeer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Manages an in-process 3-node Apache Ratis consensus cluster.
 *
 * <p>Provides lifecycle controls for spinning up a full quorum, finding the leader,
 * simulating node failure / network partitions, and verifying catch-up replication.
 */
public final class RatisCluster implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RatisCluster.class);

    private final Path rootDir;
    private final int nodeCount;
    private final List<Integer> ports;
    private final List<RaftPeer> peers;
    private final List<RatisDurabilityAdapter> adapters;
    private final List<DomainStateMachine> stateMachines;

    public RatisCluster(Path rootDir, int nodeCount) throws IOException {
        this.rootDir = Objects.requireNonNull(rootDir);
        this.nodeCount = nodeCount;
        this.ports = new ArrayList<>(nodeCount);
        this.peers = new ArrayList<>(nodeCount);
        this.adapters = new ArrayList<>(nodeCount);
        this.stateMachines = new ArrayList<>(nodeCount);

        List<String> addresses = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            int port = findFreePort();
            ports.add(port);
            addresses.add("127.0.0.1:" + port);
        }

        this.peers.addAll(RatisClusterConfig.buildPeers(addresses));

        for (int i = 0; i < nodeCount; i++) {
            String peerId = "node" + (i + 1);
            Path nodeDir = rootDir.resolve(peerId);
            Files.createDirectories(nodeDir);

            RatisClusterConfig config = new RatisClusterConfig(
                    peerId,
                    ports.get(i),
                    nodeDir,
                    peers,
                    RatisClusterConfig.DEFAULT_GROUP_ID
            );

            DomainStateMachine sm = new DomainStateMachine();
            stateMachines.add(sm);
            adapters.add(new RatisDurabilityAdapter(config, sm));
        }
    }

    public static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    /**
     * Starts all nodes in the cluster.
     */
    public void start() throws Exception {
        log.info("Starting {} node Ratis cluster...", nodeCount);
        for (RatisDurabilityAdapter adapter : adapters) {
            adapter.start();
        }
    }

    /**
     * Waits for a leader to be elected across the cluster within a timeout.
     */
    public RatisDurabilityAdapter awaitLeader(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            for (RatisDurabilityAdapter adapter : adapters) {
                if (adapter.isLeader()) {
                    log.info("Leader identified: {}", adapter.server().getId());
                    return adapter;
                }
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("No leader elected in Ratis cluster within " + timeout + " " + unit);
    }

    public List<RatisDurabilityAdapter> adapters() {
        return Collections.unmodifiableList(adapters);
    }

    public List<DomainStateMachine> stateMachines() {
        return Collections.unmodifiableList(stateMachines);
    }

    public RatisDurabilityAdapter getAdapter(int index) {
        return adapters.get(index);
    }

    public void stopNode(int index) throws IOException {
        log.info("Stopping Ratis node index {}", index);
        adapters.get(index).close();
    }

    @Override
    public void close() throws IOException {
        log.info("Shutting down Ratis cluster...");
        for (RatisDurabilityAdapter adapter : adapters) {
            try {
                adapter.close();
            } catch (Exception e) {
                log.warn("Error closing adapter: {}", e.getMessage());
            }
        }
    }
}
