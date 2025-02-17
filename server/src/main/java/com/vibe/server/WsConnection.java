package com.vibe.server;

import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameEncoder;
import com.vibe.transport.nio.TransportConnection;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter presenting a Netty WebSocket channel as a Vibe {@link TransportConnection}.
 */
public final class WsConnection implements TransportConnection {

    private static final Logger log = LoggerFactory.getLogger(WsConnection.class);
    private static final AtomicLong ID_GEN = new AtomicLong(50_000);

    private final long connectionId = ID_GEN.incrementAndGet();
    private final ChannelHandlerContext ctx;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private volatile String userId;
    private volatile String deviceId;
    private volatile String username;

    public WsConnection(ChannelHandlerContext ctx) {
        this.ctx = Objects.requireNonNull(ctx);
    }

    @Override
    public long connectionId() {
        return connectionId;
    }

    @Override
    public boolean send(Frame frame) {
        if (isClosed() || !ctx.channel().isActive()) {
            return false;
        }

        try {
            ByteBuffer encoded = FrameEncoder.encode(frame);
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);

            ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes)));
            return true;
        } catch (Exception e) {
            log.warn("Error sending WebSocket frame to conn {}: {}", connectionId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isOpen() {
        return !isClosed.get() && ctx.channel().isActive();
    }

    @Override
    public boolean isClosed() {
        return isClosed.get() || !ctx.channel().isActive();
    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            ctx.close();
        }
    }

    @Override
    public String userId() {
        return userId;
    }

    @Override
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String deviceId() {
        return deviceId;
    }

    @Override
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }
}
