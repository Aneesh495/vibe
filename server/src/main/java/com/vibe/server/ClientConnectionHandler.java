package com.vibe.server;

import com.vibe.delivery.DeliveryEngine;
import com.vibe.delivery.UserSession;
import com.vibe.domain.auth.PasswordHasher;
import com.vibe.domain.auth.TokenManager;
import com.vibe.domain.command.AcknowledgeReceiptCommand;
import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.DomainCommandCodec;
import com.vibe.domain.durability.DurabilityAdapter;
import com.vibe.domain.entity.Conversation;
import com.vibe.domain.entity.User;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.protocol.ErrorCode;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.ProtocolConstants;
import com.vibe.protocol.payload.*;
import com.vibe.transport.nio.ConnectionListener;
import com.vibe.transport.nio.FrameHandler;
import com.vibe.transport.nio.TransportConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles incoming Vibe protocol frames across raw TCP and WebSocket connections.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Version and capabilities negotiation during handshake</li>
 *   <li>Offloading BCrypt password hashing to a bounded worker pool</li>
 *   <li>Stateless cryptographic token issuance and verification</li>
 *   <li>Durable consensus/WAL command dispatching and fanout</li>
 *   <li>Resumable cursors and connection cleanup on disconnect</li>
 * </ul>
 */
public final class ClientConnectionHandler implements FrameHandler, ConnectionListener, Closeable {

    private static final Logger log = LoggerFactory.getLogger(ClientConnectionHandler.class);

    private final DurabilityAdapter durabilityAdapter;
    private final DeliveryEngine deliveryEngine;
    private final AttachmentManager attachmentManager;
    private final TokenManager tokenManager;
    private final ExecutorService authExecutor;
    private final SecureRandom secureRandom = new SecureRandom();

