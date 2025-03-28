package com.vibe.benchmarks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkSmokeTest {

    @Test
    void testDomainCommandCodecBenchmarkExecution() {
        DomainCommandCodecBenchmark bench = new DomainCommandCodecBenchmark();
        bench.setup();

        assertThat(bench.benchEncodeSendMessage()).isNotNull();
        assertThat(bench.benchDecodeSendMessage()).isNotNull();
        assertThat(bench.benchEncodeCreateConversation()).isNotNull();
        assertThat(bench.benchDecodeCreateConversation()).isNotNull();
    }

    @Test
    void testFrameCodecBenchmarkExecution() {
        FrameCodecBenchmark bench = new FrameCodecBenchmark();
        bench.setup();

        assertThat(bench.benchEncodeFrame()).isNotNull();
        assertThat(bench.benchComputeCRC32C()).isNotZero();
        assertThat(bench.benchDecodeFrame()).isNotNull();
    }

    @Test
    void testWalAppendBenchmarkExecution() throws Exception {
        WalAppendBenchmark bench = new WalAppendBenchmark();
        bench.setupTrial();
        try {
            Long index = bench.benchWalAppend();
            assertThat(index).isNotNull().isGreaterThan(0L);
        } finally {
            bench.teardownTrial();
        }
    }

    @Test
    void testLocalDurabilityBenchmarkExecution() throws Exception {
        LocalDurabilityBenchmark bench = new LocalDurabilityBenchmark();
        bench.setupTrial();
        try {
            var res = bench.benchDurableMessageExecution();
            assertThat(res).isNotNull();
            assertThat(res.isSuccess()).isTrue();
        } finally {
            bench.teardownTrial();
        }
    }

    @Test
    void testRatisDurabilityBenchmarkExecution() throws Exception {
        RatisDurabilityBenchmark bench = new RatisDurabilityBenchmark();
        bench.setupTrial();
        try {
            var res = bench.benchRatisQuorumCommit();
            assertThat(res).isNotNull();
            assertThat(res.isSuccess()).isTrue();
        } finally {
            bench.teardownTrial();
        }
    }
}
