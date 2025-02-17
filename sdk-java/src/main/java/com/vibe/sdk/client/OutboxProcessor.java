package com.vibe.sdk.client;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.DomainCommandCodec;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.CommandPayload;
import com.vibe.protocol.payload.CommandResultPayload;
import com.vibe.sdk.storage.MessageDeliveryStatus;
import com.vibe.sdk.storage.OutboxItem;
import com.vibe.sdk.storage.SqliteClientStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background worker responsible for draining offline outbox items when connectivity is restored.
 */
public final class OutboxProcessor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final SqliteClientStorage storage;
    private final VibeClient client;
    private final ExecutorService drainExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vibe-outbox-drainer");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean draining = new AtomicBoolean(false);

    public OutboxProcessor(SqliteClientStorage storage, VibeClient client) {
        this.storage = storage;
        this.client = client;
    }

    /**
     * Triggers asynchronous draining of any pending items in the offline outbox.
     */
    public void triggerDrain() {
        if (!client.isAuthenticated()) {
            return;
        }

        if (draining.compareAndSet(false, true)) {
            drainExecutor.submit(this::drainLoop);
        }
    }

    private void drainLoop() {
        try {
            while (client.isAuthenticated()) {
                List<OutboxItem> pending = storage.getPendingOutbox();
                if (pending.isEmpty()) {
                    break;
                }

                for (OutboxItem item : pending) {
                    if (!client.isAuthenticated()) {
                        break;
                    }

                    try {
                        CommandPayload cmdPayload = new CommandPayload(
                                item.commandType(),
                                item.clientMessageId(),
                                item.conversationId(),
                                item.payload()
                        );

                        long correlationId = client.nextCorrelationId();
                        Frame frame = Frame.create(FrameType.COMMAND, correlationId, cmdPayload.encode());

                        CompletableFuture<Frame> responseFuture = client.sendRequest(frame, correlationId);
                        Frame resp = responseFuture.get(10, TimeUnit.SECONDS);

                        if (resp.type() == FrameType.COMMAND_RESULT) {
                            CommandResultPayload resPayload = CommandResultPayload.decode(resp.payload().duplicate());
                            if (resPayload.isSuccess()) {
                                storage.removeOutbox(item.clientMessageId());
                                if (item.conversationId() != null) {
                                    // Parse original command to find messageId if applicable
                                    try {
                                        DomainCommand decodedCmd = DomainCommandCodec.decode(ByteBuffer.wrap(item.payload()));
                                        if (decodedCmd instanceof SendMessageCommand sendCmd) {
                                            storage.updateMessageStatus(sendCmd.messageId(), MessageDeliveryStatus.COMMITTED);
                                        }
                                    } catch (Exception e) {
                                        log.debug("Could not decode domain command for message status update: {}", e.getMessage());
                                    }
                                }
                                log.debug("Successfully drained outbox item {}", item.clientMessageId());
                            } else {
                                String errorMsg = new String(resPayload.payload(), StandardCharsets.UTF_8);
                                log.warn("Outbox item {} rejected by server: {}", item.clientMessageId(), errorMsg);
                                storage.removeOutbox(item.clientMessageId());
                            }
                        } else {
                            log.warn("Unexpected response to outbox item {}: {}", item.clientMessageId(), resp.type());
                            storage.incrementOutboxRetry(item.clientMessageId());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to drain outbox item {}: {}", item.clientMessageId(), e.getMessage());
                        try {
                            storage.incrementOutboxRetry(item.clientMessageId());
                        } catch (Exception se) {
                            log.error("Failed to update retry count: {}", se.getMessage());
                        }
                        // Pause before next attempt if network threw
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in outbox drain loop: {}", e.getMessage(), e);
        } finally {
            draining.set(false);
        }
    }

    @Override
    public void close() {
        drainExecutor.shutdownNow();
    }
}
