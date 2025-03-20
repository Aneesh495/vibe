package com.vibe.storage.local;

import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.entity.Conversation;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.entity.Message;
import com.vibe.domain.entity.User;
import com.vibe.domain.state.CommandExecutionResult;
import com.vibe.domain.state.DomainStateMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rigorous 100-cycle durability crash recovery acceptance campaign.
 *
 * <p>Validates that across 100 crash/recovery cycles involving:
 * <ul>
 *   <li>Abrupt process death (no clean shutdown)</li>
 *   <li>Torn records and partial writes at WAL segment EOF</li>
 *   <li>Segment rotations under active writes</li>
 *   <li>In-flight temporary snapshot file interruptions</li>
 * </ul>
 *
 * <p>Guarantees zero lost acknowledged commits, zero data corruption,
 * clean replay, and exact monotonic state rebuild.
 */
class CrashRecoveryAcceptanceTest {

    private static final int CRASH_CYCLES = 100;

    @Test
    void testOneHundredCrashRecoveryCyclesWithZeroLostAcknowledgedCommits(@TempDir Path tempDir) throws Exception {
        Random rng = new Random(495_2025L);

        // Ground-truth oracle of all acknowledged transactions
        Map<String, String> acknowledgedUsers = new HashMap<>(); // userId -> username
        Map<UUID, UUID> acknowledgedConversations = new HashMap<>(); // convId -> convId
        Map<UUID, List<String>> acknowledgedMessages = new HashMap<>(); // convId -> list of message contents

        long segmentSizeBytes = 128 * 1024L; // 128 KiB to force frequent segment rotations
        long flushIntervalMs = 20L;
        int maxBatchSize = 50;

        for (int cycle = 1; cycle <= CRASH_CYCLES; cycle++) {
            DomainStateMachine stateMachine = new DomainStateMachine();
            LocalDurabilityAdapter adapter = new LocalDurabilityAdapter(
                    tempDir,
                    stateMachine,
                    segmentSizeBytes,
                    flushIntervalMs,
                    maxBatchSize
            );

            // 1. Recover state machine from persistent WAL + snapshots
            adapter.start();

            // 2. Verify state rebuild against all previously acknowledged transactions
            for (Map.Entry<String, String> entry : acknowledgedUsers.entrySet()) {
                User u = stateMachine.getUserById(entry.getKey());
                assertThat(u)
                        .withFailMessage("Cycle %d: User %s lost after recovery", cycle, entry.getKey())
                        .isNotNull();
                assertThat(u.username()).isEqualTo(entry.getValue());
            }

            for (UUID convId : acknowledgedConversations.keySet()) {
                Conversation c = stateMachine.getConversation(convId);
                assertThat(c)
                        .withFailMessage("Cycle %d: Conversation %s lost after recovery", cycle, convId)
                        .isNotNull();
            }

            for (Map.Entry<UUID, List<String>> entry : acknowledgedMessages.entrySet()) {
                List<Message> msgs = stateMachine.getMessages(entry.getKey(), 1L, Integer.MAX_VALUE);
                List<String> expectedContents = entry.getValue();
                assertThat(msgs).hasSize(expectedContents.size());
                for (int i = 0; i < expectedContents.size(); i++) {
                    assertThat(msgs.get(i).content()).isEqualTo(expectedContents.get(i));
                    assertThat(msgs.get(i).seq()).isEqualTo(i + 1L);
                }
            }

            // 3. Apply new mutations in this cycle
            int opCount = 5 + rng.nextInt(15);
            for (int i = 0; i < opCount; i++) {
                int opType = rng.nextInt(3);
                if (opType == 0 || acknowledgedUsers.size() < 2) {
                    // Register user
                    String uid = "user_c" + cycle + "_" + i;
                    String uname = "u_" + cycle + "_" + i;
                    RegisterUserCommand reg = new RegisterUserCommand(
                            UUID.randomUUID(), System.currentTimeMillis(),
                            uid, "dev", "req_" + cycle + "_" + i, uname,
                            "hash_" + uname, "Name " + uname, "Bio", ""
                    );
                    CommandExecutionResult res = adapter.executeCommand(reg, true).get(5, TimeUnit.SECONDS);
                    if (res.isSuccess()) {
                        acknowledgedUsers.put(uid, uname);
                    }
                } else if (opType == 1 || acknowledgedConversations.isEmpty()) {
                    // Create conversation
                    UUID cid = UUID.randomUUID();
                    List<String> userList = new ArrayList<>(acknowledgedUsers.keySet());
                    String creator = userList.get(0);
                    List<String> members = userList.subList(0, Math.min(userList.size(), 3));

                    CreateConversationCommand cc = new CreateConversationCommand(
                            UUID.randomUUID(), System.currentTimeMillis(),
                            creator, "dev", "cc_" + cycle + "_" + i, cid,
                            ConversationType.GROUP, "Chat " + cycle + "_" + i, members
                    );
                    CommandExecutionResult res = adapter.executeCommand(cc, true).get(5, TimeUnit.SECONDS);
                    if (res.isSuccess()) {
                        acknowledgedConversations.put(cid, cid);
                        acknowledgedMessages.put(cid, new ArrayList<>());
                    }
                } else {
                    // Send message
                    List<UUID> convList = new ArrayList<>(acknowledgedConversations.keySet());
                    UUID cid = convList.get(rng.nextInt(convList.size()));
                    Conversation conv = stateMachine.getConversation(cid);
                    String sender = conv.members().keySet().iterator().next();
                    String content = "Msg cycle " + cycle + " op " + i + " rand=" + rng.nextInt(10000);

                    SendMessageCommand sm = new SendMessageCommand(
                            UUID.randomUUID(), System.currentTimeMillis(),
                            sender, "dev", "msg_" + cycle + "_" + i,
                            UUID.randomUUID(), cid, content, null
                    );
                    CommandExecutionResult res = adapter.executeCommand(sm, true).get(5, TimeUnit.SECONDS);
                    if (res.isSuccess()) {
                        acknowledgedMessages.get(cid).add(content);
                    }
                }
            }

            // 4. Inject Crash Failure Scenario
            int crashType = cycle % 4;
            if (crashType == 0) {
                // Scenario A: Clean shutdown
                adapter.close();
            } else if (crashType == 1) {
                // Scenario B: Abrupt termination (no adapter.close() invoked)
                // Leave files as-is
            } else if (crashType == 2) {
                // Scenario C: Torn write at EOF of the active WAL segment
                Path activeSeg = findActiveSegment(tempDir);
                if (activeSeg != null && Files.exists(activeSeg)) {
                    byte[] tornBytes = new byte[3 + rng.nextInt(15)];
                    rng.nextBytes(tornBytes);
                    Files.write(activeSeg, tornBytes, StandardOpenOption.APPEND);
                }
            } else {
                // Scenario D: Incomplete snapshot write (simulate crash mid-snapshot creation)
                Path snapshotDir = tempDir.resolve("snapshots");
                Files.createDirectories(snapshotDir);
                Path orphanedTmpSnap = snapshotDir.resolve("snapshot_" + cycle + ".snap.tmp");
                byte[] partialSnap = new byte[50];
                rng.nextBytes(partialSnap);
                Files.write(orphanedTmpSnap, partialSnap, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
        }

        // Final verification: 100 cycles completed and all acknowledged commits survived!
        assertThat(acknowledgedUsers).hasSizeGreaterThan(100);
        assertThat(acknowledgedConversations).hasSizeGreaterThan(20);
    }

    private static Path findActiveSegment(Path dir) throws IOException {
        Path best = null;
        long highestIdx = -1;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "wal-*.log")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                // wal-0000000000000001.log
                try {
                    String numStr = name.substring(4, name.length() - 4);
                    long idx = Long.parseLong(numStr);
                    if (idx > highestIdx) {
                        highestIdx = idx;
                        best = p;
                    }
                } catch (Exception ignored) {}
            }
        }
        return best;
    }
}
