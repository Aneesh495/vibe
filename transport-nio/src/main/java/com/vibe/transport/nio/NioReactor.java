package com.vibe.transport.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reactor thread executing a dedicated NIO Selector loop.
 *
 * <p>Owns a subset of registered channels, processes I/O readiness, and executes
 * tasks submitted via its non-blocking thread-safe mailbox.
 */
public final class NioReactor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(NioReactor.class);
    private static final int SELECT_TIMEOUT_MS = 50;
    private static final int MAX_MAILBOX_SIZE = 10_000;

    private final String name;
    private final Selector selector;
    private final TransportMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Queue<Runnable> taskMailbox = new ConcurrentLinkedQueue<>();
    private final AtomicInteger mailboxSize = new AtomicInteger(0);

    private final Map<Long, NioConnection> registeredConnections = new ConcurrentHashMap<>();
    private Thread thread;

    public NioReactor(String name, TransportMetrics metrics) {
        this.name = Objects.requireNonNull(name, "Reactor name must not be null");
        this.metrics = Objects.requireNonNull(metrics, "TransportMetrics must not be null");
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("Failed to open NIO Selector for reactor " + name, e);
        }
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.setDaemon(true);
            thread.start();
            log.info("Started NIO Reactor [{}]", name);
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            selector.wakeup();
            try {
                if (thread != null) {
                    thread.join(2000);
                }
                selector.close();
            } catch (Exception e) {
                log.warn("Error stopping reactor [{}]: {}", name, e.getMessage());
            }
            log.info("Stopped NIO Reactor [{}]", name);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int connectionCount() {
        return registeredConnections.size();
    }

    /**
     * Submits a task to be executed by this reactor thread during its selector cycle.
     * Prevents lost wakeups by calling selector.wakeup().
     */
    public boolean submitTask(Runnable task) {
        if (!running.get()) {
            return false;
        }

        if (mailboxSize.incrementAndGet() > MAX_MAILBOX_SIZE) {
            mailboxSize.decrementAndGet();
            metrics.recordRejectedTask();
            log.warn("Mailbox full for reactor [{}]; rejected task", name);
            return false;
        }

        taskMailbox.offer(task);
        selector.wakeup();
        return true;
    }

    /**
     * Registers an accepted SocketChannel with this reactor.
     */
    public void registerChannel(
            SocketChannel channel,
            FrameHandler frameHandler,
            ConnectionListener listener,
            SslEngineHandler sslHandler
    ) {
        submitTask(() -> {
            try {
                channel.configureBlocking(false);
                NioConnection connection = new NioConnection(
                        channel, this, metrics, frameHandler, listener, sslHandler
                );

                SelectionKey key = channel.register(selector, SelectionKey.OP_READ, connection);
                connection.setSelectionKey(key);
                registeredConnections.put(connection.connectionId(), connection);
                metrics.onConnectionAccepted();

                if (sslHandler != null) {
                    connection.setState(ConnectionState.HANDSHAKING_TLS);
                    sslHandler.beginHandshake();
                    sslHandler.processHandshakeStatus(channel, sslHandler.sslEngine().getHandshakeStatus());
                } else {
                    connection.setState(ConnectionState.HANDSHAKING_PROTOCOL);
                }

                if (listener != null) {
                    listener.onConnected(connection);
                }
            } catch (Exception e) {
                log.error("Failed to register channel on reactor [{}]", name, e);
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }
        });
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                selector.select(SELECT_TIMEOUT_MS);

                // 1. Process mailbox tasks submitted by external threads
                drainMailbox();

                // 2. Process selected I/O readiness keys
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    NioConnection connection = (NioConnection) key.attachment();
                    if (connection == null) {
                        continue;
                    }

                    if (key.isReadable()) {
                        connection.onReadReady();
                    }

                    if (key.isValid() && key.isWritable()) {
                        connection.onWriteReady();
                    }
                }

                // 3. Clean up closed connections from registry
                registeredConnections.values().removeIf(conn -> conn.state() == ConnectionState.CLOSED);

            } catch (Exception e) {
                if (running.get()) {
                    log.error("Unexpected error in reactor [{}] loop: {}", name, e.getMessage(), e);
                }
            }
        }
    }

    private void drainMailbox() {
        Runnable task;
        while ((task = taskMailbox.poll()) != null) {
            mailboxSize.decrementAndGet();
            try {
                task.run();
            } catch (Throwable t) {
                log.error("Error executing task in reactor [{}]", name, t);
            }
        }
    }
}
