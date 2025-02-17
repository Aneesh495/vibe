package com.vibe.server;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.entity.User;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.*;
import com.vibe.transport.nio.NioClientTransport;
import com.vibe.transport.nio.NioConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VibeServerIntegrationTest {

    private VibeServer server;
    private int tcpPort;
    private int wsPort;
    private Path storageDir;

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.storageDir = tempDir;
        this.tcpPort = findFreePort();
        this.wsPort = findFreePort();

        VibeServerConfig config = VibeServerConfig.builder()
                .mode(VibeServerConfig.ServerMode.STANDALONE)
                .tcpPort(tcpPort)
                .wsPort(wsPort)
                .tlsEnabled(false)
                .storageDir(storageDir)
                .authThreads(2)
                .attachmentThreads(2)
                .reactorWorkers(2)
                .deliveryLanes(4)
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
    void testEndToEndTcpClientSessionAndCommandDispatch() throws Exception {
        // Pre-register user in state machine
        UUID regCmdId = UUID.randomUUID();
        String userId = "alice-user-id";
        RegisterUserCommand regCmd = new RegisterUserCommand(
                regCmdId, System.currentTimeMillis(),
                userId, "dev-1", "m1", "alice",
                com.vibe.domain.auth.PasswordHasher.hashPassword("secret123"),
                "Alice", "Bio", "avatar.png"
        );
        server.durabilityAdapter().executeCommand(regCmd, true).get(5, TimeUnit.SECONDS);

        // Connect TCP client using NioClientTransport
        BlockingQueue<Frame> clientFrames = new LinkedBlockingQueue<>();
        NioClientTransport clientTransport = new NioClientTransport();
        clientTransport.start();

        NioConnection clientConn = clientTransport.connect("127.0.0.1", tcpPort, (conn, frame) -> {
            clientFrames.offer(frame);
        }).get(5, TimeUnit.SECONDS);

        assertThat(clientConn.isOpen()).isTrue();

        // 1. Send HANDSHAKE
        HandshakePayload handshakePayload = new HandshakePayload(1, "vibe-client-test", "dev-test-1", 0);
        clientConn.send(Frame.create(FrameType.HANDSHAKE, 101L, handshakePayload.encode()));

        Frame ackFrame = clientFrames.poll(5, TimeUnit.SECONDS);
        assertThat(ackFrame).isNotNull();
        assertThat(ackFrame.type()).isEqualTo(FrameType.HANDSHAKE_ACK);
        assertThat(ackFrame.correlationId()).isEqualTo(101L);

        // 2. Send AUTHENTICATE
        AuthenticatePayload authPayload = AuthenticatePayload.withPassword("alice", "secret123", "dev-test-1");
        clientConn.send(Frame.create(FrameType.AUTHENTICATE, 102L, authPayload.encode()));

        Frame authResultFrame = clientFrames.poll(5, TimeUnit.SECONDS);
        assertThat(authResultFrame).isNotNull();
        assertThat(authResultFrame.type()).isEqualTo(FrameType.AUTH_RESULT);
        assertThat(authResultFrame.correlationId()).isEqualTo(102L);

        AuthResultPayload authResult = AuthResultPayload.decode(authResultFrame.payload().duplicate());
        assertThat(authResult.isSuccess()).isTrue();
        assertThat(authResult.userId()).isEqualTo(userId);
        assertThat(authResult.token()).isNotEmpty();

        // 3. Send COMMAND: Create Conversation
        UUID convId = UUID.randomUUID();
        CreateConversationCommand createConv = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userId, "dev-test-1", "m2", convId, ConversationType.GROUP, "Test Group", List.of(userId)
        );
        ByteBuffer encodedCreate = createConv.encode();
        byte[] createBytes = new byte[encodedCreate.remaining()];
        encodedCreate.get(createBytes);

        CommandPayload cmdPayload1 = new CommandPayload(CommandType.CREATE_CONVERSATION, "m2", convId, createBytes);
        clientConn.send(Frame.create(FrameType.COMMAND, 103L, cmdPayload1.encode()));

        Frame cmdResFrame1 = clientFrames.poll(5, TimeUnit.SECONDS);
        assertThat(cmdResFrame1).isNotNull();
        assertThat(cmdResFrame1.type()).isEqualTo(FrameType.COMMAND_RESULT);
        assertThat(cmdResFrame1.correlationId()).isEqualTo(103L);

        CommandResultPayload res1 = CommandResultPayload.decode(cmdResFrame1.payload().duplicate());
        assertThat(res1.isSuccess()).isTrue();

        // 4. Send COMMAND: Send Message
        UUID msgId = UUID.randomUUID();
        SendMessageCommand sendMsg = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userId, "dev-test-1", "m3", msgId, convId, "Hello World from NIO client!", null
        );
        ByteBuffer encodedSend = sendMsg.encode();
        byte[] sendBytes = new byte[encodedSend.remaining()];
        encodedSend.get(sendBytes);

        CommandPayload cmdPayload2 = new CommandPayload(CommandType.SEND_MESSAGE, "m3", convId, sendBytes);
        clientConn.send(Frame.create(FrameType.COMMAND, 104L, cmdPayload2.encode()));

        // We receive the command result AND the conversation event fanout!
        boolean receivedCmdRes = false;
        boolean receivedFanout = false;

        for (int i = 0; i < 2; i++) {
            Frame f = clientFrames.poll(5, TimeUnit.SECONDS);
            assertThat(f).isNotNull();
            if (f.type() == FrameType.COMMAND_RESULT) {
                receivedCmdRes = true;
                CommandResultPayload res2 = CommandResultPayload.decode(f.payload().duplicate());
                assertThat(res2.isSuccess()).isTrue();
                assertThat(res2.assignedSeq()).isEqualTo(1L);
            } else if (f.type() == FrameType.CONVERSATION_EVENT) {
                receivedFanout = true;
                ConversationEventPayload eventPayload = ConversationEventPayload.decode(f.payload().duplicate());
                assertThat(eventPayload.seqNumber()).isEqualTo(1L);
                assertThat(eventPayload.senderUserId()).isEqualTo(userId);
            }
        }

        assertThat(receivedCmdRes).isTrue();
        assertThat(receivedFanout).isTrue();

        clientConn.close();
        clientTransport.close();
    }

    @Test
    void testHttpInspectorEndpoints() throws Exception {
        URI statusUri = URI.create("http://127.0.0.1:" + wsPort + "/api/status");
        HttpURLConnection conn = (HttpURLConnection) statusUri.toURL().openConnection();
        conn.setRequestMethod("GET");
        assertThat(conn.getResponseCode()).isEqualTo(200);

        try (InputStream in = conn.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(json).contains("\"status\":\"UP\"");
            assertThat(json).contains("\"isLeader\":true");
        }

        URI inspectorUri = URI.create("http://127.0.0.1:" + wsPort + "/api/inspector");
        HttpURLConnection inspConn = (HttpURLConnection) inspectorUri.toURL().openConnection();
        inspConn.setRequestMethod("GET");
        assertThat(inspConn.getResponseCode()).isEqualTo(200);

        try (InputStream in = inspConn.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(json).contains("\"stateMachine\"");
            assertThat(json).contains("\"delivery\"");
        }
    }

    @Test
    void testLegacyDataImport(@TempDir Path legacyDir) throws Exception {
        // Create mock legacy data files
        Files.writeString(legacyDir.resolve("userInfo.txt"),
                "alice | pw123 | pic.png | Hello world\n" +
                "bob | pw456 | pic2.png | Hey there\n");

        Files.writeString(legacyDir.resolve("friends.txt"),
                "alice | bob\n");

        Files.writeString(legacyDir.resolve("msgs.txt"),
                "alice | bob | Hello Bob!-1#S# ; Hey Alice!-2#R#\n");

        LegacyDataImporter importer = new LegacyDataImporter(server.durabilityAdapter());
        LegacyDataImporter.ImportReport report = importer.importData(legacyDir);

        assertThat(report.usersImported()).isEqualTo(2);
        assertThat(report.friendshipsImported()).isGreaterThanOrEqualTo(1);
        assertThat(report.messagesImported()).isEqualTo(2);

        User alice = server.stateMachine().getUserByUsername("alice");
        assertThat(alice).isNotNull();
        assertThat(alice.bio()).isEqualTo("Hello world");
    }
}
