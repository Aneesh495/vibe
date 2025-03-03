package com.vibe.server.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Content-Addressed Store (CAS) for immutable attachment binary payloads.
 *
 * <p>Durability Contract:
 * <ul>
 *   <li>Attachments are keyed by SHA-256 cryptographic digest of their full content.</li>
 *   <li>Objects are organized in a two-level sharded directory structure: {@code cas/ab/cd/{sha256}.blob}.</li>
 *   <li>In replicated cluster configurations, Raft consensus logs the deterministic attachment reference
 *       (attachment ID, SHA-256 digest, size, mime type), while attachment bytes are stored locally in the CAS.
 *       Replicated metadata does not automatically replicate bulk attachment bytes.</li>
 *   <li>Supports resumable uploads with chunk tracking bitmaps and deduplication of identical payloads.</li>
 * </ul>
 */
public final class ContentAddressedStore implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ContentAddressedStore.class);

    public static final int CHUNK_SIZE = 64 * 1024; // 64 KiB
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MiB limit

    private final Path rootDir;
    private final Path tempDir;
    private final ExecutorService ioExecutor;

    public static class InFlightSession {
        final UUID uploadId;
        final int totalChunks;
        final long expectedSizeBytes;
        final Path tempFile;
        final FileChannel channel;
        final BitSet receivedChunks;
        final MessageDigest sha256Digest;
        final AtomicInteger receivedCount = new AtomicInteger(0);

        InFlightSession(UUID uploadId, int totalChunks, long expectedSizeBytes, Path tempFile, FileChannel channel) {
            this.uploadId = uploadId;
            this.totalChunks = totalChunks;
            this.expectedSizeBytes = expectedSizeBytes;
            this.tempFile = tempFile;
            this.channel = channel;
            this.receivedChunks = new BitSet(totalChunks);
            try {
                this.sha256Digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 not available", e);
            }
        }
    }

    private final ConcurrentHashMap<UUID, InFlightSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> finalizedAttachmentHashes = new ConcurrentHashMap<>();

    public ContentAddressedStore(Path storageDir, int threadCount) throws IOException {
        this.rootDir = storageDir.resolve("cas");
        this.tempDir = storageDir.resolve("cas_tmp");
        Files.createDirectories(rootDir);
        Files.createDirectories(tempDir);

        this.ioExecutor = Executors.newFixedThreadPool(threadCount > 0 ? threadCount : 4, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "vibe-cas-io-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });
    }

    /**
     * Initializes an upload session for an attachment.
     */
    public void startSession(UUID uploadId, int totalChunks, long expectedSizeBytes) throws IOException {
        if (expectedSizeBytes > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 50 MiB limit: " + expectedSizeBytes);
        }
        Path tempFile = tempDir.resolve(uploadId.toString() + ".tmp");
        FileChannel ch = FileChannel.open(tempFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
        activeSessions.put(uploadId, new InFlightSession(uploadId, totalChunks, expectedSizeBytes, tempFile, ch));
    }

    /**
     * Writes a chunk into the in-flight upload session.
     * Returns true if this chunk completes the upload.
     */
    public CompletableFuture<String> writeChunk(UUID uploadId, int chunkIndex, byte[] data) {
        CompletableFuture<String> future = new CompletableFuture<>();

        ioExecutor.execute(() -> {
            try {
                InFlightSession session = activeSessions.get(uploadId);
                if (session == null) {
                    future.completeExceptionally(new IOException("Upload session not found or expired: " + uploadId));
                    return;
                }

                if (chunkIndex < 0 || chunkIndex >= session.totalChunks) {
                    future.completeExceptionally(new IndexOutOfBoundsException("Invalid chunk index: " + chunkIndex));
                    return;
                }

                long offset = (long) chunkIndex * CHUNK_SIZE;
                ByteBuffer buf = ByteBuffer.wrap(data);
                while (buf.hasRemaining()) {
                    offset += session.channel.write(buf, offset);
                }

                synchronized (session.receivedChunks) {
                    if (!session.receivedChunks.get(chunkIndex)) {
                        session.receivedChunks.set(chunkIndex);
                        session.receivedCount.incrementAndGet();
                    }
                }

                if (session.receivedCount.get() >= session.totalChunks) {
                    // Finalize upload, compute SHA-256, move to CAS path
                    session.channel.force(true);
                    session.channel.close();

                    String sha256 = computeFileHash(session.tempFile);
                    Path casTarget = getBlobPath(sha256);
                    Files.createDirectories(casTarget.getParent());

                    if (!Files.exists(casTarget)) {
                        Files.move(session.tempFile, casTarget, StandardCopyOption.ATOMIC_MOVE);
                    } else {
                        Files.deleteIfExists(session.tempFile); // Deduplicated
                    }

                    finalizedAttachmentHashes.put(uploadId, sha256);
                    activeSessions.remove(uploadId);
                    log.info("CAS finalized blob {} for upload {} ({} bytes)", sha256, uploadId, Files.size(casTarget));
                    future.complete(sha256);
                } else {
                    future.complete(null); // In progress
                }
            } catch (Exception e) {
                log.error("Error writing chunk {} for upload {}", chunkIndex, uploadId, e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Returns the bitset of chunks received so far for an upload session.
     */
    public BitSet getReceivedChunks(UUID uploadId) {
        InFlightSession session = activeSessions.get(uploadId);
        if (session == null) {
            return null;
        }
        synchronized (session.receivedChunks) {
            return (BitSet) session.receivedChunks.clone();
        }
    }

    public boolean hasBlob(String sha256) {
        return Files.exists(getBlobPath(sha256));
    }

    public Path getBlobPath(String sha256) {
        if (sha256 == null || sha256.length() < 4) {
            throw new IllegalArgumentException("Invalid SHA-256 hash");
        }
        String p1 = sha256.substring(0, 2);
        String p2 = sha256.substring(2, 4);
        return rootDir.resolve(p1).resolve(p2).resolve(sha256 + ".blob");
    }

    public String getHashForAttachment(UUID attachmentId) {
        return finalizedAttachmentHashes.get(attachmentId);
    }

    private static String computeFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        for (InFlightSession session : activeSessions.values()) {
            try {
                session.channel.close();
            } catch (Exception ignored) {}
        }
        activeSessions.clear();
    }
}
