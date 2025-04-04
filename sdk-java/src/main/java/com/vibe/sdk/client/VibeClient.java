package com.vibe.sdk.client;

import com.vibe.domain.command.*;
import com.vibe.domain.entity.AttachmentInfo;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.event.MessageSentEvent;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.*;
import com.vibe.sdk.config.VibeClientConfig;
import com.vibe.sdk.storage.*;
import com.vibe.transport.nio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32C;

/**
 * Production Java Client SDK for the Vibe Social Network.
 * Handles non-blocking multiplexed transport, TLS, reconnect with exponential backoff,
 * offline outbox queuing, SQLite cache, and real-time event distribution.
 */
public final class VibeClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VibeClient.class);
    public static final int ATTACHMENT_CHUNK_SIZE = 64 * 1024;

    private final VibeClientConfig config;
    private final NioClientTransport transport;
    private final SqliteClientStorage storage;
    private final OutboxProcessor outboxProcessor;

    private final List<VibeClientListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<Long, CompletableFuture<Frame>> pendingRequests = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CompletableFuture<Path>> activeDownloads = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AttachmentProgressListener> downloadListeners = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> downloadBytesReceived = new ConcurrentHashMap<>();

    private final AtomicLong correlationSeq = new AtomicLong(1);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "vibe-client-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile TransportConnection connection;
    private volatile ConnectionState connectionState = ConnectionState.CLOSED;
    private volatile String authenticatedUserId;
    private volatile String authenticatedUsername;
    private volatile String sessionToken;
    private volatile int reconnectAttempts = 0;

    public VibeClient(VibeClientConfig config) throws SQLException, IOException {
        this.config = config;
        this.transport = new NioClientTransport(config.sslContext(), Executors.newVirtualThreadPerTaskExecutor());
        this.storage = new SqliteClientStorage(config.storageDir(), "vibe-client-" + config.deviceId());
        this.outboxProcessor = new OutboxProcessor(storage, this);
    }

    public synchronized CompletableFuture<Void> start() {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            transport.start();
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }

        // Start heartbeat sender
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat, config.heartbeatIntervalMs(), config.heartbeatIntervalMs(), TimeUnit.MILLISECONDS);

        return connect();
    }

    public CompletableFuture<Void> connect() {
        if (!running.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is closed"));
        }

        if (connection != null && connection.isOpen() && connectionState == ConnectionState.ESTABLISHED) {
            return CompletableFuture.completedFuture(null);
        }

        if (!connecting.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> connectFuture = new CompletableFuture<>();

        log.info("Connecting to {}:{} (TLS={})...", config.host(), config.port(), config.tlsEnabled());
        transport.connect(config.host(), config.port(), this::onFrameReceived, new ConnectionListener() {
            @Override
            public void onConnected(TransportConnection conn) {
                connection = conn;
                connecting.set(false);
                reconnectAttempts = 0;
                log.info("TCP connection established with server");
                setConnectionState(ConnectionState.HANDSHAKING_PROTOCOL);
                sendProtocolHandshake();
                connectFuture.complete(null);
            }

            @Override
            public void onStateChanged(TransportConnection conn, ConnectionState oldState, ConnectionState newState) {
                setConnectionState(newState);
            }

            @Override
            public void onClosed(TransportConnection conn, Throwable cause) {
                connection = null;
                connecting.set(false);
                setConnectionState(ConnectionState.CLOSED);
                failPendingRequests(cause != null ? cause : new IOException("Connection closed"));
                if (running.get()) {
                    scheduleReconnect();
                }
            }
        }).exceptionally(ex -> {
            connecting.set(false);
            connectFuture.completeExceptionally(ex);
            if (running.get()) {
                scheduleReconnect();
            }
            return null;
        });

        return connectFuture;
    }

    private void scheduleReconnect() {
        if (!running.get() || connecting.get()) {
            return;
        }

        reconnectAttempts++;
        long delay = (long) Math.min(
                config.reconnectInitialDelayMs() * Math.pow(config.reconnectBackoffMultiplier(), reconnectAttempts - 1),
                config.reconnectMaxDelayMs()
        );

        log.info("Scheduling reconnect attempt #{} in {}ms", reconnectAttempts, delay);
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private void sendProtocolHandshake() {
        if (connection == null || !connection.isOpen()) {
            return;
        }

        long cid = nextCorrelationId();
        HandshakePayload payload = new HandshakePayload(1, config.clientVersion(), config.deviceId(), 0);
        Frame frame = Frame.create(FrameType.HANDSHAKE, cid, payload.encode());
        sendRequest(frame, cid).whenComplete((ack, error) -> {
            if (error == null && ack.type() == FrameType.HANDSHAKE_ACK) {
                log.info("Handshake ACK received from server");
                // If we have an existing session token, try resuming session
                if (sessionToken != null && authenticatedUsername != null) {
                    resumeSession();
                }
            } else if (error != null) {
                log.warn("Handshake error: {}", error.getMessage());
            }
        });
    }

    private void resumeSession() {
        if (sessionToken == null || authenticatedUserId == null || authenticatedUsername == null) {
            return;
        }

        long cid = nextCorrelationId();
        AuthenticatePayload payload = AuthenticatePayload.withToken(authenticatedUsername, sessionToken, config.deviceId());
        Frame frame = Frame.create(FrameType.AUTHENTICATE, cid, payload.encode());
        sendRequest(frame, cid).whenComplete((res, error) -> {
            if (error == null && res.type() == FrameType.AUTH_RESULT) {
                AuthResultPayload result = AuthResultPayload.decode(res.payload().duplicate());
                if (result.isSuccess()) {
                    setConnectionState(ConnectionState.ESTABLISHED);
                    log.info("Successfully resumed authenticated session for user {}", authenticatedUserId);
                    resubscribeConversations();
                    outboxProcessor.triggerDrain();
                } else {
                    log.warn("Session resume rejected: {}", result.errorMessage());
                    sessionToken = null;
                }
            }
        });
    }

    private void resubscribeConversations() {
        try {
            List<StoredConversation> convs = storage.getAllConversations();
            for (StoredConversation conv : convs) {
                SubscribePayload sub = new SubscribePayload(conv.conversationId(), conv.lastSeq(), 100);
                long cid = nextCorrelationId();
                if (connection != null && connection.isOpen()) {
                    connection.send(Frame.create(FrameType.SUBSCRIBE, cid, sub.encode()));
                }
            }
        } catch (Exception e) {
            log.warn("Error re-subscribing to conversations: {}", e.getMessage());
        }
    }

    private void sendHeartbeat() {
        if (connection != null && connection.isOpen()) {
            long cid = nextCorrelationId();
            HeartbeatPayload hb = HeartbeatPayload.ping();
            connection.send(Frame.create(FrameType.HEARTBEAT, cid, hb.encode()));
        }
    }

    // --- Authentication APIs ---

    public CompletableFuture<AuthResultPayload> register(
            String username,
            String password,
            String displayName,
            String bio,
            String avatarUrl
    ) {
        String clientMsgId = "reg-" + UUID.randomUUID();
        String userId = "u-" + UUID.randomUUID();
        RegisterUserCommand cmd = new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userId, config.deviceId(), clientMsgId, username,
                com.vibe.domain.auth.PasswordHasher.hashPassword(password),
                displayName, bio, avatarUrl
        );

        ByteBuffer encoded = cmd.encode();
        byte[] payloadBytes = new byte[encoded.remaining()];
        encoded.get(payloadBytes);

        CommandPayload cmdPayload = new CommandPayload(CommandType.REGISTER_USER, clientMsgId, null, payloadBytes);
        long cid = nextCorrelationId();
        Frame frame = Frame.create(FrameType.COMMAND, cid, cmdPayload.encode());

        return sendRequest(frame, cid).thenCompose(resFrame -> {
            if (resFrame.type() == FrameType.COMMAND_RESULT) {
                CommandResultPayload res = CommandResultPayload.decode(resFrame.payload().duplicate());
                if (res.isSuccess()) {
                    // Auto login after registration
                    return login(username, password);
                } else {
                    String errorMsg = new String(res.payload(), StandardCharsets.UTF_8);
                    return CompletableFuture.completedFuture(AuthResultPayload.failure(res.errorCode(), errorMsg));
                }
            } else {
                return CompletableFuture.failedFuture(new IOException("Unexpected frame type: " + resFrame.type()));
            }
        });
    }

    public CompletableFuture<AuthResultPayload> login(String username, String password) {
        long cid = nextCorrelationId();
        AuthenticatePayload payload = AuthenticatePayload.withPassword(username, password, config.deviceId());
        Frame frame = Frame.create(FrameType.AUTHENTICATE, cid, payload.encode());

        return sendRequest(frame, cid).thenApply(resFrame -> {
            if (resFrame.type() == FrameType.AUTH_RESULT) {
                AuthResultPayload result = AuthResultPayload.decode(resFrame.payload().duplicate());
                if (result.isSuccess()) {
                    this.authenticatedUserId = result.userId();
                    this.authenticatedUsername = username;
                    this.sessionToken = result.token();
                    setConnectionState(ConnectionState.ESTABLISHED);
                    log.info("User {} logged in successfully", username);
                    for (VibeClientListener l : listeners) {
                        l.onAuthenticated(result.userId(), result.token());
                    }
                    resubscribeConversations();
                    outboxProcessor.triggerDrain();
                }
                return result;
            } else {
                throw new IllegalStateException("Unexpected response to AUTH: " + resFrame.type());
            }
        });
    }

    public CompletableFuture<Void> logout() {
        this.authenticatedUserId = null;
        this.authenticatedUsername = null;
        this.sessionToken = null;
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
        return CompletableFuture.completedFuture(null);
    }

    // --- Messaging & Commands ---

    public CompletableFuture<CommandResultPayload> createConversation(
            ConversationType type,
            String title,
            List<String> memberUserIds
    ) {
        ensureAuthenticated();
        UUID convId = UUID.randomUUID();
        String clientMsgId = "c-" + UUID.randomUUID();
        List<String> allMembers = new ArrayList<>(memberUserIds);
        if (!allMembers.contains(authenticatedUserId)) {
            allMembers.add(authenticatedUserId);
        }

        CreateConversationCommand cmd = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                authenticatedUserId, config.deviceId(), clientMsgId,
                convId, type, title, allMembers
        );

        ByteBuffer buf = cmd.encode();
        byte[] payloadBytes = new byte[buf.remaining()];
        buf.get(payloadBytes);

        CommandPayload cmdPayload = new CommandPayload(CommandType.CREATE_CONVERSATION, clientMsgId, convId, payloadBytes);
        long cid = nextCorrelationId();
        Frame frame = Frame.create(FrameType.COMMAND, cid, cmdPayload.encode());

        return sendRequest(frame, cid).thenApply(resFrame -> {
            CommandResultPayload result = CommandResultPayload.decode(resFrame.payload().duplicate());
            if (result.isSuccess()) {
                try {
                    storage.saveConversation(new StoredConversation(
                            convId, type, title, System.currentTimeMillis(), 0, 0
                    ));
                    // Subscribe to events
                    SubscribePayload sub = new SubscribePayload(convId, 0, 100);
                    if (connection != null && connection.isOpen()) {
                        connection.send(Frame.create(FrameType.SUBSCRIBE, nextCorrelationId(), sub.encode()));
                    }
                } catch (Exception e) {
                    log.warn("Failed to cache newly created conversation: {}", e.getMessage());
                }
            }
            return result;
        });
    }

    public CompletableFuture<CommandResultPayload> sendMessage(
            UUID conversationId,
            String content,
            AttachmentInfo attachment
    ) {
        return sendMessage(conversationId, content, attachment, null);
    }

    public CompletableFuture<CommandResultPayload> sendMessage(
            UUID conversationId,
            String content,
            AttachmentInfo attachment,
            UUID replyToMessageId
    ) {
        ensureAuthenticated();
        UUID messageId = UUID.randomUUID();
        String clientMsgId = "m-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        SendMessageCommand cmd = new SendMessageCommand(
                UUID.randomUUID(), now,
                authenticatedUserId, config.deviceId(), clientMsgId,
                messageId, conversationId, content, attachment, replyToMessageId
        );

        ByteBuffer buf = cmd.encode();
        byte[] payloadBytes = new byte[buf.remaining()];
        buf.get(payloadBytes);

        // Store tentatively in local SQLite messages table and offline outbox
        try {
            storage.saveMessage(new StoredMessage(
                    messageId, conversationId, authenticatedUserId, 0,
                    content, now, MessageDeliveryStatus.PENDING_OUTBOX,
                    attachment != null ? attachment.attachmentId() : null,
                    attachment != null ? attachment.fileName() : null,
                    attachment != null ? attachment.sizeBytes() : 0
            ));
            storage.enqueueOutbox(new OutboxItem(
                    clientMsgId, CommandType.SEND_MESSAGE, conversationId, payloadBytes, now, 0
            ));
        } catch (SQLException e) {
            log.error("Failed to persist message to SQLite outbox: {}", e.getMessage(), e);
        }

        // If not connected, outbox processor will drain when connection restores
        if (connection == null || !connection.isOpen()) {
            return CompletableFuture.completedFuture(
                    CommandResultPayload.success(0L, now, messageId.toString().getBytes(StandardCharsets.UTF_8))
            );
        }

        CommandPayload cmdPayload = new CommandPayload(CommandType.SEND_MESSAGE, clientMsgId, conversationId, payloadBytes);
        long cid = nextCorrelationId();
        Frame frame = Frame.create(FrameType.COMMAND, cid, cmdPayload.encode());

        return sendRequest(frame, cid).thenApply(resFrame -> {
            CommandResultPayload result = CommandResultPayload.decode(resFrame.payload().duplicate());
            try {
                if (result.isSuccess()) {
                    storage.removeOutbox(clientMsgId);
                    storage.updateMessageStatus(messageId, MessageDeliveryStatus.COMMITTED);
                } else {
                    storage.updateMessageStatus(messageId, MessageDeliveryStatus.FAILED);
                }
            } catch (SQLException e) {
                log.warn("Failed to update message status in SQLite: {}", e.getMessage());
            }
            return result;
        });
    }

    public CompletableFuture<CommandResultPayload> addReaction(UUID conversationId, UUID messageId, String emoji) {
        ensureAuthenticated();
        String clientMsgId = "r-" + UUID.randomUUID();
        AddReactionCommand cmd = new AddReactionCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                authenticatedUserId, config.deviceId(), clientMsgId,
                conversationId, messageId, emoji
        );

        ByteBuffer buf = cmd.encode();
        byte[] payloadBytes = new byte[buf.remaining()];
        buf.get(payloadBytes);

        CommandPayload cmdPayload = new CommandPayload(CommandType.ADD_REACTION, clientMsgId, conversationId, payloadBytes);
        long cid = nextCorrelationId();
        Frame frame = Frame.create(FrameType.COMMAND, cid, cmdPayload.encode());

        return sendRequest(frame, cid).thenApply(resFrame ->
                CommandResultPayload.decode(resFrame.payload().duplicate())
        );
    }

    public CompletableFuture<Void> sendReadReceipt(UUID conversationId, long readSeq) {
        if (connection == null || !connection.isOpen()) {
            return CompletableFuture.completedFuture(null);
        }
        AcknowledgePayload ack = AcknowledgePayload.read(conversationId, readSeq);
        long cid = nextCorrelationId();
        connection.send(Frame.create(FrameType.ACKNOWLEDGE, cid, ack.encode()));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> sendTyping(UUID conversationId, boolean typing) {
        if (connection == null || !connection.isOpen()) {
            return CompletableFuture.completedFuture(null);
        }
        CancelPayload cancel = new CancelPayload(nextCorrelationId(), typing ? "typing_start" : "typing_stop");
        connection.send(Frame.create(FrameType.CANCEL, nextCorrelationId(), cancel.encode()));
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<CommandResultPayload> addFriend(String targetUserId) {
        ensureAuthenticated();
        String clientMsgId = "f-" + UUID.randomUUID();
        FriendUserCommand cmd = new FriendUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                authenticatedUserId, config.deviceId(), clientMsgId, targetUserId
        );

        ByteBuffer buf = cmd.encode();
        byte[] payloadBytes = new byte[buf.remaining()];
        buf.get(payloadBytes);

        CommandPayload cmdPayload = new CommandPayload(CommandType.FRIEND_USER, clientMsgId, null, payloadBytes);
        long cid = nextCorrelationId();
        Frame frame = Frame.create(FrameType.COMMAND, cid, cmdPayload.encode());

        return sendRequest(frame, cid).thenApply(resFrame ->
                CommandResultPayload.decode(resFrame.payload().duplicate())
        );
    }

    public CompletableFuture<CommandResultPayload> blockUser(String targetUserId) {
        ensureAuthenticated();
        String clientMsgId = "b-" + UUID.randomUUID();
        BlockUserCommand cmd = new BlockUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                authenticatedUserId, config.deviceId(), clientMsgId, targetUserId
        );

        ByteBuffer buf = cmd.encode();
        byte[] payloadBytes = new byte[buf.remaining()];
        buf.get(payloadBytes);

        CommandPayload cmdPayload = new CommandPayload(CommandType.BLOCK_USER, clientMsgId, null, payloadBytes);
        long cid = nextCorrelationId();
        Frame frame = Frame.create(FrameType.COMMAND, cid, cmdPayload.encode());

        return sendRequest(frame, cid).thenApply(resFrame ->
                CommandResultPayload.decode(resFrame.payload().duplicate())
        );
    }

    // --- Attachment Pipeline ---

    public CompletableFuture<AttachmentInfo> uploadAttachment(
            Path filePath,
            String mimeType,
            AttachmentProgressListener progressListener
    ) {
        ensureAuthenticated();
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!Files.exists(filePath)) {
                    throw new IOException("File does not exist: " + filePath);
                }

                long totalBytes = Files.size(filePath);
                String filename = filePath.getFileName().toString();
                UUID attachmentId = UUID.randomUUID();

                CRC32C overallCrc = new CRC32C();
                byte[] buffer = new byte[ATTACHMENT_CHUNK_SIZE];
                int totalChunks = (int) Math.ceil((double) totalBytes / ATTACHMENT_CHUNK_SIZE);
                if (totalChunks == 0) totalChunks = 1;

                long bytesSent = 0;
                try (InputStream in = Files.newInputStream(filePath)) {
                    int chunkIndex = 0;
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        byte[] chunkData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunkData, 0, bytesRead);
                        overallCrc.update(chunkData, 0, bytesRead);

                        CRC32C chunkCrc = new CRC32C();
                        chunkCrc.update(chunkData, 0, bytesRead);

                        AttachmentChunkPayload payload = new AttachmentChunkPayload(
                                attachmentId, chunkIndex, totalChunks, (int) chunkCrc.getValue(), chunkData
                        );

                        long cid = nextCorrelationId();
                        Frame frame = Frame.create(FrameType.ATTACHMENT_CHUNK, cid, payload.encode());
                        connection.send(frame);

                        bytesSent += bytesRead;
                        chunkIndex++;

                        if (progressListener != null) {
                            double percent = (double) bytesSent / totalBytes * 100.0;
                            progressListener.onProgress(attachmentId, bytesSent, totalBytes, percent);
                        }
                    }
                }

                if (progressListener != null) {
                    progressListener.onComplete(attachmentId);
                }

                return new AttachmentInfo(attachmentId, filename, mimeType, totalBytes, (int) overallCrc.getValue(), true);
            } catch (Exception e) {
                if (progressListener != null) {
                    progressListener.onError(null, e);
                }
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Path> downloadAttachment(
            UUID attachmentId,
            Path destinationFile,
            AttachmentProgressListener progressListener
    ) {
        ensureAuthenticated();
        CompletableFuture<Path> future = new CompletableFuture<>();
        activeDownloads.put(attachmentId, future);
        if (progressListener != null) {
            downloadListeners.put(attachmentId, progressListener);
        }
        downloadBytesReceived.put(attachmentId, 0L);

        long cid = nextCorrelationId();
        AttachmentChunkPayload reqPayload = new AttachmentChunkPayload(attachmentId, 0, 0, 0, new byte[0]);
        Frame frame = Frame.create(FrameType.ATTACHMENT_CHUNK, cid, reqPayload.encode());
        connection.send(frame);

        return future;
    }

    // --- Internal Frame Dispatcher ---

    private void onFrameReceived(TransportConnection conn, Frame frame) {
        CompletableFuture<Frame> pending = pendingRequests.remove(frame.correlationId());
        if (pending != null) {
            pending.complete(frame);
        }

        switch (frame.type()) {
            case CONVERSATION_EVENT -> handleConversationEvent(frame);
            case ATTACHMENT_CHUNK -> handleAttachmentChunk(frame);
            case HEARTBEAT -> log.trace("Received server heartbeat");
            case ERROR -> {
                ErrorPayload err = ErrorPayload.decode(frame.payload().duplicate());
                log.warn("Server sent error frame: {} - {}", err.errorCode(), err.message());
                for (VibeClientListener l : listeners) {
                    l.onError(err.resolveErrorCode().name(), err.message());
                }
            }
            default -> log.trace("Received frame of type {}", frame.type());
        }
    }

    private void handleConversationEvent(Frame frame) {
        try {
            ConversationEventPayload event = ConversationEventPayload.decode(frame.payload().duplicate());

            // If message sent event, decode clean text content and attachment metadata
            if (event.eventType() == ConversationEventPayload.EVENT_MESSAGE_SENT) {
                try {
                    MessageSentEvent mse = MessageSentEvent.decode(
                            event.eventId(), event.conversationId(), event.seqNumber(),
                            event.senderUserId(), event.timestamp(), ByteBuffer.wrap(event.payload())
                    );
                    UUID attId = mse.attachment() != null ? mse.attachment().attachmentId() : null;
                    String attName = mse.attachment() != null ? mse.attachment().fileName() : null;
                    long attSize = mse.attachment() != null ? mse.attachment().sizeBytes() : 0L;

                    storage.saveMessage(new StoredMessage(
                            mse.messageId(),
                            event.conversationId(),
                            event.senderUserId(),
                            event.seqNumber(),
                            mse.content(),
                            event.timestamp(),
                            MessageDeliveryStatus.DELIVERED,
                            attId, attName, attSize
                    ));
                    storage.updateConversationLastSeq(event.conversationId(), event.seqNumber());
                } catch (Exception e) {
                    log.warn("Failed to decode MessageSentEvent payload: {}", e.getMessage());
                }
            }

            // Send delivery acknowledgment back to server
            if (connection != null && connection.isOpen()) {
                AcknowledgePayload ack = AcknowledgePayload.delivered(event.conversationId(), event.seqNumber());
                connection.send(Frame.create(FrameType.ACKNOWLEDGE, nextCorrelationId(), ack.encode()));
            }

            for (VibeClientListener l : listeners) {
                l.onMessageReceived(event);
            }
        } catch (Exception e) {
            log.error("Error processing conversation event: {}", e.getMessage(), e);
        }
    }

    private void handleAttachmentChunk(Frame frame) {
        try {
            AttachmentChunkPayload chunk = AttachmentChunkPayload.decode(frame.payload().duplicate());
            UUID attId = chunk.attachmentId();
            CompletableFuture<Path> future = activeDownloads.get(attId);
            if (future == null) {
                return;
            }

            Path tempTarget = config.storageDir().resolve("att-" + attId + ".tmp");
            Files.createDirectories(config.storageDir());

            try (FileChannel fc = FileChannel.open(tempTarget,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                long offset = (long) chunk.chunkIndex() * ATTACHMENT_CHUNK_SIZE;
                fc.write(ByteBuffer.wrap(chunk.chunkData()), offset);
            }

            long totalBytes = downloadBytesReceived.compute(attId, (k, current) -> (current != null ? current : 0L) + chunk.chunkData().length);
            AttachmentProgressListener l = downloadListeners.get(attId);
            if (l != null) {
                double percent = chunk.totalChunks() > 0 ? (double) (chunk.chunkIndex() + 1) / chunk.totalChunks() * 100.0 : 100.0;
                l.onProgress(attId, totalBytes, 0, percent);
            }

            if (chunk.chunkIndex() >= chunk.totalChunks() - 1) {
                Path finalPath = config.storageDir().resolve("att-" + attId + ".bin");
                Files.move(tempTarget, finalPath);
                if (l != null) {
                    l.onComplete(attId);
                }
                activeDownloads.remove(attId);
                downloadListeners.remove(attId);
                downloadBytesReceived.remove(attId);
                future.complete(finalPath);
            }
        } catch (Exception e) {
            log.error("Failed to handle attachment chunk: {}", e.getMessage(), e);
        }
    }

    public CompletableFuture<Frame> sendRequest(Frame frame, long correlationId) {
        CompletableFuture<Frame> future = new CompletableFuture<>();
        if (connection == null || !connection.isOpen()) {
            future.completeExceptionally(new IOException("Not connected to server"));
            return future;
        }

        pendingRequests.put(correlationId, future);

        // Schedule timeout
        scheduler.schedule(() -> {
            CompletableFuture<Frame> timedOut = pendingRequests.remove(correlationId);
            if (timedOut != null) {
                timedOut.completeExceptionally(new TimeoutException("Request " + correlationId + " timed out"));
            }
        }, config.ackTimeoutMs(), TimeUnit.MILLISECONDS);

        connection.send(frame);
        return future;
    }

    public long nextCorrelationId() {
        return correlationSeq.getAndIncrement();
    }

    private void failPendingRequests(Throwable cause) {
        for (Map.Entry<Long, CompletableFuture<Frame>> entry : pendingRequests.entrySet()) {
            entry.getValue().completeExceptionally(cause);
        }
        pendingRequests.clear();
    }

    private void setConnectionState(ConnectionState state) {
        this.connectionState = state;
        for (VibeClientListener l : listeners) {
            try {
                l.onConnectionStateChanged(state);
            } catch (Exception e) {
                log.warn("Error in listener onConnectionStateChanged: {}", e.getMessage());
            }
        }
    }

    private void ensureAuthenticated() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("Client must be authenticated before performing this action");
        }
    }

    public boolean isConnected() {
        return connection != null && connection.isOpen();
    }

    public boolean isAuthenticated() {
        return authenticatedUserId != null && isConnected() && connectionState == ConnectionState.ESTABLISHED;
    }

    public String authenticatedUserId() {
        return authenticatedUserId;
    }

    public String authenticatedUsername() {
        return authenticatedUsername;
    }

    public SqliteClientStorage storage() {
        return storage;
    }

    public VibeClientConfig config() {
        return config;
    }

    public void addListener(VibeClientListener listener) {
        listeners.add(listener);
    }

    public void removeListener(VibeClientListener listener) {
        listeners.remove(listener);
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdownNow();
            outboxProcessor.close();
            if (connection != null) {
                connection.close();
            }
            transport.close();
            try {
                storage.close();
            } catch (SQLException e) {
                log.warn("Error closing SQLite storage: {}", e.getMessage());
            }
            failPendingRequests(new IOException("Client closed"));
            log.info("VibeClient shut down cleanly");
        }
    }
}
