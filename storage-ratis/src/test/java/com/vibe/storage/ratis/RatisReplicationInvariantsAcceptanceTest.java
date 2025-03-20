package com.vibe.storage.ratis;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.Conversation;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.entity.Message;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification of consensus replication invariants across a 3-node Apache Ratis cluster.
 *
 * <p>Validates:
 * <ul>
 *   <li>Leader kill, failover election, and continued consensus progress</li>
 *   <li>Follower failure, majority commit progression, and follower rejoin catch-up</li>
 *   <li>Identical linear state machine convergence across all nodes</li>
 * </ul>
 */
class RatisReplicationInvariantsAcceptanceTest {

    private static final Logger log = LoggerFactory.getLogger(RatisReplicationInvariantsAcceptanceTest.class);

    private RatisCluster cluster;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
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
    void testLeaderKillFailoverAndFollowerRejoinCatchUp() throws Exception {
        // 1. Await initial leader
        RatisDurabilityAdapter leader1 = cluster.awaitLeader(15, TimeUnit.SECONDS);
        int leader1Idx = cluster.getLeaderIndex();
        assertThat(leader1Idx).isGreaterThanOrEqualTo(0);
        log.info("Initial leader node index: {}", leader1Idx);

        // 2. Commit initial user and conversation on leader1
        String userAlice = "user-alice-rep";
        RegisterUserCommand regAlice = new RegisterUserCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userAlice, "dev-1", "req-1", "alice",
                "$2a$10$hashalice", "Alice", "Leader Kill Test", ""
        );
        CommandExecutionResult res1 = leader1.executeCommand(regAlice, true).get(10, TimeUnit.SECONDS);
        assertThat(res1.isSuccess()).isTrue();

        UUID convId = UUID.randomUUID();
        CreateConversationCommand cc1 = new CreateConversationCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userAlice, "dev-1", "req-2", convId,
                ConversationType.GROUP, "Consensus Channel", List.of(userAlice)
        );
        CommandExecutionResult res2 = leader1.executeCommand(cc1, true).get(10, TimeUnit.SECONDS);
        assertThat(res2.isSuccess()).isTrue();

        // 3. Kill Leader 1 abruptly!
        log.info("Killing leader index {}...", leader1Idx);
        cluster.stopNode(leader1Idx);

        // 4. Await new leader election among the remaining 2 nodes
        RatisDurabilityAdapter leader2 = cluster.awaitLeaderExcept(leader1Idx, 20, TimeUnit.SECONDS);
        int leader2Idx = -1;
        for (int i = 0; i < cluster.adapters().size(); i++) {
            if (i != leader1Idx && cluster.getAdapter(i).isLeader()) {
                leader2Idx = i;
                break;
            }
        }
        assertThat(leader2Idx).isGreaterThanOrEqualTo(0);
        log.info("New leader elected: index {}", leader2Idx);

        // 5. Submit messages to new leader2 (2 of 3 nodes maintain quorum!)
        SendMessageCommand sm1 = new SendMessageCommand(
                UUID.randomUUID(), System.currentTimeMillis(),
                userAlice, "dev-1", "cmsg-post-failover-1",
                UUID.randomUUID(), convId, "Message committed post-failover", null
        );
        CommandExecutionResult res3 = leader2.executeCommand(sm1, true).get(10, TimeUnit.SECONDS);
        assertThat(res3.isSuccess()).isTrue();
        assertThat(res3.assignedSeq()).isEqualTo(1L);

        // 6. Restart the old leader (leader1) as a follower and verify catch-up replication
        log.info("Restarting killed node index {}...", leader1Idx);
        cluster.restartNode(leader1Idx);

        // Allow catch-up replication across all nodes
        boolean allSynced = false;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            boolean everyNodeHasMessage = true;
            for (DomainStateMachine sm : cluster.stateMachines()) {
                List<Message> msgs = sm.getMessages(convId, 1L, 10);
                if (msgs.isEmpty() || !msgs.get(0).content().equals("Message committed post-failover")) {
                    everyNodeHasMessage = false;
                    break;
                }
            }
            if (everyNodeHasMessage) {
                allSynced = true;
                break;
            }
            Thread.sleep(100);
        }

        assertThat(allSynced)
                .withFailMessage("Rejoined node did not catch up with committed entries")
                .isTrue();

        // 7. Verify all 3 state machines are strictly identical
        for (DomainStateMachine sm : cluster.stateMachines()) {
            User u = sm.getUserById(userAlice);
            assertThat(u).isNotNull();
            assertThat(u.username()).isEqualTo("alice");

            Conversation c = sm.getConversation(convId);
            assertThat(c).isNotNull();

            List<Message> msgs = sm.getMessages(convId, 1L, 10);
            assertThat(msgs).hasSize(1);
            assertThat(msgs.get(0).seq()).isEqualTo(1L);
            assertThat(msgs.get(0).content()).isEqualTo("Message committed post-failover");
        }
    }
}
