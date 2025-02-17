package com.vibe.storage.local;

import com.vibe.domain.state.DomainStateMachine;
import com.vibe.domain.state.SnapshotCodec;
import com.vibe.protocol.CRC32CUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages atomic snapshot creation, checksum verification, and cleanup.
 */
public final class SnapshotManager {

    private static final Logger log = LoggerFactory.getLogger(SnapshotManager.class);
    private static final int SNAPSHOT_MAGIC = 0x56534E50; // "VSNP"

    private final Path snapshotDir;

    public SnapshotManager(Path snapshotDir) {
        this.snapshotDir = Objects.requireNonNull(snapshotDir);
        try {
            Files.createDirectories(snapshotDir);
        } catch (IOException e) {
            throw new StorageException("Failed to create snapshot directory: " + snapshotDir, e);
        }
    }

    /**
     * Atomically publishes a snapshot of the current state machine.
     *
     * @param stateMachine domain state machine
     * @param lastAppliedIndex authoritative log index applied to this snapshot
     * @return Path to the published .snap file
     */
    public synchronized Path createSnapshot(DomainStateMachine stateMachine, long lastAppliedIndex) throws Exception {
        String baseName = String.format("snapshot-%012d", lastAppliedIndex);
        Path tmpFile = snapshotDir.resolve(baseName + ".tmp");
        Path finalFile = snapshotDir.resolve(baseName + ".snap");

        byte[] snapshotBytes = SnapshotCodec.serialize(stateMachine);
        int checksum = CRC32CUtil.compute(snapshotBytes, 0, snapshotBytes.length);

        // Write header (magic 4 + version 4 + lastAppliedIndex 8 + checksum 4 + length 4 = 24 bytes) + body
        ByteBuffer header = ByteBuffer.allocate(24);
        header.putInt(SNAPSHOT_MAGIC);
        header.putInt(1); // Version
        header.putLong(lastAppliedIndex);
        header.putInt(checksum);
        header.putInt(snapshotBytes.length);
        header.flip();

        try (FileChannel ch = FileChannel.open(tmpFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            while (header.hasRemaining()) {
                ch.write(header);
            }
            ByteBuffer body = ByteBuffer.wrap(snapshotBytes);
            while (body.hasRemaining()) {
                ch.write(body);
            }
            ch.force(true);
        }

        // Atomic publish via rename
        Files.move(tmpFile, finalFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        log.info("Atomically published snapshot {} (size={} bytes, checksum=0x{:08X})",
                finalFile.getFileName(), snapshotBytes.length, checksum);

        cleanupOldSnapshots(2);
        return finalFile;
    }

    /**
     * Locates the latest valid snapshot file and loads its domain state.
     */
    public synchronized Optional<SnapshotCodec.DomainSnapshot> loadLatestSnapshot() throws Exception {
        List<Path> snapFiles;
        try (var stream = Files.list(snapshotDir)) {
            snapFiles = stream.filter(p -> p.toString().endsWith(".snap"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
        }

        for (Path snapPath : snapFiles) {
            try {
                SnapshotCodec.DomainSnapshot snap = readAndVerifySnapshot(snapPath);
                log.info("Successfully recovered from snapshot {}", snapPath.getFileName());
                return Optional.of(snap);
            } catch (Exception e) {
                log.warn("Corrupt snapshot file detected at {}; checking earlier snapshots: {}",
                        snapPath.getFileName(), e.getMessage());
            }
        }

        return Optional.empty();
    }

    private SnapshotCodec.DomainSnapshot readAndVerifySnapshot(Path path) throws Exception {
        byte[] fileBytes = Files.readAllBytes(path);
        if (fileBytes.length < 24) {
            throw new StorageCorruptionException("Snapshot file smaller than minimum header size");
        }

        ByteBuffer buf = ByteBuffer.wrap(fileBytes);
        int magic = buf.getInt();
        if (magic != SNAPSHOT_MAGIC) {
            throw new StorageCorruptionException("Invalid snapshot magic: " + magic);
        }

        int version = buf.getInt();
        if (version != 1) {
            throw new StorageCorruptionException("Unsupported snapshot version: " + version);
        }

        long lastAppliedIndex = buf.getLong();
        int expectedChecksum = buf.getInt();
        int bodyLength = buf.getInt();

        if (buf.remaining() != bodyLength) {
            throw new StorageCorruptionException(String.format(
                    "Snapshot payload length mismatch: header says %d bytes, buffer has %d bytes",
                    bodyLength, buf.remaining()));
        }

        int computedChecksum = CRC32CUtil.compute(fileBytes, 24, bodyLength);
        if (expectedChecksum != computedChecksum) {
            throw new StorageCorruptionException(String.format(
                    "Snapshot CRC mismatch: expected 0x%08X, computed 0x%08X", expectedChecksum, computedChecksum));
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes, 24, bodyLength);
        return SnapshotCodec.readSnapshot(bais);
    }

    private void cleanupOldSnapshots(int retainCount) {
        try (var stream = Files.list(snapshotDir)) {
            List<Path> snaps = stream.filter(p -> p.toString().endsWith(".snap"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            if (snaps.size() > retainCount) {
                for (int i = retainCount; i < snaps.size(); i++) {
                    Files.deleteIfExists(snaps.get(i));
                    log.debug("Purged old snapshot {}", snaps.get(i).getFileName());
                }
            }
        } catch (Exception e) {
            log.warn("Error cleaning up old snapshots: {}", e.getMessage());
        }
    }
}
