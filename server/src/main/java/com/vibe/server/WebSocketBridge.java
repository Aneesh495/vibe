package com.vibe.server;

import com.vibe.delivery.DeliveryMetrics;
import com.vibe.domain.durability.DurabilityAdapter;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameDecoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Netty-based WebSocket bridge and HTTP Operational Inspector server.
 *
 * <p>Serves:
 * <ul>
 *   <li><code>/ws</code>: Binary WebSocket bridge connecting browsers to Vibe binary protocol</li>
 *   <li><code>/api/status</code>: Real-time node status and consensus information</li>
 *   <li><code>/api/metrics</code>: High-frequency delivery throughput, fanout, and backpressure metrics</li>
 *   <li><code>/api/inspector</code>: Deep diagnostic dump for the Operational Inspector UI</li>
 * </ul>
 */
public final class WebSocketBridge implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(WebSocketBridge.class);

    private final int port;
    private final ClientConnectionHandler connectionHandler;
    private final DurabilityAdapter durabilityAdapter;
    private final DeliveryMetrics deliveryMetrics;
    private final long startTime = System.currentTimeMillis();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private final ConcurrentHashMap<ChannelId, WsConnection> activeWsConnections = new ConcurrentHashMap<>();

    public WebSocketBridge(
            int port,
            ClientConnectionHandler connectionHandler,
            DurabilityAdapter durabilityAdapter,
            DeliveryMetrics deliveryMetrics
    ) {
        this.port = port;
        this.connectionHandler = Objects.requireNonNull(connectionHandler);
        this.durabilityAdapter = Objects.requireNonNull(durabilityAdapter);
        this.deliveryMetrics = Objects.requireNonNull(deliveryMetrics);
    }

    public synchronized void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 16 * 1024 * 1024));
                        pipeline.addLast(new BridgeChannelHandler());
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        ChannelFuture future = bootstrap.bind(port).sync();
        this.serverChannel = future.channel();
        log.info("WebSocketBridge and HTTP Inspector bound to port {}", port);
    }

    private class BridgeChannelHandler extends SimpleChannelInboundHandler<Object> {
        private final FrameDecoder decoder = new FrameDecoder();

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            super.channelActive(ctx);
            WsConnection wsConn = new WsConnection(ctx);
            activeWsConnections.put(ctx.channel().id(), wsConn);
            connectionHandler.onConnected(wsConn);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            WsConnection wsConn = activeWsConnections.remove(ctx.channel().id());
            if (wsConn != null) {
                connectionHandler.onClosed(wsConn, null);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest req) {
                handleHttpRequest(ctx, req);
            } else if (msg instanceof BinaryWebSocketFrame binFrame) {
                WsConnection wsConn = activeWsConnections.get(ctx.channel().id());
                if (wsConn != null) {
                    ByteBuf content = binFrame.content();
                    ByteBuffer nioBuffer = content.nioBuffer();
                    decoder.decode(nioBuffer, frame -> connectionHandler.handleFrame(wsConn, frame));
                }
            } else if (msg instanceof CloseWebSocketFrame) {
                ctx.close();
            }
        }

        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
            String uri = req.uri();
            HttpResponseStatus status = HttpResponseStatus.OK;
            String json;

            if (uri.startsWith("/api/status")) {
                json = generateStatusJson();
            } else if (uri.startsWith("/api/metrics")) {
                json = generateMetricsJson();
            } else if (uri.startsWith("/api/inspector")) {
                json = generateInspectorJson();
            } else {
                status = HttpResponseStatus.NOT_FOUND;
                json = "{\"error\":\"Not Found\"}";
            }

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, Unpooled.wrappedBuffer(bytes));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");

            ctx.writeAndFlush(response);
        }
    }

    private String generateStatusJson() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        boolean leader = durabilityAdapter.isLeader();
        long committedIndex = durabilityAdapter.lastCommittedIndex();
        int activeWs = activeWsConnections.size();

        return String.format(
                "{\"status\":\"UP\",\"uptimeMs\":%d,\"isLeader\":%b,\"lastCommittedIndex\":%d,\"activeWebSocketConnections\":%d}",
                uptimeMs, leader, committedIndex, activeWs
        );
    }

    private String generateMetricsJson() {
        DeliveryMetrics.DeliveryMetricsSnapshot snap = deliveryMetrics.snapshot();
        return String.format(
                "{\"totalDispatched\":%d,\"totalDelivered\":%d,\"droppedSignals\":%d,\"slowConsumersDisconnected\":%d,\"activeSessions\":%d,\"activeLanes\":%d}",
                snap.totalDispatched(), snap.totalDelivered(), snap.droppedSignals(), snap.slowConsumersDisconnected(), snap.activeSessions(), snap.activeLanes()
        );
    }

    private String generateInspectorJson() {
        long uptimeMs = System.currentTimeMillis() - startTime;
        int userCount = durabilityAdapter.stateMachine().allUsers().size();
        int convCount = durabilityAdapter.stateMachine().allConversations().size();
        long committedIndex = durabilityAdapter.lastCommittedIndex();
        boolean leader = durabilityAdapter.isLeader();
        DeliveryMetrics.DeliveryMetricsSnapshot metricsSnap = deliveryMetrics.snapshot();

        long freeMem = Runtime.getRuntime().freeMemory();
        long totalMem = Runtime.getRuntime().totalMemory();

        return String.format(
                "{\"node\":{\"uptimeMs\":%d,\"isLeader\":%b,\"committedIndex\":%d,\"freeMemoryBytes\":%d,\"totalMemoryBytes\":%d}," +
                "\"stateMachine\":{\"registeredUsers\":%d,\"totalConversations\":%d}," +
                "\"delivery\":{\"dispatched\":%d,\"delivered\":%d,\"dropped\":%d,\"slowDisconnected\":%d,\"activeSessions\":%d,\"lanes\":%d}," +
                "\"connections\":{\"webSockets\":%d}}",
                uptimeMs, leader, committedIndex, freeMem, totalMem,
                userCount, convCount,
                metricsSnap.totalDispatched(), metricsSnap.totalDelivered(), metricsSnap.droppedSignals(), metricsSnap.slowConsumersDisconnected(), metricsSnap.activeSessions(), metricsSnap.activeLanes(),
                activeWsConnections.size()
        );
    }

    public int port() {
        return port;
    }

    @Override
    public synchronized void close() {
        log.info("Stopping WebSocketBridge...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        activeWsConnections.clear();
    }
}
