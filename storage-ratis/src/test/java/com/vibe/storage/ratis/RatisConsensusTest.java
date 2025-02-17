package com.vibe.storage.ratis;

import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.entity.User;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RatisConsensusTest {

    private static final Logger log = LoggerFactory.getLogger(RatisConsensusTest.class);

    private RatisCluster cluster;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        // Create 3-node in-process Ratis cluster
        cluster = new RatisCluster(tempDir, 3);
        cluster.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Test
    void testThreeNodeQuorumCommitAndStateConvergence() throws Exception {
        // 1. Wait for leader election across the 3-node cluster
        RatisDurabilityAdapter leaderAdapter = cluster.awaitLeader(10, TimeUnit.SECONDS);
        assertThat(leaderAdapter).isNotNull();
        assertThat(leaderAdapter.isLeader()).isTrue();

        // 2. Submit domain command for durable consensus commit
        UUID commandId = UUID.randomUUID();
        String userId = "user-quorum-1";
        RegisterUserCommand cmd = new RegisterUserCommand(
                commandId,
                System.currentTimeMillis(),
                userId,
                "dev-quorum-1",
                "client-msg-quorum-1",
                "bob",
                "$2a$10$hashedpasswordplaceholder",
                "Bob",
                "Consensus enthusiast",
                "bob.png"
        );

        CompletableFuture<CommandExecutionResult> future = leaderAdapter.executeCommand(cmd, true);
        CommandExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();

        // 3. Verify state machine convergence across all nodes in the cluster
        // Allow brief window for follower log replication and state machine application
        boolean converged = false;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            boolean allHaveUser = true;
            for (DomainStateMachine sm : cluster.stateMachines()) {
                User u = sm.getUserById(userId);
                if (u == null || !"bob".equals(u.username())) {
                    allHaveUser = false;
                    break;
                }
            }
            if (allHaveUser) {
                converged = true;
                break;
            }
            Thread.sleep(50);
        }

        assertThat(converged)
                .withFailMessage("All 3 state machines did not converge on committed state")
                .isTrue();

        for (DomainStateMachine sm : cluster.stateMachines()) {
            User user = sm.getUserById(userId);
            assertThat(user.displayName()).isEqualTo("Bob");
            assertThat(user.bio()).isEqualTo("Consensus enthusiast");
        }
    }

    @Test
    void testQuorumProgressWithOneNodeOffline() throws Exception {
        RatisDurabilityAdapter leader = cluster.awaitLeader(10, TimeUnit.SECONDS);

        // Find a follower index
        int followerIdx = -1;
        for (int i = 0; i < cluster.adapters().size(); i++) {
            if (!cluster.getAdapter(i).isLeader()) {
                followerIdx = i;
                break;
            }
        }
        assertThat(followerIdx).isNotEqualTo(-1);

        // Stop follower (2 out of 3 nodes remain active - quorum maintained!)
        cluster.stopNode(followerIdx);

        // Submit mutation to leader
        UUID commandId = UUID.randomUUID();
        String userId = "user-quorum-fault-tolerant";
        RegisterUserCommand cmd = new RegisterUserCommand(
                commandId,
                System.currentTimeMillis(),
                userId,
                "dev-quorum-2",
                "client-msg-quorum-2",
                "charlie",
                "$2a$10$hashedpasswordplaceholder",
                "Charlie",
                "Fault tolerance verification",
                "charlie.png"
        );

        CompletableFuture<CommandExecutionResult> future = leader.executeCommand(cmd, true);
        CommandExecutionResult result = future.get(10, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();

        User registered = leader.stateMachine().getUserById(userId);
        assertThat(registered).isNotNull();
        assertThat(registered.username()).isEqualTo("charlie");
    }
}
