package com.vibe.benchmarks;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import com.vibe.storage.local.LocalDurabilityAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

/**
 * Standalone load client for durable message throughput and latency measurement.
 *
 * <p>Uses uncoordinated arrival scheduling to avoid coordinated omission:
 * arrivals are scheduled at fixed intervals regardless of prior completion.
 * Measures enqueue-to-commit latency as the wall-clock time from scheduled arrival
 * to acknowledgment of durable commit.
 *
 * <p>Produces latency histograms (p50/p95/p99/max) and throughput measurements
 * for standalone WAL durability mode.
 */
public final class VibeLoadClient {

    private final Path storageDir;
    private final int concurrentClients;
    private final int messagesPerClient;
    private final int warmupMessages;
    private final long targetInterArrivalNanos;

    private DomainStateMachine stateMachine;
    private LocalDurabilityAdapter adapter;

    // Latency tracking (nanoseconds)
    private final ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
    private final AtomicLong committedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);

    public VibeLoadClient(Path storageDir, int concurrentClients, int messagesPerClient,
                          int warmupMessages, double targetRatePerSecond) {
        this.storageDir = storageDir;
        this.concurrentClients = concurrentClients;
        this.messagesPerClient = messagesPerClient;
        this.warmupMessages = warmupMessages;
        this.targetInterArrivalNanos = (long) (1_000_000_000.0 / targetRatePerSecond);
    }

    public LoadReport run() throws Exception {
        Files.createDirectories(storageDir);
        this.stateMachine = new DomainStateMachine();
        this.adapter = new LocalDurabilityAdapter(
                storageDir, stateMachine,
                64 * 1024 * 1024L, // 64 MiB segments
                20L,               // 20ms batch flush
                500                // 500 record batch
        );
        adapter.start();

        try {
            // Pre-register users and create conversations
            List<String> userIds = new ArrayList<>();
            List<UUID> convIds = new ArrayList<>();

            for (int i = 0; i < concurrentClients; i++) {
                String uid = "load-user-" + i;
                userIds.add(uid);
                RegisterUserCommand reg = new RegisterUserCommand(
                        UUID.randomUUID(), System.currentTimeMillis(),
                        uid, "dev-" + i, "reg-" + i, "user" + i,
                        "$2a$10$placeholder", "User " + i, "", ""
                );
                adapter.executeCommand(reg, true).get(5, TimeUnit.SECONDS);
            }

            // Create conversations (groups of 4 users each)
            for (int i = 0; i < Math.max(10, concurrentClients / 4); i++) {
                UUID cid = UUID.randomUUID();
                convIds.add(cid);
                List<String> members = new ArrayList<>();
                for (int j = 0; j < Math.min(4, userIds.size()); j++) {
                    members.add(userIds.get((i * 4 + j) % userIds.size()));
                }
                CreateConversationCommand cc = new CreateConversationCommand(
                        UUID.randomUUID(), System.currentTimeMillis(),
                        members.get(0), "dev-0", "cc-" + i, cid,
                        ConversationType.GROUP, "Load Channel " + i, members
                );
                adapter.executeCommand(cc, true).get(5, TimeUnit.SECONDS);
            }

            // Warmup phase
            Random warmupRng = new Random(42);
            for (int i = 0; i < warmupMessages; i++) {
                String sender = userIds.get(warmupRng.nextInt(userIds.size()));
                UUID convId = convIds.get(warmupRng.nextInt(convIds.size()));
                SendMessageCommand sm = new SendMessageCommand(
                        UUID.randomUUID(), System.currentTimeMillis(),
                        sender, "dev-0", "warmup-" + i,
                        UUID.randomUUID(), convId,
                        "W".repeat(1024), null
                );
                adapter.executeCommand(sm, true).get(5, TimeUnit.SECONDS);
            }

            // Clear warmup state
            latencies.clear();
            committedCount.set(0);
            errorCount.set(0);

            // Measurement phase with uncoordinated arrivals
            ExecutorService executor = Executors.newFixedThreadPool(concurrentClients);
            long measurementStart = System.nanoTime();

            List<Future<?>> futures = new ArrayList<>();
            for (int c = 0; c < concurrentClients; c++) {
                final int clientIdx = c;
                final String sender = userIds.get(clientIdx % userIds.size());
                futures.add(executor.submit(() -> {
                    Random rng = new Random(clientIdx * 7919L);
                    long nextArrival = System.nanoTime();

                    for (int m = 0; m < messagesPerClient; m++) {
                        // Wait until scheduled arrival time (uncoordinated)
                        long now = System.nanoTime();
                        if (nextArrival > now) {
                            try {
                                long sleepNanos = nextArrival - now;
                                Thread.sleep(sleepNanos / 1_000_000, (int) (sleepNanos % 1_000_000));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }

                        long arrivalTime = System.nanoTime();
                        nextArrival = arrivalTime + targetInterArrivalNanos;

                        UUID convId = convIds.get(rng.nextInt(convIds.size()));
                        SendMessageCommand sm = new SendMessageCommand(
                                UUID.randomUUID(), System.currentTimeMillis(),
                                sender, "dev-" + clientIdx, "load-" + clientIdx + "-" + m,
                                UUID.randomUUID(), convId,
                                "L".repeat(1024), null // 1 KiB payload
                        );

                        try {
                            CommandExecutionResult result = adapter.executeCommand(sm, true)
                                    .get(10, TimeUnit.SECONDS);
                            long commitTime = System.nanoTime();
                            long latencyNanos = commitTime - arrivalTime;

                            if (result.isSuccess()) {
                                committedCount.incrementAndGet();
                                latencies.add(latencyNanos);
                            } else {
                                errorCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get(120, TimeUnit.SECONDS);
            }
            long measurementEnd = System.nanoTime();
            executor.shutdown();

            // Compute latency histogram
            long[] sortedLatencies = latencies.stream().mapToLong(Long::longValue).sorted().toArray();

            long durationNanos = measurementEnd - measurementStart;
            double durationSeconds = durationNanos / 1_000_000_000.0;
            long totalMessages = (long) concurrentClients * messagesPerClient;

            return new LoadReport(
                    concurrentClients,
                    messagesPerClient,
                    totalMessages,
                    committedCount.get(),
                    errorCount.get(),
                    retryCount.get(),
                    durationSeconds,
                    committedCount.get() / durationSeconds,
                    sortedLatencies.length > 0 ? percentile(sortedLatencies, 50) : 0,
                    sortedLatencies.length > 0 ? percentile(sortedLatencies, 95) : 0,
                    sortedLatencies.length > 0 ? percentile(sortedLatencies, 99) : 0,
                    sortedLatencies.length > 0 ? sortedLatencies[sortedLatencies.length - 1] : 0
            );
        } finally {
            adapter.close();
        }
    }

    private static long percentile(long[] sorted, int p) {
        int idx = (int) Math.ceil((p / 100.0) * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    public record LoadReport(
            int clients,
            int messagesPerClient,
            long totalOffered,
            long totalCommitted,
            long totalErrors,
            long totalRetries,
            double durationSeconds,
            double throughputMsgPerSec,
            long p50LatencyNanos,
            long p95LatencyNanos,
            long p99LatencyNanos,
            long maxLatencyNanos
    ) {
        public String toSummary() {
            return String.format("""
                    === Vibe Load Report ===
                    Clients:            %d
                    Messages/client:    %d
                    Total offered:      %d
                    Total committed:    %d
                    Total errors:       %d
                    Duration:           %.2f s
                    Throughput:         %.1f msg/s
                    p50 latency:        %.2f ms
                    p95 latency:        %.2f ms
                    p99 latency:        %.2f ms
                    max latency:        %.2f ms
                    """,
                    clients, messagesPerClient, totalOffered, totalCommitted, totalErrors,
                    durationSeconds, throughputMsgPerSec,
                    p50LatencyNanos / 1_000_000.0,
                    p95LatencyNanos / 1_000_000.0,
                    p99LatencyNanos / 1_000_000.0,
                    maxLatencyNanos / 1_000_000.0);
        }
    }

    public static void main(String[] args) throws Exception {
        Path storageDir = Files.createTempDirectory("vibe-load-test");
        int clients = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int msgsPerClient = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        double targetRate = args.length > 2 ? Double.parseDouble(args[2]) : 2000.0;

        VibeLoadClient loadClient = new VibeLoadClient(
                storageDir, clients, msgsPerClient, 50, targetRate
        );

        LoadReport report = loadClient.run();
        System.out.println(report.toSummary());

        // Cleanup
        try (var s = Files.walk(storageDir)) {
            s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }
}
