package com.vibe.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameHeaderTest {

    @Test
    void testEncodeAndDecodeHeader() {
        FrameHeader original = FrameHeader.create(
                ProtocolConstants.VERSION_1,
                FrameType.COMMAND,
                (short) (FrameFlags.FLAG_SYNC | FrameFlags.FLAG_URGENT),
                123456789L,
                1024
        );

        ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
        original.encode(buf);
        buf.flip();

        assertThat(buf.remaining()).isEqualTo(24);
        FrameHeader decoded = FrameHeader.decode(buf);

        assertThat(decoded.magic()).isEqualTo(ProtocolConstants.MAGIC);
        assertThat(decoded.version()).isEqualTo(ProtocolConstants.VERSION_1);
        assertThat(decoded.type()).isEqualTo(FrameType.COMMAND);
        assertThat(decoded.flags()).isEqualTo(original.flags());
        assertThat(decoded.correlationId()).isEqualTo(123456789L);
        assertThat(decoded.payloadLength()).isEqualTo(1024);
        assertThat(decoded.headerCrc()).isEqualTo(original.headerCrc());
        assertThat(decoded.hasFlag(FrameFlags.FLAG_SYNC)).isTrue();
        assertThat(decoded.hasFlag(FrameFlags.FLAG_URGENT)).isTrue();
        assertThat(decoded.hasFlag(FrameFlags.FLAG_COMPRESSED)).isFalse();
    }

    @Test
    void testCorruptedMagicThrowsException() {
        ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
        buf.putInt(0xDEADBEEF); // Bad magic
        buf.put(ProtocolConstants.VERSION_1);
        buf.put(FrameType.HEARTBEAT.code());
        buf.putShort((short) 0);
        buf.putLong(1L);
        buf.putInt(0);
        buf.putInt(0);
        buf.flip();

        assertThatThrownBy(() -> FrameHeader.decode(buf))
                .isInstanceOf(ProtocolException.class)
                .matches(e -> ((ProtocolException) e).errorCode() == ErrorCode.PROTOCOL_MALFORMED_HEADER);
    }

    @Test
    void testUnsupportedVersionThrowsException() {
        ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
        buf.putInt(ProtocolConstants.MAGIC);
        buf.put((byte) 0x99); // Unsupported version
        buf.put(FrameType.HEARTBEAT.code());
        buf.putShort((short) 0);
        buf.putLong(1L);
        buf.putInt(0);
        buf.putInt(0);
        buf.flip();

        assertThatThrownBy(() -> FrameHeader.decode(buf))
                .isInstanceOf(ProtocolException.class)
                .matches(e -> ((ProtocolException) e).errorCode() == ErrorCode.PROTOCOL_UNSUPPORTED_VERSION);
    }

    @Test
    void testOversizedPayloadLengthThrowsException() {
        ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
        buf.putInt(ProtocolConstants.MAGIC);
        buf.put(ProtocolConstants.VERSION_1);
        buf.put(FrameType.COMMAND.code());
        buf.putShort((short) 0);
        buf.putLong(1L);
        buf.putInt(ProtocolConstants.MAX_PAYLOAD_LENGTH + 1); // Exceeds 16 MiB
        buf.putInt(0);
        buf.flip();

        assertThatThrownBy(() -> FrameHeader.decode(buf))
                .isInstanceOf(ProtocolException.class)
                .matches(e -> ((ProtocolException) e).errorCode() == ErrorCode.PROTOCOL_FRAME_TOO_LARGE);
    }

    @Test
    void testCorruptedCrcThrowsException() {
        FrameHeader original = FrameHeader.create(
                ProtocolConstants.VERSION_1,
                FrameType.HEARTBEAT,
                (short) 0,
                42L,
                0
        );

        ByteBuffer buf = ByteBuffer.allocate(ProtocolConstants.HEADER_SIZE);
        original.encode(buf);
        // Tamper with the correlationId byte
        buf.put(8, (byte) (buf.get(8) ^ 0xFF));
        buf.flip();

        assertThatThrownBy(() -> FrameHeader.decode(buf))
                .isInstanceOf(ProtocolException.class)
                .matches(e -> ((ProtocolException) e).errorCode() == ErrorCode.PROTOCOL_CHECKSUM_MISMATCH);
    }
}
