package com.vibe.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * CLI launcher for Vibe JMH benchmarks.
 */
public final class BenchmarkMain {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(DomainCommandCodecBenchmark.class.getSimpleName())
                .include(FrameCodecBenchmark.class.getSimpleName())
                .include(WalAppendBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(1)
                .measurementIterations(2)
                .build();

        new Runner(opt).run();
    }
}
