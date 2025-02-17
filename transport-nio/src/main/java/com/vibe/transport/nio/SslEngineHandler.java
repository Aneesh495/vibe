package com.vibe.transport.nio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Non-blocking TLS 1.3 / 1.2 handler wrapping a JSSE SSLEngine for Java NIO.
 *
 * <p>Manages handshake progression, wrap/unwrap state machines, delegated tasks,
 * buffer resizing under bounds, and close_notify sequences.
 */
public final class SslEngineHandler {

    private static final Logger log = LoggerFactory.getLogger(SslEngineHandler.class);
    private static final int MAX_BUFFER_SIZE = 2 * 1024 * 1024; // 2 MiB bound

    private final SSLEngine sslEngine;
    private final Executor taskExecutor;

    private ByteBuffer netIn;
    private ByteBuffer appIn;
    private ByteBuffer netOut;

    private boolean handshakeFinished = false;
    private boolean isClosed = false;

    public SslEngineHandler(SSLEngine sslEngine, Executor taskExecutor) {
        this.sslEngine = Objects.requireNonNull(sslEngine, "SSLEngine must not be null");
        this.taskExecutor = taskExecutor != null ? taskExecutor : Runnable::run;

        SSLSession session = sslEngine.getSession();
        int appBufferSize = Math.max(session.getApplicationBufferSize(), 32 * 1024);
        int netBufferSize = Math.max(session.getPacketBufferSize(), 32 * 1024);

        this.appIn = ByteBuffer.allocate(appBufferSize);
        this.netIn = ByteBuffer.allocate(netBufferSize);
        this.netOut = ByteBuffer.allocate(netBufferSize);
    }

    public SSLEngine sslEngine() {
        return sslEngine;
    }

    public boolean isHandshakeFinished() {
        return handshakeFinished;
    }

    public void beginHandshake() throws SSLException {
        sslEngine.beginHandshake();
    }

    /**
     * Unwraps incoming network bytes into decrypted application bytes.
     *
     * @param plainTextConsumer consumer receiving decrypted application bytes ready for decoding
     * @return true if data was processed or handshake progressed
     */
    public boolean unwrap(SocketChannel channel, ByteBuffer externalNetIn, PlainTextConsumer plainTextConsumer)
            throws IOException {
        if (isClosed) {
            return false;
        }

        // Transfer any external network bytes into netIn
        if (externalNetIn != null && externalNetIn.hasRemaining()) {
            ensureCapacity(netIn, externalNetIn.remaining());
            netIn.put(externalNetIn);
        }

        netIn.flip();
        try {
            while (netIn.hasRemaining()) {
                SSLEngineResult result = sslEngine.unwrap(netIn, appIn);
                SSLEngineResult.Status status = result.getStatus();

                if (status == SSLEngineResult.Status.OK) {
                    processHandshakeStatus(channel, result.getHandshakeStatus());
                } else if (status == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                    // Need more network bytes to decrypt record
                    ensureCapacity(netIn, sslEngine.getSession().getPacketBufferSize());
                    break;
                } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    appIn = resizeBuffer(appIn, sslEngine.getSession().getApplicationBufferSize() * 2);
                } else if (status == SSLEngineResult.Status.CLOSED) {
                    isClosed = true;
                    return false;
                }
            }
        } finally {
            netIn.compact();
        }

        // Deliver any decrypted application bytes
        if (appIn.position() > 0) {
            appIn.flip();
            try {
                plainTextConsumer.accept(appIn);
            } finally {
                appIn.compact();
            }
        }

