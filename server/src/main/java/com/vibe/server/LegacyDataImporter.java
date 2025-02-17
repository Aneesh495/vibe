package com.vibe.server;

import com.vibe.domain.auth.PasswordHasher;
import com.vibe.domain.command.BlockUserCommand;
import com.vibe.domain.command.CreateConversationCommand;
import com.vibe.domain.command.FriendUserCommand;
import com.vibe.domain.command.RegisterUserCommand;
import com.vibe.domain.command.SendMessageCommand;
import com.vibe.domain.durability.DurabilityAdapter;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Migrates legacy flat-file data from Vibe v1 (Database/Data/{userInfo,friends,blocked,msgs}.txt)
 * into durably committed domain commands under Vibe v2 consensus/WAL.
 */
public final class LegacyDataImporter {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataImporter.class);

    public record ImportReport(
            int usersImported,
            int friendshipsImported,
            int blocksImported,
            int messagesImported,
            List<String> errors
    ) {}

    private final DurabilityAdapter durabilityAdapter;

    public LegacyDataImporter(DurabilityAdapter durabilityAdapter) {
        this.durabilityAdapter = Objects.requireNonNull(durabilityAdapter);
    }

    /**
     * Executes legacy data import from directory containing legacy flat files.
     */
    public ImportReport importData(Path legacyDir) throws Exception {
        log.info("Starting legacy data migration from {}", legacyDir);
        List<String> errors = new ArrayList<>();
        Map<String, String> usernameToUserId = new HashMap<>();

        int usersImported = 0;
        int friendshipsImported = 0;
        int blocksImported = 0;
        int messagesImported = 0;

        // 1. Import Users
        Path userInfoPath = legacyDir.resolve("userInfo.txt");
        if (Files.exists(userInfoPath)) {
            try (BufferedReader reader = Files.newBufferedReader(userInfoPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(" \\| ", -1);
                    if (parts.length >= 2) {
                        String username = parts[0].trim();
                        String rawPassword = parts[1].trim();
                        String avatar = parts.length > 2 ? parts[2].trim() : "default.png";
                        String bio = parts.length > 3 ? parts[3].trim() : "";

                        String userId = "legacy-" + UUID.nameUUIDFromBytes(username.getBytes());
                        String passwordHash = PasswordHasher.hashPassword(rawPassword);

                        RegisterUserCommand cmd = new RegisterUserCommand(
                                UUID.randomUUID(),
                                System.currentTimeMillis(),
                                userId,
                                "importer",
                                "import-user-" + username,
                                username,
                                passwordHash,
                                username,
                                bio,
                                avatar
                        );

                        try {
                            durabilityAdapter.executeCommand(cmd, true).get(5, TimeUnit.SECONDS);
                            usernameToUserId.put(username.toLowerCase(), userId);
                            usersImported++;
                        } catch (Exception e) {
                            errors.add("Failed importing user " + username + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        // Cache existing users from state machine if already imported
        for (User u : durabilityAdapter.stateMachine().allUsers().values()) {
            usernameToUserId.put(u.username().toLowerCase(), u.userId());
        }

        // 2. Import Friendships
        Path friendsPath = legacyDir.resolve("friends.txt");
        if (Files.exists(friendsPath)) {
            try (BufferedReader reader = Files.newBufferedReader(friendsPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(" \\| ", -1);
                    if (parts.length >= 2) {
                        String u1 = parts[0].trim().toLowerCase();
                        String id1 = usernameToUserId.get(u1);
                        if (id1 == null) continue;

                        for (int i = 1; i < parts.length; i++) {
                            String u2 = parts[i].trim().toLowerCase();
                            String id2 = usernameToUserId.get(u2);
                            if (id2 != null) {
                                FriendUserCommand cmd = new FriendUserCommand(
                                        UUID.randomUUID(), System.currentTimeMillis(),
                                        id1, "importer", "friend-" + id1 + "-" + id2, id2
                                );
                                try {
                                    durabilityAdapter.executeCommand(cmd, true).get(5, TimeUnit.SECONDS);
                                    friendshipsImported++;
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }
        }

        // 3. Import Blocked Users
        Path blockedPath = legacyDir.resolve("blocked.txt");
        if (Files.exists(blockedPath)) {
            try (BufferedReader reader = Files.newBufferedReader(blockedPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(" \\| ", -1);
                    if (parts.length >= 2) {
                        String u1 = parts[0].trim().toLowerCase();
                        String id1 = usernameToUserId.get(u1);
                        if (id1 == null) continue;

                        for (int i = 1; i < parts.length; i++) {
                            String u2 = parts[i].trim().toLowerCase();
                            String id2 = usernameToUserId.get(u2);
                            if (id2 != null) {
                                BlockUserCommand cmd = new BlockUserCommand(
                                        UUID.randomUUID(), System.currentTimeMillis(),
                                        id1, "importer", "block-" + id1 + "-" + id2, id2
                                );
                                try {
                                    durabilityAdapter.executeCommand(cmd, true).get(5, TimeUnit.SECONDS);
                                    blocksImported++;
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }
        }

        // 4. Import Historical Messages
        Path msgsPath = legacyDir.resolve("msgs.txt");
        if (Files.exists(msgsPath)) {
            try (BufferedReader reader = Files.newBufferedReader(msgsPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] parts = line.split(" \\| ", -1);
                    if (parts.length >= 3) {
                        String u1 = parts[0].trim().toLowerCase();
                        String u2 = parts[1].trim().toLowerCase();
                        String id1 = usernameToUserId.get(u1);
                        String id2 = usernameToUserId.get(u2);
                        if (id1 == null || id2 == null) continue;

                        // Create direct conversation
                        UUID convId = UUID.nameUUIDFromBytes((id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1).getBytes());
                        CreateConversationCommand createConv = new CreateConversationCommand(
                                UUID.randomUUID(), System.currentTimeMillis(),
                                id1, "importer", "create-conv-" + convId,
                                convId, ConversationType.DIRECT, "Direct", List.of(id1, id2)
                        );
                        try {
                            durabilityAdapter.executeCommand(createConv, true).get(5, TimeUnit.SECONDS);
                        } catch (Exception ignored) {}

                        // Parse messages
                        String msgBlock = parts[2];
                        String[] messages = msgBlock.split(" ; ");
                        for (String rawMsg : messages) {
                            rawMsg = rawMsg.trim();
                            if (rawMsg.isEmpty()) continue;

                            String senderId = id1;
                            if (rawMsg.endsWith("#R#")) {
                                senderId = id2;
                                rawMsg = rawMsg.substring(0, rawMsg.length() - 3);
                            } else if (rawMsg.endsWith("#S#")) {
                                senderId = id1;
                                rawMsg = rawMsg.substring(0, rawMsg.length() - 3);
                            }

                            // Trim suffix "-<num>" if present
                            int lastDash = rawMsg.lastIndexOf('-');
                            String text = lastDash > 0 ? rawMsg.substring(0, lastDash) : rawMsg;

                            SendMessageCommand sendCmd = new SendMessageCommand(
                                    UUID.randomUUID(), System.currentTimeMillis(),
                                    senderId, "importer", "import-msg-" + UUID.randomUUID(),
                                    UUID.randomUUID(), convId, text, null
                            );
                            try {
                                durabilityAdapter.executeCommand(sendCmd, true).get(5, TimeUnit.SECONDS);
                                messagesImported++;
                            } catch (Exception e) {
                                errors.add("Error importing message in conv " + convId + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }

        ImportReport report = new ImportReport(usersImported, friendshipsImported, blocksImported, messagesImported, errors);
        log.info("Legacy migration complete: {} users, {} friendships, {} blocks, {} messages. Errors: {}",
                usersImported, friendshipsImported, blocksImported, messagesImported, errors.size());
        return report;
    }
}
