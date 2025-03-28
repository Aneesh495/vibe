package com.vibe.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the load client verifying basic throughput measurement functions correctly.
 */
class LoadClientSmokeTest {

    @Test
    void testSmallLoadRunProducesMeaningfulReport(@TempDir Path tempDir) throws Exception {
        VibeLoadClient client = new VibeLoadClient(tempDir, 2, 50, 10, 500.0);
        VibeLoadClient.LoadReport report = client.run();

        assertThat(report.totalCommitted()).isGreaterThan(50);
        assertThat(report.totalErrors()).isLessThan(report.totalOffered());
        assertThat(report.throughputMsgPerSec()).isGreaterThan(0);
        assertThat(report.p50LatencyNanos()).isGreaterThan(0);
        assertThat(report.p99LatencyNanos()).isGreaterThanOrEqualTo(report.p50LatencyNanos());
        assertThat(report.durationSeconds()).isGreaterThan(0);

        System.out.println(report.toSummary());
    }
}
