package com.vibe.storage.local;

import com.vibe.domain.command.RegisterUserCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentedWalTest {

    @TempDir
    Path tempDir;

    private SegmentedWal wal;

    @BeforeEach
    void setUp() throws Exception {
        // Small segment size (512 bytes) to force quick segment rolling
        wal = new SegmentedWal(tempDir, 512L, 2L, 50);
        wal.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (wal != null) {
            wal.close();
        }
    }

    @Test
    void testAppendAndSegmentRolling() throws Exception {
        int recordCount = 20;
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {
            RegisterUserCommand cmd = new RegisterUserCommand(
                    UUID.randomUUID(), System.currentTimeMillis(), "u-" + i, "d1", "c-" + i,
                    "user" + i, "hash" + i, "User " + i, "bio " + i, "avatar.png"
            );
            futures.add(wal.append(cmd, false));
        }

        // Wait for all group commit futures to complete
        for (int i = 0; i < recordCount; i++) {
            Long committedIdx = futures.get(i).get(5, TimeUnit.SECONDS);
            assertThat(committedIdx).isEqualTo((long) (i + 1));
        }

        assertThat(wal.nextLogIndex()).isEqualTo(recordCount + 1);
        // Because segment size limit was 512 bytes, multiple segments must have been created!
        assertThat(wal.segments().size()).isGreaterThan(1);
    }
}
