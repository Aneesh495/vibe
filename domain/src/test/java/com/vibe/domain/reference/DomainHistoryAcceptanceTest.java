package com.vibe.domain.reference;

import com.vibe.domain.command.DomainCommand;
import com.vibe.domain.state.DomainStateMachine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainHistoryAcceptanceTest {

    @Test
    void testTenThousandOperationsAgainstSequentialReferenceModel() {
        long seed = 495_2025L;
        HistoryGenerator generator = new HistoryGenerator(seed);
        List<DomainCommand> history = generator.generateHistory(10_000, 25, 50);

        DomainStateMachine stateMachine = new DomainStateMachine();
        SequentialReferenceModel referenceModel = new SequentialReferenceModel();
        LinearizabilityHistoryChecker.VerificationReport report =
                LinearizabilityHistoryChecker.verifyHistory(history, stateMachine, referenceModel);

        assertThat(report.linearizable())
                .as("History verification failed: " + report.failureReason())
                .isTrue();
        assertThat(report.totalOperations()).isEqualTo(10_000);
        assertThat(report.matchedOutcomes()).isEqualTo(10_000);
        assertThat(report.successfulOperations()).isGreaterThan(5_000);
        assertThat(report.expectedFailures()).isGreaterThan(1_000);
    }
}
