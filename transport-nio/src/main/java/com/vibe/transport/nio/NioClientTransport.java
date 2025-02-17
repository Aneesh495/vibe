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
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asynchronous Java NIO Client Transport for establishing non-blocking TCP / TLS connections.
 */
public final class NioClientTransport implements Runnable, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NioClientTransport.class);

    private final SSLContext sslContext;
    private final Executor offloadExecutor;
    private final TransportMetrics metrics = new TransportMetrics();
    private final NioReactor workerReactor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Selector connectSelector;
    private Thread connectThread;

    public NioClientTransport() {
        this(null, null);
    }

    public NioClientTransport(SSLContext sslContext, Executor offloadExecutor) {
        this.sslContext = sslContext;
        this.offloadExecutor = offloadExecutor != null ? offloadExecutor : Runnable::run;
        this.workerReactor = new NioReactor("nio-client-worker", metrics);
    }

    public synchronized void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            connectSelector = Selector.open();
            workerReactor.start();

            connectThread = new Thread(this, "nio-client-connector");
            connectThread.setDaemon(true);
            connectThread.start();
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (connectSelector != null) {
                connectSelector.wakeup();
            }
            try {
                if (connectSelector != null) {
                    connectSelector.close();
                }
                if (connectThread != null) {
                    connectThread.join(2000);
                }
            } catch (Exception e) {
                log.warn("Error stopping client connector: {}", e.getMessage());
            }
            workerReactor.stop();
        }
    }

    @Override
    public void close() {
        stop();
    }

    /**
     * Connects to a remote server asynchronously with a frame handler.
     */
    public CompletableFuture<NioConnection> connect(
            String host,
            int port,
            FrameHandler frameHandler
    ) {
        return connect(host, port, frameHandler, null);
    }

    /**
     * Connects to a remote server asynchronously.
     *
     * @param host destination hostname or IP
     * @param port destination TCP port
     * @param frameHandler handler for frames received on this connection
     * @param connectionListener listener for connection state changes
     * @return CompletableFuture completing when the connection and TLS are established
     */
    public CompletableFuture<NioConnection> connect(
            String host,
            int port,
            FrameHandler frameHandler,
            ConnectionListener connectionListener
    ) {
        CompletableFuture<NioConnection> future = new CompletableFuture<>();
        try {
            if (!running.get()) {
                start();
            }

            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

            boolean connectedImmediately = channel.connect(new InetSocketAddress(host, port));

            if (connectedImmediately) {
                completeConnection(channel, host, port, frameHandler, connectionListener, future);
            } else {
                ConnectAttachment attachment = new ConnectAttachment(
                        channel, host, port, frameHandler, connectionListener, future
                );
                channel.register(connectSelector, SelectionKey.OP_CONNECT, attachment);
                connectSelector.wakeup();
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                connectSelector.select(50);

                Iterator<SelectionKey> keyIterator = connectSelector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isConnectable()) {
                        ConnectAttachment attachment = (ConnectAttachment) key.attachment();
                        key.cancel();
                        try {
                            if (attachment.channel.finishConnect()) {
                                completeConnection(
                                        attachment.channel,
                                        attachment.host,
                                        attachment.port,
                                        attachment.frameHandler,
                                        attachment.connectionListener,
                                        attachment.future
                                );
                            } else {
                                attachment.future.completeExceptionally(new IOException("finishConnect returned false"));
                            }
                        } catch (Exception e) {
                            attachment.future.completeExceptionally(e);
                            attachment.channel.close();
                        }
                    }
                }
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error in connectSelector loop: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void completeConnection(
            SocketChannel channel,
            String host,
            int port,
            FrameHandler frameHandler,
            ConnectionListener listener,
            CompletableFuture<NioConnection> future
    ) {
        final SslEngineHandler sslHandler;
        if (sslContext != null) {
            SSLEngine engine = sslContext.createSSLEngine(host, port);
            engine.setUseClientMode(true);
            sslHandler = new SslEngineHandler(engine, offloadExecutor);
        } else {
            sslHandler = null;
        }

        ConnectionListener wrapperListener = new ConnectionListener() {
            @Override
            public void onConnected(TransportConnection connection) {
                if (sslHandler == null && connection instanceof NioConnection nioConn) {
                    future.complete(nioConn);
                }
                if (listener != null) {
                    listener.onConnected(connection);
                }
            }

            @Override
            public void onStateChanged(TransportConnection connection, ConnectionState oldState, ConnectionState newState) {
                if (sslHandler != null && connection instanceof NioConnection nioConn && (newState == ConnectionState.HANDSHAKING_PROTOCOL || newState == ConnectionState.ESTABLISHED)) {
                    future.complete(nioConn);
                }
                if (listener != null) {
                    listener.onStateChanged(connection, oldState, newState);
                }
            }

            @Override
            public void onClosed(TransportConnection connection, Throwable cause) {
                if (!future.isDone()) {
                    future.completeExceptionally(cause != null ? cause : new IOException("Connection closed before establishment"));
                }
                if (listener != null) {
                    listener.onClosed(connection, cause);
                }
            }
        };

        workerReactor.registerChannel(channel, frameHandler, wrapperListener, sslHandler);
    }

    public TransportMetrics metrics() {
        return metrics;
    }

    private record ConnectAttachment(
            SocketChannel channel,
            String host,
            int port,
            FrameHandler frameHandler,
            ConnectionListener connectionListener,
            CompletableFuture<NioConnection> future
    ) {
    }
}
