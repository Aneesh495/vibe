package com.vibe.sdk;

import com.vibe.domain.entity.ConversationType;
import com.vibe.protocol.payload.AuthResultPayload;
import com.vibe.protocol.payload.CommandResultPayload;
import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.sdk.client.VibeClient;
import com.vibe.sdk.client.VibeClientListener;
import com.vibe.sdk.config.VibeClientConfig;
import com.vibe.sdk.storage.StoredConversation;
import com.vibe.sdk.storage.StoredMessage;
import com.vibe.server.VibeServer;
import com.vibe.server.VibeServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VibeClientIntegrationTest {

    private VibeServer server;
    private int tcpPort;
    private int wsPort;
    private Path serverDir;

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        this.serverDir = tempDir.resolve("server");
        this.tcpPort = findFreePort();
        this.wsPort = findFreePort();

        VibeServerConfig config = VibeServerConfig.builder()
                .mode(VibeServerConfig.ServerMode.STANDALONE)
                .tcpPort(tcpPort)
                .wsPort(wsPort)
                .tlsEnabled(false)
                .storageDir(serverDir)
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
    void testClientRegistrationLoginAndMessageExchange(@TempDir Path clientDir) throws Exception {
        VibeClientConfig clientConfig = VibeClientConfig.builder()
                .host("127.0.0.1")
                .port(tcpPort)
                .tlsEnabled(false)
                .deviceId("test-dev-1")
                .storageDir(clientDir)
                .build();

        try (VibeClient client = new VibeClient(clientConfig)) {
            BlockingQueue<ConversationEventPayload> receivedEvents = new LinkedBlockingQueue<>();
            client.addListener(new VibeClientListener() {
                @Override
                public void onMessageReceived(ConversationEventPayload event) {
                    receivedEvents.offer(event);
                }
            });

            client.start().get(5, TimeUnit.SECONDS);

            // Register and Login
            AuthResultPayload authRes = client.register(
                    "alice", "pass123", "Alice In Wonderland", "Curiouser", "avatar.png"
            ).get(5, TimeUnit.SECONDS);

            assertThat(authRes.isSuccess()).isTrue();
            assertThat(client.isAuthenticated()).isTrue();
            assertThat(client.authenticatedUserId()).isNotEmpty();

            // Create Conversation
            CommandResultPayload createRes = client.createConversation(
                    ConversationType.GROUP, "Alice Group", List.of(client.authenticatedUserId())
            ).get(5, TimeUnit.SECONDS);

            assertThat(createRes.isSuccess()).isTrue();
            List<StoredConversation> convs = client.storage().getAllConversations();
            assertThat(convs).isNotEmpty();
            UUID convId = convs.get(0).conversationId();

            // Send Message
            CommandResultPayload sendRes = client.sendMessage(
                    convId, "Hello world via Java SDK!", null
            ).get(5, TimeUnit.SECONDS);

            assertThat(sendRes.isSuccess()).isTrue();

            // Event received via real-time fanout
            ConversationEventPayload event = receivedEvents.poll(5, TimeUnit.SECONDS);
            assertThat(event).isNotNull();
            assertThat(event.conversationId()).isEqualTo(convId);
            com.vibe.domain.event.MessageSentEvent mse = com.vibe.domain.event.MessageSentEvent.decode(
                    event.eventId(), event.conversationId(), event.seqNumber(),
                    event.senderUserId(), event.timestamp(), java.nio.ByteBuffer.wrap(event.payload())
            );
            assertThat(mse.content()).isEqualTo("Hello world via Java SDK!");

            // Verify SQLite message storage
            List<StoredMessage> storedMessages = client.storage().getMessages(convId, 10, 0);
            assertThat(storedMessages).isNotEmpty();
            assertThat(storedMessages.get(0).content()).isEqualTo("Hello world via Java SDK!");
        }
    }
}
