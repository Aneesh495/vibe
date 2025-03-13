package com.vibe.domain.reference;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.Conversation;
import com.vibe.domain.entity.Message;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;

import java.util.*;

/**
 * Validates domain state machine outcomes against the sequential reference model.
 */
public final class LinearizabilityHistoryChecker {

    public record VerificationReport(
            int totalOperations,
            int successfulOperations,
            int expectedFailures,
            int matchedOutcomes,
            boolean linearizable,
            String failureReason
    ) {
    }

    public static VerificationReport verifyHistory(List<DomainCommand> history, DomainStateMachine stateMachine, SequentialReferenceModel referenceModel) {
        int successCount = 0;
        int failureCount = 0;
        int matched = 0;

        for (int i = 0; i < history.size(); i++) {
            DomainCommand cmd = history.get(i);
            SequentialReferenceModel.ExpectedResult expected = referenceModel.apply(cmd);
            CommandExecutionResult actual = stateMachine.apply(cmd);

            if (actual.isSuccess() != expected.success()) {
                return new VerificationReport(
                        history.size(), successCount, failureCount, matched, false,
                        String.format("Op %d mismatch for %s: expected success=%b, got success=%b (%s)",
                                i, cmd.getClass().getSimpleName(), expected.success(), actual.isSuccess(), actual.errorMessage())
                );
            }

            if (actual.isSuccess()) {
                successCount++;
                if (expected.seq() > 0 && actual.assignedSeq() != expected.seq()) {
                    return new VerificationReport(
                        history.size(), successCount, failureCount, matched, false,
                        String.format("Op %d sequence mismatch: expected seq=%d, got seq=%d",
                                i, expected.seq(), actual.assignedSeq())
                    );
                }
            } else {
                failureCount++;
                if (expected.error() != null && actual.errorCode() != expected.error()) {
                    return new VerificationReport(
                            history.size(), successCount, failureCount, matched, false,
                            String.format("Op %d error code mismatch: expected %s, got %s",
                                    i, expected.error(), actual.errorCode())
                    );
                }
            }
            matched++;
        }

        // Post-execution invariants check
        for (Conversation conv : stateMachine.allConversations().values()) {
            List<Message> msgs = stateMachine.getMessages(conv.conversationId(), 1L, Integer.MAX_VALUE);
            long expectedSeq = 1;
            for (Message m : msgs) {
                if (m.seq() != expectedSeq) {
                    return new VerificationReport(
                            history.size(), successCount, failureCount, matched, false,
                            String.format("Sequence gap in conv %s: expected %d, got %d",
                                    conv.conversationId(), expectedSeq, m.seq())
                    );
                }
                expectedSeq++;
            }
        }

        return new VerificationReport(history.size(), successCount, failureCount, matched, true, null);
    }
}
