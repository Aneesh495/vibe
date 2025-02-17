package com.vibe.benchmarks;

import com.vibe.protocol.CRC32CUtil;
import com.vibe.protocol.Frame;
import com.vibe.protocol.FrameDecoder;
import com.vibe.protocol.FrameEncoder;
import com.vibe.protocol.FrameType;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class FrameCodecBenchmark {

    private Frame testFrame;
    private byte[] rawBytes;

    @Setup
    public void setup() {
        byte[] payload = "Monotonic stream chunk with CRC32C validation!".getBytes(StandardCharsets.UTF_8);
        testFrame = Frame.create(FrameType.CONVERSATION_EVENT, 987654321L, ByteBuffer.wrap(payload));
        ByteBuffer encodedBuffer = FrameEncoder.encode(testFrame);
        rawBytes = new byte[encodedBuffer.remaining()];
        encodedBuffer.get(rawBytes);
    }

    @Benchmark
    public ByteBuffer benchEncodeFrame() {
        return FrameEncoder.encode(testFrame);
    }

    @Benchmark
    public int benchComputeCRC32C() {
        return CRC32CUtil.compute(rawBytes, 24, rawBytes.length - 24);
    }

    @Benchmark
    public Frame benchDecodeFrame() {
        FrameDecoder decoder = new FrameDecoder();
        return decoder.decode(ByteBuffer.wrap(rawBytes)).get(0);
    }
}
