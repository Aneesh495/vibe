package com.vibe.benchmarks;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.storage.local.LocalDurabilityAdapter;
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
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class LocalDurabilityBenchmark {

    private Path tempDir;
    private DomainStateMachine stateMachine;
    private LocalDurabilityAdapter adapter;
    private UUID convId;
    private final AtomicLong counter = new AtomicLong(0);

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        tempDir = Files.createTempDirectory("vibe-durability-bench");
        stateMachine = new DomainStateMachine();
        adapter = new LocalDurabilityAdapter(tempDir, stateMachine, 64 * 1024 * 1024L, 5L, 200);
        adapter.start();

        // Setup base user and conversation
        RegisterUserCommand reg = new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "bench-user", "dev-1", "reg-1", "benchuser", "hash", "Bench User", "", ""
        );
        adapter.executeCommand(reg, true).get(5, TimeUnit.SECONDS);

        convId = UUID.randomUUID();
        CreateConversationCommand cc = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "bench-user", "dev-1", "cc-1", convId, ConversationType.GROUP, "Bench Conv", List.of("bench-user")
        );
        adapter.executeCommand(cc, true).get(5, TimeUnit.SECONDS);
    }

    @TearDown(Level.Trial)
    public void teardownTrial() throws IOException {
        if (adapter != null) {
            adapter.close();
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
    public CommandExecutionResult benchDurableMessageExecution() throws Exception {
        long id = counter.incrementAndGet();
        SendMessageCommand cmd = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                "bench-user", "dev-1", "bench-msg-" + id,
                UUID.randomUUID(), convId, "Benchmarking durable message pipeline with group commit", null
        );
        return adapter.executeCommand(cmd, true).get(5, TimeUnit.SECONDS);
    }
}
