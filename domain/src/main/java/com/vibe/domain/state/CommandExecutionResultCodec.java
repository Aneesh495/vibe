package com.vibe.domain.state;

import com.vibe.protocol.ErrorCode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Binary codec for serializing and deserializing CommandExecutionResult.
 */
public final class CommandExecutionResultCodec {

    private CommandExecutionResultCodec() {}

    public static byte[] encode(CommandExecutionResult result) {
        byte[] msgBytes = result.errorMessage() != null ? result.errorMessage().getBytes(StandardCharsets.UTF_8) : null;
        int msgLen = msgBytes != null ? msgBytes.length : 0;
        byte[] payload = result.payload();
        int payloadLen = payload != null ? payload.length : -1;

        int totalSize = 1 + 8 + 4 + 2 + msgLen + 4 + (payloadLen > 0 ? payloadLen : 0);
        ByteBuffer buf = ByteBuffer.allocate(totalSize);

        buf.put((byte) (result.isSuccess() ? 1 : 0));
        buf.putLong(result.assignedSeq());
        buf.putInt(result.errorCode() != null ? result.errorCode().code() : 0);

        buf.putShort((short) msgLen);
        if (msgBytes != null) {
            buf.put(msgBytes);
        }

        buf.putInt(payloadLen);
        if (payload != null && payloadLen > 0) {
            buf.put(payload);
        }

        return buf.array();
    }

    public static CommandExecutionResult decode(ByteBuffer buf) {
        boolean success = buf.get() != 0;
        long assignedSeq = buf.getLong();
        int errorCodeVal = buf.getInt();
        ErrorCode errorCode = errorCodeVal != 0 ? ErrorCode.fromCode(errorCodeVal) : null;

        int msgLen = buf.getShort() & 0xFFFF;
        String errorMessage = null;
        if (msgLen > 0) {
            byte[] msgBytes = new byte[msgLen];
            buf.get(msgBytes);
            errorMessage = new String(msgBytes, StandardCharsets.UTF_8);
        }

        int payloadLen = buf.getInt();
        byte[] payload = null;
        if (payloadLen >= 0) {
            payload = new byte[payloadLen];
            if (payloadLen > 0) {
                buf.get(payload);
            }
        }

        return new CommandExecutionResult(success, assignedSeq, errorCode, errorMessage, payload, null);
    }
}
