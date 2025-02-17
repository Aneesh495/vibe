package com.vibe.transport.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated Acceptor reactor for accepting new client TCP socket channels and
 * distributing them across worker reactors.
 */
public final class NioAcceptor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(NioAcceptor.class);

    private final String host;
    private final int port;
    private final List<NioReactor> workerReactors;
    private final FrameHandler frameHandler;
    private final ConnectionListener connectionListener;
    private final SSLContext sslContext;
    private final Executor offloadExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private Thread thread;
    private int boundPort;

    public NioAcceptor(
            String host,
            int port,
            List<NioReactor> workerReactors,
            FrameHandler frameHandler,
            ConnectionListener connectionListener,
            SSLContext sslContext,
            Executor offloadExecutor
    ) {
        this.host = host != null ? host : "0.0.0.0";
        this.port = port;
        this.workerReactors = Objects.requireNonNull(workerReactors, "Worker reactors must not be null");
        if (workerReactors.isEmpty()) {
            throw new IllegalArgumentException("Worker reactor pool must not be empty");
        }
        this.frameHandler = Objects.requireNonNull(frameHandler, "FrameHandler must not be null");
        this.connectionListener = connectionListener;
        this.sslContext = sslContext;
        this.offloadExecutor = offloadExecutor != null ? offloadExecutor : Runnable::run;
    }

    public synchronized void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            serverChannel.bind(new InetSocketAddress(host, port), 1024);
            boundPort = ((InetSocketAddress) serverChannel.getLocalAddress()).getPort();

            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            thread = new Thread(this, "nio-acceptor-" + boundPort);
            thread.setDaemon(true);
            thread.start();
            log.info("NioAcceptor listening on {}:{} (TLS: {})", host, boundPort, sslContext != null);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (selector != null) {
                selector.wakeup();
            }
            try {
                if (serverChannel != null && serverChannel.isOpen()) {
                    serverChannel.close();
                }
                if (selector != null && selector.isOpen()) {
                    selector.close();
                }
                if (thread != null) {
                    thread.join(2000);
                }
            } catch (Exception e) {
                log.warn("Error stopping NioAcceptor: {}", e.getMessage());
            }
            log.info("NioAcceptor stopped");
        }
    }

    public int boundPort() {
        return boundPort;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                selector.select(100);

                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptConnections();
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error in NioAcceptor loop: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void acceptConnections() {
        try {
            SocketChannel clientChannel;
            while ((clientChannel = serverChannel.accept()) != null) {
                clientChannel.configureBlocking(false);
                clientChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
                clientChannel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

                SslEngineHandler sslHandler = null;
                if (sslContext != null) {
                    SSLEngine engine = sslContext.createSSLEngine();
                    engine.setUseClientMode(false);
                    engine.setNeedClientAuth(false);
                    sslHandler = new SslEngineHandler(engine, offloadExecutor);
                }

                // Distribute round-robin to worker reactors
                int idx = Math.abs(roundRobinIndex.getAndIncrement() % workerReactors.size());
                NioReactor reactor = workerReactors.get(idx);
                reactor.registerChannel(clientChannel, frameHandler, connectionListener, sslHandler);
            }
        } catch (IOException e) {
            log.error("Error accepting incoming socket connection: {}", e.getMessage());
        }
    }
}
