package com.vibe.protocol.payload;

import java.nio.ByteBuffer;

/**
 * Payload for COMMAND_RESULT frame (0x07).
 */
public record CommandResultPayload(
        byte status,
        long assignedSeq,
        long committedTimestamp,
        int errorCode,
        byte[] payload
) {

    public static final byte STATUS_SUCCESS = 0x00;
    public static final byte STATUS_REJECTED = 0x01;

    public CommandResultPayload {
        payload = payload != null ? payload : new byte[0];
    }

    public static CommandResultPayload success(long assignedSeq, long committedTimestamp, byte[] payload) {
        return new CommandResultPayload(STATUS_SUCCESS, assignedSeq, committedTimestamp, 0, payload);
    }

    public static CommandResultPayload failure(int errorCode, byte[] payload) {
        return new CommandResultPayload(STATUS_REJECTED, -1L, System.currentTimeMillis(), errorCode, payload);
    }

    public boolean isSuccess() {
        return status == STATUS_SUCCESS;
    }

    public ByteBuffer encode() {
        int size = 1 + 8 + 8 + 2 + PayloadCodecUtil.bytesLength(payload);
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.put(status);
        buf.putLong(assignedSeq);
        buf.putLong(committedTimestamp);
        buf.putShort((short) errorCode);
        PayloadCodecUtil.writeBytes(buf, payload);
        buf.flip();
        return buf;
    }

    public static CommandResultPayload decode(ByteBuffer src) {
        byte status = src.get();
        long assignedSeq = src.getLong();
        long committedTimestamp = src.getLong();
        int errorCode = src.getShort() & 0xFFFF;
        byte[] payload = PayloadCodecUtil.readBytes(src);
        return new CommandResultPayload(status, assignedSeq, committedTimestamp, errorCode, payload);
    }
}
