package com.vibe.server;

import com.vibe.delivery.DeliveryEngine;
import com.vibe.domain.auth.TokenManager;
import com.vibe.domain.durability.DurabilityAdapter;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.storage.local.LocalDurabilityAdapter;
import com.vibe.storage.ratis.RatisDurabilityAdapter;
import com.vibe.transport.nio.NioServerTransport;
import com.vibe.transport.nio.TlsContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified server lifecycle coordinator.
 *
 * <p>Binds custom Java NIO TLS reactors for native TCP clients and Netty WebSocket bridge
 * for web clients, hooked into the unified DurabilityAdapter (Standalone WAL or Replicated Ratis).
 */
public final class VibeServer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(VibeServer.class);

    private final VibeServerConfig config;
    private final DomainStateMachine stateMachine;
    private final DurabilityAdapter durabilityAdapter;
    private final DeliveryEngine deliveryEngine;
    private final AttachmentManager attachmentManager;
    private final TokenManager tokenManager;
    private final ClientConnectionHandler connectionHandler;

    private NioServerTransport tcpTransport;
    private WebSocketBridge webSocketBridge;
    private volatile boolean isRunning = false;

    public VibeServer(VibeServerConfig config) throws Exception {
        this.config = Objects.requireNonNull(config);
        Files.createDirectories(config.storageDir());

        this.stateMachine = new DomainStateMachine();

        // 1. Initialize durability adapter based on configured mode
        if (config.mode() == VibeServerConfig.ServerMode.STANDALONE) {
            log.info("Initializing Standalone Segmented WAL durability backend...");
            this.durabilityAdapter = new LocalDurabilityAdapter(
                    config.storageDir(),
                    stateMachine,
                    64 * 1024 * 1024L, // 64 MiB segments
                    50L,               // 50ms sync flush
                    1000               // 1000 records batch
            );
        } else {
            log.info("Initializing Replicated Apache Ratis Quorum Commit cluster backend...");
            int raftPort = config.tcpPort() + 100;
            this.durabilityAdapter = RatisDurabilityAdapter.create(
                    config.nodeId(),
                    raftPort,
                    config.storageDir().resolve(config.nodeId()),
                    config.clusterPeers(),
                    stateMachine
            );
        }

        // 2. Initialize domain and network services
        this.deliveryEngine = new DeliveryEngine(stateMachine, config.deliveryLanes());
        this.attachmentManager = new AttachmentManager(config.storageDir(), config.attachmentThreads());
        this.tokenManager = TokenManager.createRandom();

        this.connectionHandler = new ClientConnectionHandler(
                durabilityAdapter,
                deliveryEngine,
                attachmentManager,
                tokenManager,
                config.authThreads()
        );
    }

    public synchronized void start() throws Exception {
        if (isRunning) {
            return;
        }

        log.info("Starting VibeServer [{}]...", config.mode());

        // 1. Start durability adapter (runs WAL recovery or Ratis election)
        durabilityAdapter.start();

        // 2. Start TCP transport (Custom Java NIO reactor)
        SSLContext sslContext = null;
        if (config.tlsEnabled()) {
            log.info("Configuring TLS for TCP NIO reactors...");
            Path certDir = config.storageDir().resolve("certs");
            java.security.KeyStore ks = TlsContextFactory.generateDevKeyStore(certDir, "vibepass");
            sslContext = TlsContextFactory.createSSLContext(ks, "vibepass".toCharArray(), null);
        }

        this.tcpTransport = new NioServerTransport(
                "0.0.0.0",
                config.tcpPort(),
                config.reactorWorkers(),
                connectionHandler,
                connectionHandler,
                sslContext,
                null
        );
        tcpTransport.start();
        log.info("Vibe TCP NIO Reactor listening on port {}", config.tcpPort());

        // 3. Start WebSocket bridge & HTTP Operational Inspector
        this.webSocketBridge = new WebSocketBridge(
                config.wsPort(),
                connectionHandler,
                durabilityAdapter,
                deliveryEngine.metrics()
        );
        webSocketBridge.start();
        log.info("Vibe WebSocket Bridge listening on port {}", config.wsPort());

        isRunning = true;
        log.info("VibeServer [{}] started successfully!", config.mode());
    }

    public VibeServerConfig config() {
        return config;
    }

    public DomainStateMachine stateMachine() {
        return stateMachine;
    }

    public DurabilityAdapter durabilityAdapter() {
        return durabilityAdapter;
    }

    public DeliveryEngine deliveryEngine() {
        return deliveryEngine;
    }

    public AttachmentManager attachmentManager() {
        return attachmentManager;
    }

    public TokenManager tokenManager() {
        return tokenManager;
    }

    public ClientConnectionHandler connectionHandler() {
        return connectionHandler;
    }

    public NioServerTransport tcpTransport() {
        return tcpTransport;
    }

    public WebSocketBridge webSocketBridge() {
        return webSocketBridge;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isRunning) {
            return;
        }
        log.info("Stopping VibeServer...");
        isRunning = false;

        if (tcpTransport != null) {
            try {
                tcpTransport.close();
            } catch (Exception e) {
                log.warn("Error closing TCP transport", e);
            }
        }

        if (webSocketBridge != null) {
            try {
                webSocketBridge.close();
            } catch (Exception e) {
                log.warn("Error closing WebSocket bridge", e);
            }
        }

        connectionHandler.close();
        deliveryEngine.close();
        attachmentManager.close();

        try {
            durabilityAdapter.close();
        } catch (Exception e) {
            log.warn("Error closing durability adapter", e);
        }

        log.info("VibeServer stopped.");
    }
}
