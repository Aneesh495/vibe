package com.vibe.domain.state;

import com.vibe.domain.event.DomainEvent;
import com.vibe.protocol.ErrorCode;

/**
 * Result of applying a domain command to the state machine.
 */
public record CommandExecutionResult(
        boolean success,
        long assignedSeq,
        ErrorCode errorCode,
        String errorMessage,
        byte[] payload,
        DomainEvent event
) {

    public static CommandExecutionResult success(long assignedSeq, byte[] payload, DomainEvent event) {
        return new CommandExecutionResult(true, assignedSeq, null, null, payload, event);
    }

    public static CommandExecutionResult success(long assignedSeq, byte[] payload) {
        return success(assignedSeq, payload, null);
    }

    public static CommandExecutionResult failure(ErrorCode errorCode, String message) {
        return new CommandExecutionResult(false, -1L, errorCode, message, null, null);
    }

    public boolean isSuccess() {
        return success;
    }
}
