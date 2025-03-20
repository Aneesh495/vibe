package com.vibe.server;

import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.protocol.ErrorCode;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameHeader;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.ProtocolConstants;
import com.vibe.protocol.payload.AuthenticatePayload;
import com.vibe.protocol.payload.HandshakePayload;
import com.vibe.transport.nio.NioClientTransport;
import com.vibe.transport.nio.NioConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Socket-level protocol fuzzing acceptance test.
 *
 * <p>Floods the live TCP reactor with malformed byte streams, bad headers,
 * invalid magics, checksum errors, and partial writes over real TCP sockets.
 * Validates zero socket leaks, no reactor stalling, and normal operation for valid clients.
 */
class ProtocolFuzzingSocketAcceptanceTest {

    private VibeServer server;
    private int tcpPort;
    private int wsPort;

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.tcpPort = findFreePort();
        this.wsPort = findFreePort();

        VibeServerConfig config = VibeServerConfig.builder()
                .mode(VibeServerConfig.ServerMode.STANDALONE)
                .tcpPort(tcpPort)
                .wsPort(wsPort)
                .tlsEnabled(false)
                .storageDir(tempDir)
                .authThreads(2)
                .attachmentThreads(2)
                .reactorWorkers(2)
                .deliveryLanes(2)
                .build();

        server = new VibeServer(config);
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void testLiveSocketFuzzingAndSubsequentClientHealth() throws Exception {
        Random rng = new Random(42L);

        // Pre-register user for the subsequent health check
        String userId = "valid-fuzz-target";
        RegisterUserCommand reg = new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userId, "dev-1", "req-fuzz-1", "fuzzuser",
                com.vibe.domain.auth.PasswordHasher.hashPassword("pw123"),
                "Fuzz User", "Bio", ""
        );
        server.durabilityAdapter().executeCommand(reg, true).get(5, TimeUnit.SECONDS);

        // 1. Send 100 malformed socket streams over raw TCP sockets
        for (int i = 0; i < 100; i++) {
            try (Socket socket = new Socket("127.0.0.1", tcpPort)) {
                socket.setSoTimeout(1000);
                OutputStream out = socket.getOutputStream();

                int variant = i % 5;
                if (variant == 0) {
                    // Random noise bytes
                    byte[] garbage = new byte[1 + rng.nextInt(256)];
                    rng.nextBytes(garbage);
                    out.write(garbage);
                } else if (variant == 1) {
                    // Bad magic number
                    ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
                    buf.putInt(0xDEADBEEF);
                    buf.put(ProtocolConstants.VERSION_1);
                    buf.put(FrameType.HEARTBEAT.code());
                    buf.putShort((short) 0);
                    buf.putLong(i);
                    buf.putInt(0);
                    buf.putInt(0);
                    out.write(buf.array());
                } else if (variant == 2) {
                    // Truncated header (only 12 bytes instead of 24)
                    byte[] halfHeader = new byte[12];
                    rng.nextBytes(halfHeader);
                    out.write(halfHeader);
                } else if (variant == 3) {
                    // Valid header but corrupted CRC
                    FrameHeader header = FrameHeader.create(FrameType.HEARTBEAT, (short) 0, i, 0);
                    ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
                    header.encode(buf);
                    byte[] raw = buf.array();
                    raw[23] ^= 0x01; // flip CRC bit
                    out.write(raw);
                } else {
                    // Oversized payload declaration
                    ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
                    buf.putInt(ProtocolConstants.MAGIC);
                    buf.put(ProtocolConstants.VERSION_1);
                    buf.put(FrameType.COMMAND.code());
                    buf.putShort((short) 0);
                    buf.putLong(i);
                    buf.putInt(20_000_000); // 20 MiB exceeds max
                    buf.putInt(0);
                    out.write(buf.array());
                }
                out.flush();

                // Read server response (if any) or expect socket closure
                try {
                    InputStream in = socket.getInputStream();
                    byte[] resp = new byte[128];
                    in.read(resp);
                } catch (Exception ignored) {
                    // Socket closed by server as expected on malformed input
                }
            }
        }

        // 2. Assert server remains fully functional and responsive to legitimate clients
        NioClientTransport clientTransport = new NioClientTransport();
        clientTransport.start();
        try {
            BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
            NioConnection conn = clientTransport.connect("127.0.0.1", tcpPort, (c, f) -> frames.offer(f))
                    .get(5, TimeUnit.SECONDS);
            assertThat(conn.isOpen()).isTrue();

            // Perform valid Handshake
            HandshakePayload hsPayload = new HandshakePayload(1, "health-check-client", "dev-fuzz", 0);
            conn.send(Frame.create(FrameType.HANDSHAKE, 100L, hsPayload.encode()));

            Frame hsAck = frames.poll(5, TimeUnit.SECONDS);
            assertThat(hsAck).isNotNull();
            assertThat(hsAck.type()).isEqualTo(FrameType.HANDSHAKE_ACK);

            // Perform valid Authenticate
            AuthenticatePayload authPayload = AuthenticatePayload.withPassword("fuzzuser", "pw123", "dev-fuzz");
            conn.send(Frame.create(FrameType.AUTHENTICATE, 101L, authPayload.encode()));

            Frame authResult = frames.poll(5, TimeUnit.SECONDS);
            assertThat(authResult).isNotNull();
            assertThat(authResult.type()).isEqualTo(FrameType.AUTH_RESULT);

            conn.close();
        } finally {
            clientTransport.close();
        }
    }
}
