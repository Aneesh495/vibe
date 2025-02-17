package com.vibe.transport.nio;

import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameDecoder;
import com.vibe.protocol.FrameEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates an active TCP session managed by an assigned NioReactor.
 *
 * <p>Handles non-blocking reads, write-queue draining without OP_WRITE busy loops,
 * optional TLS framing, and bounded backpressure.
 */
public final class NioConnection implements TransportConnection {

    private static final Logger log = LoggerFactory.getLogger(NioConnection.class);
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    /** Max pending write buffer size before disconnecting slow consumer: 8 MiB */
    public static final int MAX_PENDING_WRITE_BYTES = 8 * 1024 * 1024;

    private final long connectionId;
    private final SocketChannel channel;
    private final NioReactor reactor;
    private final TransportMetrics metrics;
    private final FrameHandler frameHandler;
    private final ConnectionListener connectionListener;
    private final SslEngineHandler sslHandler;

    private final FrameDecoder decoder = new FrameDecoder();
    private final Queue<ByteBuffer> outboundQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingWriteBytes = new AtomicInteger(0);

    private final ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);
    private ByteBuffer activeOutboundBuffer = null;

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(ConnectionState.CONNECTING);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private volatile SelectionKey selectionKey;

    private volatile long lastActivityTime = System.currentTimeMillis();
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

    // Authenticated identity cache
    private volatile String userId;
    private volatile String deviceId;
    private volatile String username;

    public NioConnection(
            SocketChannel channel,
            NioReactor reactor,
            TransportMetrics metrics,
            FrameHandler frameHandler,
            ConnectionListener connectionListener,
            SslEngineHandler sslHandler
    ) {
        this.connectionId = ID_GENERATOR.getAndIncrement();
        this.channel = Objects.requireNonNull(channel, "SocketChannel must not be null");
        this.reactor = Objects.requireNonNull(reactor, "NioReactor must not be null");
        this.metrics = Objects.requireNonNull(metrics, "TransportMetrics must not be null");
        this.frameHandler = Objects.requireNonNull(frameHandler, "FrameHandler must not be null");
        this.connectionListener = connectionListener;
        this.sslHandler = sslHandler;
    }

    public long connectionId() {
        return connectionId;
    }

    public SocketChannel channel() {
        return channel;
    }

    public NioReactor reactor() {
        return reactor;
    }

    public ConnectionState state() {
        return state.get();
    }

    public void setState(ConnectionState newState) {
        ConnectionState oldState = state.getAndSet(newState);
        if (oldState != newState && connectionListener != null) {
            connectionListener.onStateChanged(this, oldState, newState);
        }
    }

    public void setSelectionKey(SelectionKey key) {
        this.selectionKey = key;
    }

    public SelectionKey selectionKey() {
        return selectionKey;
    }

    public String userId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String deviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String username() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public long lastActivityTime() {
        return lastActivityTime;
    }

    public boolean isOpen() {
        return !isClosed.get() && channel.isOpen();
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public void touch() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public SocketAddress getRemoteAddress() {
        try {
            return channel.getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Enqueues a Frame for transmission and schedules write interest on the reactor.
     *
     * @param frame frame to transmit
     * @return true if enqueued; false if dropped due to backpressure
     */
    public boolean send(Frame frame) {
        if (isClosed.get()) {
            return false;
        }

        ByteBuffer buffer = FrameEncoder.encode(frame);
        int frameSize = buffer.remaining();

        // Check backpressure limits
        if (pendingWriteBytes.addAndGet(frameSize) > MAX_PENDING_WRITE_BYTES) {
            pendingWriteBytes.addAndGet(-frameSize);
            metrics.recordBackpressureDrop();
            log.warn("Backpressure limit exceeded for connection {} (user={}); dropping frame {}",
                    connectionId, userId, frame.type());
            // Slow consumer: schedule disconnect with error
            close(new IOException("Slow consumer exceeded outbound buffer quota"));
            return false;
        }

        outboundQueue.offer(buffer);
        metrics.incrementFramesEncoded();
        scheduleWriteInterest();
        return true;
    }

    /**
     * Signals reactor thread to enable OP_WRITE interest if pending writes exist.
     */
    public void scheduleWriteInterest() {
        if (isClosed.get()) {
            return;
        }
        reactor.submitTask(() -> {
            SelectionKey key = selectionKey;
            if (key != null && key.isValid()) {
                int currentOps = key.interestOps();
                if ((currentOps & SelectionKey.OP_WRITE) == 0) {
                    key.interestOps(currentOps | SelectionKey.OP_WRITE);
                }
            }
        });
    }

    /**
     * Executed by the reactor selector thread when OP_READ is ready.
     */
    public void onReadReady() {
        if (isClosed.get()) {
            return;
        }
        touch();
        try {
            readBuffer.clear();
            int bytesRead = channel.read(readBuffer);
            if (bytesRead == -1) {
                // Peer closed channel
                close(null);
                return;
            }
            if (bytesRead == 0) {
                return;
            }

            metrics.addBytesRead(bytesRead);
            readBuffer.flip();

            if (sslHandler != null) {
                sslHandler.unwrap(channel, readBuffer, plainText -> {
                    decoder.decode(plainText, this::dispatchFrame);
                });
                if (sslHandler.isHandshakeFinished() && state.get() == ConnectionState.HANDSHAKING_TLS) {
                    setState(ConnectionState.HANDSHAKING_PROTOCOL);
                }
            } else {
                decoder.decode(readBuffer, this::dispatchFrame);
            }
        } catch (Exception e) {
            log.debug("Read error on connection {}: {}", connectionId, e.getMessage());
            close(e);
        }
    }

    /**
     * Executed by the reactor selector thread when OP_WRITE is ready.
     */
    public void onWriteReady() {
        if (isClosed.get()) {
            return;
        }
        touch();
        try {
            while (true) {
                if (activeOutboundBuffer == null || !activeOutboundBuffer.hasRemaining()) {
                    if (activeOutboundBuffer != null) {
                        pendingWriteBytes.addAndGet(-activeOutboundBuffer.capacity());
                    }
                    activeOutboundBuffer = outboundQueue.poll();
                    if (activeOutboundBuffer == null) {
                        // All pending writes drained! Clear OP_WRITE interest to prevent busy spin
                        SelectionKey key = selectionKey;
                        if (key != null && key.isValid()) {
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                        }
                        break;
                    }
                }

                int before = activeOutboundBuffer.remaining();
                boolean success;
                if (sslHandler != null) {
                    success = sslHandler.wrap(activeOutboundBuffer, channel);
                } else {
                    channel.write(activeOutboundBuffer);
                    success = true;
                }

                int written = before - activeOutboundBuffer.remaining();
                if (written > 0) {
                    metrics.addBytesWritten(written);
                }

                if (!success || activeOutboundBuffer.hasRemaining()) {
                    // Socket output buffer full; remain subscribed to OP_WRITE
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("Write error on connection {}: {}", connectionId, e.getMessage());
            close(e);
        }
    }

    private void dispatchFrame(Frame frame) {
        metrics.incrementFramesDecoded();
        try {
            frameHandler.handleFrame(this, frame);
        } catch (Exception e) {
            log.error("Exception handling frame {} on connection {}", frame.type(), connectionId, e);
        }
    }

    /**
     * Initiates connection closure and releases resources.
     */
    public void close(Throwable cause) {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }

        setState(ConnectionState.CLOSING);

        if (sslHandler != null) {
            sslHandler.close(channel);
        }

        reactor.submitTask(() -> {
            try {
                if (selectionKey != null) {
                    selectionKey.cancel();
                }
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException e) {
                log.trace("Error closing channel {}: {}", connectionId, e.getMessage());
            } finally {
                setState(ConnectionState.CLOSED);
                metrics.onConnectionClosed();
                if (connectionListener != null) {
                    connectionListener.onClosed(this, cause);
                }
            }
        });
    }

    public void close() {
        close(null);
    }
}