        return true;
    }

    /**
     * Encrypts outgoing cleartext application bytes and writes them to the socket channel.
     *
     * @param appData cleartext application buffer
     * @param channel destination socket channel
     */
    public synchronized boolean wrap(ByteBuffer appData, SocketChannel channel) throws IOException {
        if (isClosed) {
            return false;
        }

        while (appData.hasRemaining() || sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            netOut.clear();
            SSLEngineResult result = sslEngine.wrap(appData, netOut);
            SSLEngineResult.Status status = result.getStatus();

            if (status == SSLEngineResult.Status.OK) {
                netOut.flip();
                while (netOut.hasRemaining()) {
                    if (channel.write(netOut) == 0) {
                        // Socket buffer full; channel write blocked
                        return false;
                    }
                }
                processHandshakeStatus(channel, result.getHandshakeStatus());
            } else if (status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                netOut = resizeBuffer(netOut, sslEngine.getSession().getPacketBufferSize() * 2);
            } else if (status == SSLEngineResult.Status.CLOSED) {
                isClosed = true;
                return false;
            }

            if (!appData.hasRemaining() && sslEngine.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                break;
            }
        }
        return true;
    }

    /**
     * Advances handshake state transitions and executes delegated cryptographic tasks.
     */
    public void processHandshakeStatus(SocketChannel channel, SSLEngineResult.HandshakeStatus status) throws IOException {
        while (status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                && status != SSLEngineResult.HandshakeStatus.FINISHED) {

            if (status == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                Runnable task;
                while ((task = sslEngine.getDelegatedTask()) != null) {
                    taskExecutor.execute(task);
                }
                status = sslEngine.getHandshakeStatus();
            } else if (status == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                netOut.clear();
                ByteBuffer dummy = ByteBuffer.allocate(0);
                SSLEngineResult res = sslEngine.wrap(dummy, netOut);
                netOut.flip();
                while (netOut.hasRemaining()) {
                    channel.write(netOut);
                }
                status = res.getHandshakeStatus();
            } else if (status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                // Must wait for more incoming bytes from the socket
                break;
            } else {
                break;
            }
        }

        if (status == SSLEngineResult.HandshakeStatus.FINISHED
                || status == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            handshakeFinished = true;
        }
    }

    /**
     * Closes the TLS session gracefully by transmitting a close_notify alert.
     */
    public void close(SocketChannel channel) {
        if (isClosed) {
            return;
        }
        isClosed = true;
        try {
            sslEngine.closeOutbound();
            netOut.clear();
            ByteBuffer dummy = ByteBuffer.allocate(0);
            SSLEngineResult res = sslEngine.wrap(dummy, netOut);
            if (res.getStatus() != SSLEngineResult.Status.CLOSED) {
                netOut.flip();
                while (netOut.hasRemaining()) {
                    channel.write(netOut);
                }
            }
        } catch (Exception e) {
            log.debug("Error while sending TLS close_notify: {}", e.getMessage());
        }
    }

    private ByteBuffer resizeBuffer(ByteBuffer old, int newCapacity) {
        if (newCapacity > MAX_BUFFER_SIZE) {
            throw new IllegalStateException("TLS buffer resize requested " + newCapacity + " exceeding max bound " + MAX_BUFFER_SIZE);
        }
        ByteBuffer newBuffer = ByteBuffer.allocate(Math.max(old.capacity() * 2, newCapacity));
        old.flip();
        newBuffer.put(old);
        return newBuffer;
    }

    private void ensureCapacity(ByteBuffer buffer, int additionalBytes) {
        if (buffer.remaining() < additionalBytes) {
            int needed = buffer.position() + additionalBytes;
            if (needed > buffer.capacity()) {
                if (needed > MAX_BUFFER_SIZE) {
                    throw new IllegalStateException("Buffer overflow: needed " + needed + " exceeding max " + MAX_BUFFER_SIZE);
                }
                ByteBuffer expanded = ByteBuffer.allocate(Math.max(buffer.capacity() * 2, needed));
                buffer.flip();
                expanded.put(buffer);
            }
        }
    }

    @FunctionalInterface
    public interface PlainTextConsumer {
        void accept(ByteBuffer plainText) throws IOException;
    }
}
