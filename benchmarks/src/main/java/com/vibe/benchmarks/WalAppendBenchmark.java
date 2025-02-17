package com.vibe.benchmarks;

import com.vibe.domain.command.SendMessageCommand;
import com.vibe.storage.local.SegmentedWal;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class WalAppendBenchmark {

    private Path tempDir;
    private SegmentedWal wal;
    private SendMessageCommand sendCmd;

    @Setup(Level.Trial)
    public void setupTrial() throws IOException {
        tempDir = Files.createTempDirectory("vibe-wal-bench");
        wal = new SegmentedWal(tempDir, 64 * 1024 * 1024, 2, 500);
        wal.start();

        sendCmd = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-alice", "dev-1", "m1",
                UUID.randomUUID(), UUID.randomUUID(), "Benchmarking WAL group commit fsync throughput", null
        );
    }

    @TearDown(Level.Trial)
    public void teardownTrial() throws IOException {
        if (wal != null) {
            wal.close();
        }
        if (tempDir != null) {
            try (var s = Files.walk(tempDir)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    @Benchmark
    public Long benchWalAppend() throws Exception {
        return wal.append(sendCmd, false).get(5, TimeUnit.SECONDS);
    }
}
