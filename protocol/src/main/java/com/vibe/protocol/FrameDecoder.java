package com.vibe.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Stateful incremental decoder for Vibe binary frames.
 *
 * <p>Handles arbitrary chunk boundaries, multi-frame reads, partial headers,
 * zero-length payloads, and oversized payload defense.
 */
public final class FrameDecoder {

    private enum State {
        READING_HEADER,
        READING_PAYLOAD
    }

    private State state = State.READING_HEADER;
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
    private FrameHeader currentHeader;
    private ByteBuffer payloadBuffer;

    public FrameDecoder() {
    }

    /**
     * Decodes all complete frames available in the given input buffer and invokes the consumer for each.
     *
     * @param in incoming byte buffer containing network data
     * @param consumer recipient for decoded frames
     */
    public void decode(ByteBuffer in, Consumer<Frame> consumer) {
        Objects.requireNonNull(in, "Input buffer must not be null");
        Objects.requireNonNull(consumer, "Consumer must not be null");

        while (in.hasRemaining()) {
            if (state == State.READING_HEADER) {
                readHeader(in);
                if (state == State.READING_HEADER) {
                    // Header not yet complete; wait for more network bytes
                    break;
                }
            }

            if (state == State.READING_PAYLOAD) {
                readPayload(in);
                if (state == State.READING_PAYLOAD) {
                    // Payload not yet complete; wait for more network bytes
                    break;
                }

                // Frame complete!
                Frame frame = new Frame(currentHeader, payloadBuffer);
                resetForNextFrame();
                consumer.accept(frame);
            }
        }
    }

    /**
     * Decodes all complete frames currently available in the input buffer.
     *
     * @param in incoming byte buffer
     * @return list of decoded frames (empty if no complete frame is yet available)
     */
    public List<Frame> decode(ByteBuffer in) {
        List<Frame> frames = new ArrayList<>();
        decode(in, frames::add);
        return frames;
    }

    private void readHeader(ByteBuffer in) {
        int bytesToRead = Math.min(in.remaining(), headerBuffer.remaining());
        int oldLimit = in.limit();
        in.limit(in.position() + bytesToRead);
        headerBuffer.put(in);
        in.limit(oldLimit);

        if (!headerBuffer.hasRemaining()) {
            // Full 24-byte header received
            headerBuffer.flip();
            try {
                currentHeader = FrameHeader.decode(headerBuffer);
            } finally {
                headerBuffer.clear();
            }

            if (currentHeader.payloadLength() == 0) {
                // Header with 0-byte payload transitions immediately to payload completed
                payloadBuffer = ByteBuffer.allocate(0);
                // We keep state as READING_PAYLOAD so the main loop emits the frame
                state = State.READING_PAYLOAD;
            } else {
                payloadBuffer = ByteBuffer.allocate(currentHeader.payloadLength());
                state = State.READING_PAYLOAD;
            }
        }
    }

    private void readPayload(ByteBuffer in) {
        if (currentHeader.payloadLength() == 0) {
            // Already ready
            state = State.READING_HEADER;
            return;
        }

        int bytesToRead = Math.min(in.remaining(), payloadBuffer.remaining());
        int oldLimit = in.limit();
        in.limit(in.position() + bytesToRead);
        payloadBuffer.put(in);
        in.limit(oldLimit);

        if (!payloadBuffer.hasRemaining()) {
            payloadBuffer.flip();
            state = State.READING_HEADER;
        }
    }

    private void resetForNextFrame() {
        state = State.READING_HEADER;
        headerBuffer.clear();
        currentHeader = null;
        payloadBuffer = null;
    }

    /**
     * Resets internal decoder state (e.g. on channel recycling or fatal stream reset).
     */
    public void reset() {
        resetForNextFrame();
    }

    /**
     * Returns true if the decoder is currently holding a partial frame.
     */
    public boolean hasPartialData() {
        return state == State.READING_PAYLOAD || headerBuffer.position() > 0;
    }
}
