package com.vibe.transport.nio;

import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NioTransportTest {

    private NioServerTransport server;
    private NioClientTransport clientTransport;
    private BlockingQueue<Frame> serverReceivedFrames;
    private BlockingQueue<Frame> clientReceivedFrames;

    @BeforeEach
    void setUp() throws Exception {
        serverReceivedFrames = new LinkedBlockingQueue<>();
        clientReceivedFrames = new LinkedBlockingQueue<>();

        server = new NioServerTransport(
                "127.0.0.1",
                0,
                2,
                (conn, frame) -> {
                    serverReceivedFrames.offer(frame);
                    // Echo back frame with correlationId + 1000
                    conn.send(Frame.create(
                            FrameType.COMMAND_RESULT,
                            frame.correlationId() + 1000,
                            frame.payload()
                    ));
                },
                null,
                null,
                ForkJoinPool.commonPool()
        );
        server.start();

        clientTransport = new NioClientTransport(null, ForkJoinPool.commonPool());
        clientTransport.start();
    }

    @AfterEach
    void tearDown() {
        if (clientTransport != null) {
            clientTransport.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void testPlainTcpFrameEcho() throws Exception {
        CompletableFuture<NioConnection> connFuture = clientTransport.connect(
                "127.0.0.1",
                server.boundPort(),
                (conn, frame) -> clientReceivedFrames.offer(frame),
                null
        );

        NioConnection clientConn = connFuture.get(5, TimeUnit.SECONDS);
        assertThat(clientConn).isNotNull();
        assertThat(clientConn.channel().isConnected()).isTrue();

        byte[] payloadData = "Ping from NIO client".getBytes(StandardCharsets.UTF_8);
        Frame request = Frame.create(FrameType.COMMAND, 42L, ByteBuffer.wrap(payloadData));

        clientConn.send(request);

        Frame serverReceived = serverReceivedFrames.poll(5, TimeUnit.SECONDS);
        assertThat(serverReceived).isNotNull();
        assertThat(serverReceived.correlationId()).isEqualTo(42L);
        assertThat(serverReceived.type()).isEqualTo(FrameType.COMMAND);

        Frame clientReceived = clientReceivedFrames.poll(5, TimeUnit.SECONDS);
        assertThat(clientReceived).isNotNull();
        assertThat(clientReceived.correlationId()).isEqualTo(1042L);
        assertThat(clientReceived.type()).isEqualTo(FrameType.COMMAND_RESULT);

        byte[] echoedBytes = new byte[clientReceived.payloadLength()];
        clientReceived.payload().get(echoedBytes);
        assertThat(echoedBytes).isEqualTo(payloadData);
    }

    @Test
    void testConcurrentClientsBurst() throws Exception {
        int clientCount = 5;
        int framesPerClient = 20;
        CountDownLatch latch = new CountDownLatch(clientCount * framesPerClient);
        AtomicInteger totalResponses = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try {
                    NioConnection conn = clientTransport.connect(
                            "127.0.0.1",
                            server.boundPort(),
                            (c, f) -> {
                                totalResponses.incrementAndGet();
                                latch.countDown();
                            },
                            null
                    ).get(5, TimeUnit.SECONDS);

                    for (int j = 0; j < framesPerClient; j++) {
                        long corrId = clientId * 1000L + j;
                        conn.send(Frame.create(
                                FrameType.COMMAND,
                                corrId,
                                ByteBuffer.wrap(("Msg " + j).getBytes(StandardCharsets.UTF_8))
                        ));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(totalResponses.get()).isEqualTo(clientCount * framesPerClient);
        executor.shutdown();
    }
}
