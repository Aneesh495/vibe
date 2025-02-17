package com.vibe.server;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Server configuration supporting Standalone and Replicated Raft cluster topologies.
 */
public final class VibeServerConfig {

    public enum ServerMode {
        STANDALONE,
        REPLICATED
    }

    private final ServerMode mode;
    private final int tcpPort;
    private final int wsPort;
    private final boolean tlsEnabled;
    private final Path storageDir;
    private final String nodeId;
    private final List<String> clusterPeers;
    private final int authThreads;
    private final int attachmentThreads;
    private final int reactorWorkers;
    private final int deliveryLanes;

    private VibeServerConfig(Builder builder) {
        this.mode = builder.mode;
        this.tcpPort = builder.tcpPort;
        this.wsPort = builder.wsPort;
        this.tlsEnabled = builder.tlsEnabled;
        this.storageDir = builder.storageDir;
        this.nodeId = builder.nodeId;
        this.clusterPeers = Collections.unmodifiableList(new ArrayList<>(builder.clusterPeers));
        this.authThreads = builder.authThreads;
        this.attachmentThreads = builder.attachmentThreads;
        this.reactorWorkers = builder.reactorWorkers;
        this.deliveryLanes = builder.deliveryLanes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ServerMode mode() {
        return mode;
    }

    public int tcpPort() {
        return tcpPort;
    }

    public int wsPort() {
        return wsPort;
    }

    public boolean tlsEnabled() {
        return tlsEnabled;
    }

    public Path storageDir() {
        return storageDir;
    }

    public String nodeId() {
        return nodeId;
    }

    public List<String> clusterPeers() {
        return clusterPeers;
    }

    public int authThreads() {
        return authThreads;
    }

    public int attachmentThreads() {
        return attachmentThreads;
    }

    public int reactorWorkers() {
        return reactorWorkers;
    }

    public int deliveryLanes() {
        return deliveryLanes;
    }

    public static class Builder {
        private ServerMode mode = ServerMode.STANDALONE;
        private int tcpPort = ServerConstants.DEFAULT_TCP_PORT;
        private int wsPort = ServerConstants.DEFAULT_WS_PORT;
        private boolean tlsEnabled = false;
        private Path storageDir = Paths.get("data");
        private String nodeId = "node1";
        private List<String> clusterPeers = new ArrayList<>();
        private int authThreads = 4;
        private int attachmentThreads = 4;
        private int reactorWorkers = Math.max(2, Runtime.getRuntime().availableProcessors());
        private int deliveryLanes = 32;

        public Builder mode(ServerMode mode) {
            this.mode = Objects.requireNonNull(mode);
            return this;
        }

        public Builder tcpPort(int port) {
            this.tcpPort = port;
            return this;
        }

        public Builder wsPort(int port) {
            this.wsPort = port;
            return this;
        }

        public Builder tlsEnabled(boolean tls) {
            this.tlsEnabled = tls;
            return this;
        }

        public Builder storageDir(Path dir) {
            this.storageDir = Objects.requireNonNull(dir);
            return this;
        }

        public Builder nodeId(String id) {
            this.nodeId = Objects.requireNonNull(id);
            return this;
        }

        public Builder clusterPeers(List<String> peers) {
            this.clusterPeers = new ArrayList<>(peers);
            return this;
        }

        public Builder authThreads(int threads) {
            this.authThreads = threads;
            return this;
        }

        public Builder attachmentThreads(int threads) {
            this.attachmentThreads = threads;
            return this;
        }

        public Builder reactorWorkers(int workers) {
            this.reactorWorkers = workers;
            return this;
        }

        public Builder deliveryLanes(int lanes) {
            this.deliveryLanes = lanes;
            return this;
        }

        public VibeServerConfig build() {
            return new VibeServerConfig(this);
        }
    }
}