    public ClientConnectionHandler(
            DurabilityAdapter durabilityAdapter,
            DeliveryEngine deliveryEngine,
            AttachmentManager attachmentManager,
            TokenManager tokenManager,
            int authThreads
    ) {
        this.durabilityAdapter = Objects.requireNonNull(durabilityAdapter);
        this.deliveryEngine = Objects.requireNonNull(deliveryEngine);
        this.attachmentManager = Objects.requireNonNull(attachmentManager);
        this.tokenManager = Objects.requireNonNull(tokenManager);

        this.authExecutor = Executors.newFixedThreadPool(authThreads > 0 ? authThreads : 4, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "vibe-auth-worker-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }

    @Override
    public void handleFrame(TransportConnection conn, Frame frame) {
        try {
            switch (frame.type()) {
                case HANDSHAKE -> handleHandshake(conn, frame);
                case AUTHENTICATE -> handleAuthenticate(conn, frame);
                case TOKEN_REFRESH -> handleTokenRefresh(conn, frame);
                case COMMAND -> handleCommand(conn, frame);
                case SUBSCRIBE -> handleSubscribe(conn, frame);
                case ACKNOWLEDGE -> handleAcknowledge(conn, frame);
                case RESUME -> handleResume(conn, frame);
                case HEARTBEAT -> handleHeartbeat(conn, frame);
                case ATTACHMENT_CHUNK -> handleAttachmentChunk(conn, frame);
                case CANCEL -> handleCancel(conn, frame);
                default -> {
                    log.warn("Unsupported frame type {} from conn {}", frame.type(), conn.connectionId());
                    sendError(conn, frame.correlationId(), ErrorCode.PROTOCOL_INVALID_STATE, "Unsupported frame type");
                }
            }
        } catch (Exception e) {
            log.error("Error processing frame {} from conn {}", frame.type(), conn.connectionId(), e);
            sendError(conn, frame.correlationId(), ErrorCode.PROTOCOL_DECODING_FAILURE, e.getMessage());
        }
    }

    private void handleHandshake(TransportConnection conn, Frame frame) {
        HandshakePayload payload = HandshakePayload.decode(frame.payload().duplicate());
        conn.setDeviceId(payload.deviceId());

        byte[] sessionSalt = new byte[16];
        secureRandom.nextBytes(sessionSalt);

        HandshakeAckPayload ack = new HandshakeAckPayload(
                ProtocolConstants.VERSION_1,
                15_000, // 15s heartbeat
                ProtocolConstants.MAX_PAYLOAD_LENGTH,
                System.currentTimeMillis(),
                sessionSalt
        );

        conn.send(Frame.create(FrameType.HANDSHAKE_ACK, frame.correlationId(), ack.encode()));
    }

    private void handleAuthenticate(TransportConnection conn, Frame frame) {
        AuthenticatePayload payload = AuthenticatePayload.decode(frame.payload().duplicate());

        authExecutor.execute(() -> {
            try {
                User user = durabilityAdapter.stateMachine().getUserByUsername(payload.username());
                if (user == null) {
                    AuthResultPayload fail = AuthResultPayload.failure(
                            ErrorCode.AUTH_INVALID_CREDENTIALS.code(), "Invalid username or password");
                    conn.send(Frame.create(FrameType.AUTH_RESULT, frame.correlationId(), fail.encode()));
                    return;
                }

                boolean valid = false;
                if (payload.authMethod() == AuthenticatePayload.METHOD_PASSWORD) {
                    valid = PasswordHasher.verifyPassword(payload.credential(), user.passwordHash());
                } else if (payload.authMethod() == AuthenticatePayload.METHOD_TOKEN) {
                    TokenManager.TokenValidationResult tokenRes = tokenManager.validateToken(payload.credential());
                    valid = tokenRes.isValid() && user.userId().equals(tokenRes.userId());
                }

                if (!valid) {
                    AuthResultPayload fail = AuthResultPayload.failure(
                            ErrorCode.AUTH_INVALID_CREDENTIALS.code(), "Authentication failed");
                    conn.send(Frame.create(FrameType.AUTH_RESULT, frame.correlationId(), fail.encode()));
                    return;
                }

                // Authentication succeeded!
                long ttlMs = 7L * 24 * 3600 * 1000;
                String token = tokenManager.generateToken(user.userId(), payload.deviceId(), ttlMs);
                long expiresAt = System.currentTimeMillis() + ttlMs;

                conn.setUserId(user.userId());
                conn.setUsername(user.username());
                conn.setDeviceId(payload.deviceId());

                UserSession session = new UserSession(user.userId(), payload.deviceId(), conn);
                deliveryEngine.sessionRegistry().registerSession(session);

                AuthResultPayload success = AuthResultPayload.success(user.userId(), token, expiresAt);
                conn.send(Frame.create(FrameType.AUTH_RESULT, frame.correlationId(), success.encode()));
                log.info("User {} authenticated on device {}", user.username(), payload.deviceId());
            } catch (Exception e) {
                log.error("Auth error on conn {}", conn.connectionId(), e);
                AuthResultPayload fail = AuthResultPayload.failure(
                        ErrorCode.UNKNOWN_ERROR.code(), "Internal auth error: " + e.getMessage());
                conn.send(Frame.create(FrameType.AUTH_RESULT, frame.correlationId(), fail.encode()));
            }
        });
    }

    private void handleTokenRefresh(TransportConnection conn, Frame frame) {
        if (conn.userId() == null) {
            sendError(conn, frame.correlationId(), ErrorCode.AUTH_FORBIDDEN, "Not authenticated");
            return;
        }

        long ttlMs = 7L * 24 * 3600 * 1000;
        String token = tokenManager.generateToken(conn.userId(), conn.deviceId(), ttlMs);
        long expiresAt = System.currentTimeMillis() + ttlMs;
        AuthResultPayload result = AuthResultPayload.success(conn.userId(), token, expiresAt);
        conn.send(Frame.create(FrameType.AUTH_RESULT, frame.correlationId(), result.encode()));
    }

    private void handleCommand(TransportConnection conn, Frame frame) {
        CommandPayload payload = CommandPayload.decode(frame.payload().duplicate());
        DomainCommand command = DomainCommandCodec.decode(ByteBuffer.wrap(payload.body()));

        if (command.type() != CommandType.REGISTER_USER) {
            if (conn.userId() == null) {
                sendError(conn, frame.correlationId(), ErrorCode.AUTH_FORBIDDEN, "Authentication required");
                return;
            }

            // Enforce caller security: command callerUserId MUST match authenticated connection userId
            if (!conn.userId().equals(command.callerUserId())) {
                log.warn("Spoofing attempt blocked: conn userId={} tried submitting command with callerUserId={}",
                        conn.userId(), command.callerUserId());
                sendError(conn, frame.correlationId(), ErrorCode.AUTH_FORBIDDEN, "Caller identity mismatch");
                return;
            }
        }

        // Submit for durable commit (local WAL fsync or Raft quorum commit)
        durabilityAdapter.executeCommand(command, true).thenAccept(result -> {
            if (result.isSuccess()) {
                // If this mutation produced a domain event, dispatch serialized fanout!
                if (result.event() != null) {
                    deliveryEngine.dispatchDomainEvent(result.event());
                }

                CommandResultPayload resPayload = CommandResultPayload.success(
                        result.assignedSeq(), System.currentTimeMillis(), result.payload());
                conn.send(Frame.create(FrameType.COMMAND_RESULT, frame.correlationId(), resPayload.encode()));
            } else {
                int errCode = result.errorCode() != null ? result.errorCode().code() : ErrorCode.UNKNOWN_ERROR.code();
                byte[] errBytes = result.errorMessage() != null ? result.errorMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
                CommandResultPayload resPayload = CommandResultPayload.failure(errCode, errBytes);
                conn.send(Frame.create(FrameType.COMMAND_RESULT, frame.correlationId(), resPayload.encode()));
            }
        }).exceptionally(ex -> {
            log.error("Command durable commit failed for conn {}", conn.connectionId(), ex);
            CommandResultPayload resPayload = CommandResultPayload.failure(
                    ErrorCode.DURABILITY_TIMEOUT.code(), ex.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            conn.send(Frame.create(FrameType.COMMAND_RESULT, frame.correlationId(), resPayload.encode()));
            return null;
        });
    }

    private void handleSubscribe(TransportConnection conn, Frame frame) {
        if (conn.userId() == null) {
            sendError(conn, frame.correlationId(), ErrorCode.AUTH_FORBIDDEN, "Not authenticated");
            return;
        }

        SubscribePayload payload = SubscribePayload.decode(frame.payload().duplicate());
        Conversation conv = durabilityAdapter.stateMachine().getConversation(payload.conversationId());
        if (conv == null || !conv.isMember(conn.userId())) {
            sendError(conn, frame.correlationId(), ErrorCode.DOMAIN_NOT_A_MEMBER, "Not a conversation member");
            return;
        }

        UserSession session = deliveryEngine.sessionRegistry().getSessionByConnectionId(conn.connectionId());
        if (session != null && payload.limit() > 0) {
            deliveryEngine.replayConversation(payload.conversationId(), payload.lastKnownSeq() + 1, payload.limit(), session);
        }
    }

    private void handleAcknowledge(TransportConnection conn, Frame frame) {
        if (conn.userId() == null) {
            return;
        }

        AcknowledgePayload payload = AcknowledgePayload.decode(frame.payload().duplicate());
        AcknowledgeReceiptCommand cmd = new AcknowledgeReceiptCommand(
                UUID.randomUUID(), System.currentTimeMillis(), conn.userId(), conn.deviceId(),
                "ack-" + UUID.randomUUID(), payload.conversationId(), payload.ackType(), payload.upToSeq()
        );

        durabilityAdapter.executeCommand(cmd, false);
    }

    private void handleResume(TransportConnection conn, Frame frame) {
        ResumePayload payload = ResumePayload.decode(frame.payload().duplicate());

        authExecutor.execute(() -> {
            try {
                TokenManager.TokenValidationResult res = tokenManager.validateToken(payload.sessionToken());
                if (!res.isValid()) {
                    sendError(conn, frame.correlationId(), ErrorCode.AUTH_TOKEN_INVALID, res.error() != null ? res.error() : "Invalid or expired token");
                    return;
                }

                User user = durabilityAdapter.stateMachine().getUserById(res.userId());
                if (user == null) {
                    sendError(conn, frame.correlationId(), ErrorCode.DOMAIN_USER_NOT_FOUND, "User not found");
                    return;
                }

                conn.setUserId(user.userId());
                conn.setUsername(user.username());
                conn.setDeviceId(payload.deviceId());

                UserSession session = new UserSession(user.userId(), payload.deviceId(), conn);
                deliveryEngine.sessionRegistry().registerSession(session);

                AuthResultPayload success = AuthResultPayload.success(user.userId(), payload.sessionToken(), res.expiresAt());
                conn.send(Frame.create(FrameType.AUTH_RESULT, frame.correlationId(), success.encode()));
                log.info("Session resumed for user {} device {}", user.username(), payload.deviceId());
            } catch (Exception e) {
                log.error("Resume error", e);
                sendError(conn, frame.correlationId(), ErrorCode.UNKNOWN_ERROR, e.getMessage());
            }
        });
    }

    private void handleHeartbeat(TransportConnection conn, Frame frame) {
        conn.send(Frame.create(FrameType.HEARTBEAT, frame.correlationId(), null));
    }

    private void handleAttachmentChunk(TransportConnection conn, Frame frame) {
        AttachmentChunkPayload chunk = AttachmentChunkPayload.decode(frame.payload().duplicate());
        attachmentManager.receiveChunk(chunk);
    }

    private void handleCancel(TransportConnection conn, Frame frame) {
        log.info("Received cancel request on correlationId {} from conn {}",
                frame.correlationId(), conn.connectionId());
    }

    private void sendError(TransportConnection conn, long correlationId, ErrorCode errorCode, String message) {
        ErrorPayload error = ErrorPayload.fromErrorCode(errorCode, message);
        conn.send(Frame.create(FrameType.ERROR, correlationId, error.encode()));
    }

    @Override
    public void onConnected(TransportConnection conn) {
        log.debug("New client connected: connId={}", conn.connectionId());
    }

    @Override
    public void onClosed(TransportConnection conn, Throwable cause) {
        log.debug("Client disconnected: connId={}", conn.connectionId());
        deliveryEngine.sessionRegistry().unregisterSession(conn.connectionId());
    }

    @Override
    public void close() {
        authExecutor.shutdown();
        try {
            if (!authExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                authExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            authExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
