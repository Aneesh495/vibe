package com.vibe.transport.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import java.io.Closeable;

/**
 * High-performance Java NIO TCP Server Transport.
 *
 * <p>Orchestrates the Acceptor reactor and N Selector worker reactors.
 */
public final class NioServerTransport implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(NioServerTransport.class);

    private final String host;
    private final int port;
    private final int workerCount;
    private final FrameHandler frameHandler;
    private final ConnectionListener connectionListener;
    private final SSLContext sslContext;
    private final Executor offloadExecutor;
    private final TransportMetrics metrics = new TransportMetrics();

    private final List<NioReactor> workerReactors = new ArrayList<>();
    private NioAcceptor acceptor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NioServerTransport(
            String host,
            int port,
            int workerCount,
            FrameHandler frameHandler,
            ConnectionListener connectionListener,
            SSLContext sslContext,
            Executor offloadExecutor
    ) {
        this.host = host != null ? host : "0.0.0.0";
        this.port = port;
        this.workerCount = workerCount > 0 ? workerCount : Math.max(2, Runtime.getRuntime().availableProcessors());
        this.frameHandler = Objects.requireNonNull(frameHandler, "FrameHandler must not be null");
        this.connectionListener = connectionListener;
        this.sslContext = sslContext;
        this.offloadExecutor = offloadExecutor;
    }

    public synchronized void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            log.info("Starting NioServerTransport with {} worker reactors...", workerCount);
            for (int i = 0; i < workerCount; i++) {
                NioReactor reactor = new NioReactor("nio-worker-" + i, metrics);
                reactor.start();
                workerReactors.add(reactor);
            }

            acceptor = new NioAcceptor(
                    host, port, workerReactors, frameHandler, connectionListener, sslContext, offloadExecutor
            );
            acceptor.start();
            log.info("NioServerTransport successfully started on {}:{}", host, acceptor.boundPort());
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping NioServerTransport...");
            if (acceptor != null) {
                acceptor.stop();
            }
            for (NioReactor reactor : workerReactors) {
                reactor.stop();
            }
            workerReactors.clear();
            log.info("NioServerTransport stopped");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int boundPort() {
        return acceptor != null ? acceptor.boundPort() : port;
    }

    public TransportMetrics metrics() {
        return metrics;
    }

    @Override
    public void close() {
        stop();
    }
}
