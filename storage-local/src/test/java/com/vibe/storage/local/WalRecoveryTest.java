package com.vibe.storage.local;

import com.vibe.domain.command.RegisterUserCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalRecoveryTest {

    @Test
    void testTornTailTruncationAndRecovery(@TempDir Path tempDir) throws Exception {
        SegmentedWal wal = new SegmentedWal(tempDir, 10_000L, 1L, 10);
        wal.start();

        // Write 3 complete records
        for (int i = 1; i <= 3; i++) {
            RegisterUserCommand cmd = new RegisterUserCommand(
                    UUID.randomUUID(), System.currentTimeMillis(), "u-" + i, "d1", "c-" + i,
                    "user" + i, "hash", "User " + i, "", ""
            );
            wal.append(cmd, true).get(5, TimeUnit.SECONDS);
        }

        Path activeSegPath = wal.activeSegment().filePath();
        long validSize = Files.size(activeSegPath);
        wal.close();

        // Simulate a power failure / torn tail by appending garbage bytes at EOF
        byte[] tornBytes = new byte[]{(byte) 0xAA, (byte) 0x55, 0x01, 0x01, 0x00, 0x00};
        Files.write(activeSegPath, tornBytes, StandardOpenOption.APPEND);
        assertThat(Files.size(activeSegPath)).isGreaterThan(validSize);

        // Reopen WAL: recovery must detect torn tail, truncate back to validSize, and recover 3 records!
        SegmentedWal recoveredWal = new SegmentedWal(tempDir, 10_000L, 1L, 10);
        recoveredWal.start();

        assertThat(Files.size(activeSegPath)).isEqualTo(validSize);
        assertThat(recoveredWal.nextLogIndex()).isEqualTo(4L);
        recoveredWal.close();
    }

    @Test
    void testCorruptionInMiddleFailsLoudly(@TempDir Path tempDir) throws Exception {
        SegmentedWal wal = new SegmentedWal(tempDir, 10_000L, 1L, 10);
        wal.start();

        for (int i = 1; i <= 3; i++) {
            RegisterUserCommand cmd = new RegisterUserCommand(
                    UUID.randomUUID(), System.currentTimeMillis(), "u-" + i, "d1", "c-" + i,
                    "user" + i, "hash", "User " + i, "", ""
            );
            wal.append(cmd, true).get(5, TimeUnit.SECONDS);
        }

        Path activeSegPath = wal.activeSegment().filePath();
        wal.close();

        // Corrupt a byte in the first record (offset 70, right inside first record)
        byte[] bytes = Files.readAllBytes(activeSegPath);
        bytes[70] = (byte) (bytes[70] ^ 0xFF); // flip bits in committed history
        Files.write(activeSegPath, bytes, StandardOpenOption.WRITE);

        // Reopening WAL MUST fail loudly with StorageCorruptionException!
        SegmentedWal corruptWal = new SegmentedWal(tempDir, 10_000L, 1L, 10);
        assertThatThrownBy(corruptWal::start)
                .isInstanceOf(StorageCorruptionException.class);
    }
}
