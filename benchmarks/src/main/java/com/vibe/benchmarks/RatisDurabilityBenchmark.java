package com.vibe.benchmarks;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.storage.ratis.RatisCluster;
import com.vibe.storage.ratis.RatisDurabilityAdapter;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class RatisDurabilityBenchmark {

    private Path tempDir;
    private RatisCluster cluster;
    private RatisDurabilityAdapter leader;
    private UUID convId;
    private final AtomicLong counter = new AtomicLong(0);

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        tempDir = Files.createTempDirectory("vibe-ratis-bench");
        cluster = new RatisCluster(tempDir, 3);
        cluster.start();

        leader = cluster.awaitLeader(15, TimeUnit.SECONDS);

        // Pre-create user and conversation
        RegisterUserCommand reg = new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "ratis-user", "dev-1", "reg-1", "ratisuser", "hash", "Ratis User", "", ""
        );
        leader.executeCommand(reg, true).get(10, TimeUnit.SECONDS);

        convId = UUID.randomUUID();
        CreateConversationCommand cc = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "ratis-user", "dev-1", "cc-1", convId, ConversationType.GROUP, "Ratis Conv", List.of("ratis-user")
        );
        leader.executeCommand(cc, true).get(10, TimeUnit.SECONDS);
    }

    @TearDown(Level.Trial)
    public void teardownTrial() throws IOException {
        if (cluster != null) {
            cluster.close();
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
    public CommandExecutionResult benchRatisQuorumCommit() throws Exception {
        long id = counter.incrementAndGet();
        SendMessageCommand cmd = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "ratis-user", "dev-1", "ratis-msg-" + id,
                UUID.randomUUID(), convId, "Benchmarking 3-node Raft consensus quorum commit", null
        );
        return leader.executeCommand(cmd, true).get(10, TimeUnit.SECONDS);
    }
}
