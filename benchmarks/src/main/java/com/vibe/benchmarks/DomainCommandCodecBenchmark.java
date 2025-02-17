package com.vibe.benchmarks;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.DomainCommandCodec;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.ConversationType;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class DomainCommandCodecBenchmark {

    private SendMessageCommand sendCmd;
    private CreateConversationCommand createCmd;
    private byte[] encodedSendBytes;
    private byte[] encodedCreateBytes;

    @Setup
    public void setup() {
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        UUID cmdId = UUID.randomUUID();
        sendCmd = new SendMessageCommand(
                cmdId, System.currentTimeMillis(), "user-alice-1234", "dev-desktop-5678",
                "client-msg-9999", msgId, convId, "Hello high-throughput distributed world!", null
        );

        ByteBuffer buf1 = sendCmd.encode();
        encodedSendBytes = new byte[buf1.remaining()];
        buf1.get(encodedSendBytes);

        createCmd = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(), "user-alice-1234", "dev-desktop-5678",
                "client-conv-1111", convId, ConversationType.GROUP, "Distributed Systems Engineering",
                List.of("user-alice-1234", "user-bob-5678", "user-charlie-9012")
        );

        ByteBuffer buf2 = createCmd.encode();
        encodedCreateBytes = new byte[buf2.remaining()];
        buf2.get(encodedCreateBytes);
    }

    @Benchmark
    public ByteBuffer benchEncodeSendMessage() {
        return sendCmd.encode();
    }

    @Benchmark
    public DomainCommand benchDecodeSendMessage() {
        return DomainCommandCodec.decode(ByteBuffer.wrap(encodedSendBytes));
    }

    @Benchmark
    public ByteBuffer benchEncodeCreateConversation() {
        return createCmd.encode();
    }

    @Benchmark
    public DomainCommand benchDecodeCreateConversation() {
        return DomainCommandCodec.decode(ByteBuffer.wrap(encodedCreateBytes));
    }
}
