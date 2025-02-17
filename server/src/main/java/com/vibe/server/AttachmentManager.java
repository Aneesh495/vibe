package com.vibe.server;

import com.vibe.protocol.CRC32CUtil;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameType;
import com.vibe.protocol.payload.AttachmentChunkPayload;
import com.vibe.transport.nio.TransportConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles bounded asynchronous chunked attachment uploads, CRC32C validation, and streaming downloads.
 */
public final class AttachmentManager implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(AttachmentManager.class);
    public static final int CHUNK_SIZE = 64 * 1024; // 64 KiB chunks

    private final Path attachmentsDir;
    private final ExecutorService ioExecutor;

    private static class InFlightUpload {
        final UUID attachmentId;
        final int totalChunks;
        final Path targetFile;
        final FileChannel channel;
        final AtomicInteger receivedCount = new AtomicInteger(0);

        InFlightUpload(UUID attachmentId, int totalChunks, Path targetFile, FileChannel channel) {
            this.attachmentId = attachmentId;
            this.totalChunks = totalChunks;
            this.targetFile = targetFile;
            this.channel = channel;
        }
    }

    private final ConcurrentHashMap<UUID, InFlightUpload> activeUploads = new ConcurrentHashMap<>();

    public AttachmentManager(Path storageDir, int threadCount) throws IOException {
        this.attachmentsDir = storageDir.resolve("attachments");
        Files.createDirectories(attachmentsDir);

        this.ioExecutor = Executors.newFixedThreadPool(threadCount > 0 ? threadCount : 4, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "vibe-attachment-io-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * Receives an uploaded chunk asynchronously, writes it to disk at chunk offset,
     * and completes when the full attachment is durably assembled.
     */
    public CompletableFuture<Boolean> receiveChunk(AttachmentChunkPayload chunk) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (!chunk.verifyChecksum()) {
            future.completeExceptionally(new IOException("Attachment chunk CRC32C checksum mismatch"));
            return future;
        }

        ioExecutor.execute(() -> {
            try {
                InFlightUpload upload = activeUploads.computeIfAbsent(chunk.attachmentId(), id -> {
                    try {
                        Path file = attachmentsDir.resolve(id.toString() + ".dat");
                        FileChannel ch = FileChannel.open(file,
                                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
                        return new InFlightUpload(id, chunk.totalChunks(), file, ch);
                    } catch (IOException e) {
                        throw new RuntimeException("Cannot open attachment file for " + id, e);
                    }
                });

                long offset = (long) chunk.chunkIndex() * CHUNK_SIZE;
                ByteBuffer buf = ByteBuffer.wrap(chunk.chunkData());
                while (buf.hasRemaining()) {
                    offset += upload.channel.write(buf, offset);
                }

                int count = upload.receivedCount.incrementAndGet();
                if (count >= upload.totalChunks) {
                    upload.channel.force(true);
                    upload.channel.close();
                    activeUploads.remove(chunk.attachmentId());
                    log.info("Attachment {} upload complete ({} chunks, {} bytes)",
                            chunk.attachmentId(), count, Files.size(upload.targetFile));
                    future.complete(true);
                } else {
                    future.complete(false); // In progress
                }
            } catch (Exception e) {
                log.error("Error writing attachment chunk {} for {}", chunk.chunkIndex(), chunk.attachmentId(), e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Streams an attachment from disk back to a client as sequential ATTACHMENT_CHUNK frames.
     */
    public CompletableFuture<Void> streamAttachmentToClient(UUID attachmentId, TransportConnection connection) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        ioExecutor.execute(() -> {
            Path file = attachmentsDir.resolve(attachmentId.toString() + ".dat");
            if (!Files.exists(file)) {
                future.completeExceptionally(new IOException("Attachment not found: " + attachmentId));
                return;
            }

            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                long fileSize = channel.size();
                int totalChunks = (int) Math.ceil((double) fileSize / CHUNK_SIZE);
                if (totalChunks == 0) totalChunks = 1;

                ByteBuffer buf = ByteBuffer.allocate(CHUNK_SIZE);
                for (int i = 0; i < totalChunks; i++) {
                    buf.clear();
                    int bytesRead = channel.read(buf);
                    buf.flip();

                    byte[] chunkData = new byte[bytesRead > 0 ? bytesRead : 0];
                    if (bytesRead > 0) {
                        buf.get(chunkData);
                    }

                    AttachmentChunkPayload payload = AttachmentChunkPayload.create(attachmentId, i, totalChunks, chunkData);
                    Frame frame = Frame.create(FrameType.ATTACHMENT_CHUNK, 0L, payload.encode());
                    if (!connection.send(frame)) {
                        log.warn("Failed to stream chunk {} of {} to connection {}", i, attachmentId, connection.connectionId());
                        break;
                    }
                }
                future.complete(null);
            } catch (Exception e) {
                log.error("Error streaming attachment {} to connection {}", attachmentId, connection.connectionId(), e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public Path getAttachmentPath(UUID attachmentId) {
        return attachmentsDir.resolve(attachmentId.toString() + ".dat");
    }

    public boolean attachmentExists(UUID attachmentId) {
        return Files.exists(getAttachmentPath(attachmentId));
    }

    @Override
    public void close() {
        log.info("Closing AttachmentManager...");
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (InFlightUpload upload : activeUploads.values()) {
            try {
                upload.channel.close();
            } catch (Exception ignored) {}
        }
        activeUploads.clear();
    }
}
