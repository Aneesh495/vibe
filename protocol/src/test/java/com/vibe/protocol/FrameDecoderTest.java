package com.vibe.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrameDecoderTest {

    @Test
    void testSingleFrameRoundtrip() {
        byte[] payloadData = "Hello Vibe Distributed Messenger".getBytes(StandardCharsets.UTF_8);
        Frame frame = Frame.create(FrameType.COMMAND, 100L, ByteBuffer.wrap(payloadData));

        ByteBuffer encoded = FrameEncoder.encode(frame);

        FrameDecoder decoder = new FrameDecoder();
        List<Frame> decoded = decoder.decode(encoded);

        assertThat(decoded).hasSize(1);
        Frame res = decoded.getFirst();
        assertThat(res.type()).isEqualTo(FrameType.COMMAND);
        assertThat(res.correlationId()).isEqualTo(100L);
        assertThat(res.payloadLength()).isEqualTo(payloadData.length);

        byte[] decodedPayload = new byte[res.payloadLength()];
        res.payload().get(decodedPayload);
        assertThat(decodedPayload).isEqualTo(payloadData);
        assertThat(decoder.hasPartialData()).isFalse();
    }

    @Test
    void testZeroLengthPayloadFrame() {
        Frame heartbeat = Frame.heartbeat(555L);
        ByteBuffer encoded = FrameEncoder.encode(heartbeat);

        FrameDecoder decoder = new FrameDecoder();
        List<Frame> decoded = decoder.decode(encoded);

        assertThat(decoded).hasSize(1);
        Frame res = decoded.getFirst();
        assertThat(res.type()).isEqualTo(FrameType.HEARTBEAT);
        assertThat(res.correlationId()).isEqualTo(555L);
        assertThat(res.payloadLength()).isZero();
        assertThat(res.payload().remaining()).isZero();
    }

    @Test
    void testIncrementalByteByByteFeed() {
        byte[] payloadData = "Streaming across TCP chunk boundaries".getBytes(StandardCharsets.UTF_8);
        Frame original = Frame.create(FrameType.CONVERSATION_EVENT, 999L, ByteBuffer.wrap(payloadData));
        ByteBuffer encoded = FrameEncoder.encode(original);

        FrameDecoder decoder = new FrameDecoder();
        List<Frame> decoded = new ArrayList<>();

        // Feed byte by byte to simulate worst-case fragmentation
        while (encoded.hasRemaining()) {
            ByteBuffer singleByte = ByteBuffer.allocate(1);
            singleByte.put(encoded.get());
            singleByte.flip();
            decoder.decode(singleByte, decoded::add);
        }

        assertThat(decoded).hasSize(1);
        Frame res = decoded.getFirst();
        assertThat(res.type()).isEqualTo(FrameType.CONVERSATION_EVENT);
        assertThat(res.correlationId()).isEqualTo(999L);
        assertThat(res.payloadLength()).isEqualTo(payloadData.length);

        byte[] resBytes = new byte[res.payloadLength()];
        res.payload().get(resBytes);
        assertThat(resBytes).isEqualTo(payloadData);
        assertThat(decoder.hasPartialData()).isFalse();
    }

    @Test
    void testMultipleFramesInSingleBuffer() {
        Frame f1 = Frame.heartbeat(1L);
        Frame f2 = Frame.create(FrameType.COMMAND, 2L, ByteBuffer.wrap("cmd2".getBytes(StandardCharsets.UTF_8)));
        Frame f3 = Frame.create(FrameType.ACKNOWLEDGE, 3L, ByteBuffer.wrap("ack3".getBytes(StandardCharsets.UTF_8)));

        ByteBuffer combined = ByteBuffer.allocate(f1.totalSize() + f2.totalSize() + f3.totalSize());
        FrameEncoder.encode(f1, combined);
        FrameEncoder.encode(f2, combined);
        FrameEncoder.encode(f3, combined);
        combined.flip();

        FrameDecoder decoder = new FrameDecoder();
        List<Frame> decoded = decoder.decode(combined);

        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(0).correlationId()).isEqualTo(1L);
        assertThat(decoded.get(1).correlationId()).isEqualTo(2L);
        assertThat(decoded.get(2).correlationId()).isEqualTo(3L);
        assertThat(decoder.hasPartialData()).isFalse();
    }

    @Test
    void testFiftyFramesBurst() {
        List<Frame> originals = new ArrayList<>();
        int totalSize = 0;
        for (int i = 0; i < 50; i++) {
            byte[] data = ("Burst message payload #" + i).getBytes(StandardCharsets.UTF_8);
            Frame f = Frame.create(FrameType.COMMAND, i, ByteBuffer.wrap(data));
            originals.add(f);
            totalSize += f.totalSize();
        }

        ByteBuffer burstBuffer = ByteBuffer.allocate(totalSize);
        for (Frame f : originals) {
            FrameEncoder.encode(f, burstBuffer);
        }
        burstBuffer.flip();

        FrameDecoder decoder = new FrameDecoder();
        List<Frame> decoded = decoder.decode(burstBuffer);

        assertThat(decoded).hasSize(50);
        for (int i = 0; i < 50; i++) {
            assertThat(decoded.get(i).correlationId()).isEqualTo(i);
        }
        assertThat(decoder.hasPartialData()).isFalse();
    }
}
