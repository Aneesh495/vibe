package com.vibe.domain.reference;

import com.vibe.domain.command.*;
import com.vibe.domain.entity.ConversationType;

import java.util.*;

/**
 * Deterministic pseudorandom generator for comprehensive domain operation histories.
 */
public final class HistoryGenerator {

    private final Random rng;
    private final long seed;

    public HistoryGenerator(long seed) {
        this.seed = seed;
        this.rng = new Random(seed);
    }

    public long seed() {
        return seed;
    }

    public List<DomainCommand> generateHistory(int targetOperations, int userCount, int conversationCount) {
        List<DomainCommand> commands = new ArrayList<>(targetOperations);

        // 1. Initial User Registrations
        List<String> users = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            String uname = "user_" + i;
            users.add(uname);
            commands.add(new RegisterUserCommand(
                    UUID.randomUUID(), 1000L + i, uname, "device_" + i, "req_reg_" + i,
                    uname, "$2a$10$eO1vV6m5K...", "User " + i, "Bio " + i, ""
            ));
        }

        // 2. Initial Conversations
        List<UUID> convIds = new ArrayList<>();
        Map<UUID, List<String>> convMembers = new HashMap<>();
        Set<String> directPairs = new HashSet<>();

        for (int i = 0; i < conversationCount; i++) {
            UUID cid = UUID.randomUUID();
            ConversationType type = (i % 2 == 0) ? ConversationType.GROUP : ConversationType.DIRECT;
            String creator = users.get(rng.nextInt(users.size()));
            List<String> members = new ArrayList<>();
            members.add(creator);

            if (type == ConversationType.DIRECT) {
                String other = users.get((users.indexOf(creator) + 1 + rng.nextInt(users.size() - 1)) % users.size());
                String pairKey = creator.compareTo(other) < 0 ? creator + ":" + other : other + ":" + creator;
                if (directPairs.contains(pairKey)) {
                    // Switch to group if pair already exists
                    type = ConversationType.GROUP;
                    while (members.size() < Math.min(users.size(), 4)) {
                        String u = users.get(rng.nextInt(users.size()));
                        if (!members.contains(u)) members.add(u);
                    }
                } else {
                    directPairs.add(pairKey);
                    members.add(other);
                }
            } else {
                while (members.size() < Math.min(users.size(), 4)) {
                    String u = users.get(rng.nextInt(users.size()));
                    if (!members.contains(u)) members.add(u);
                }
            }

            convIds.add(cid);
            convMembers.put(cid, new ArrayList<>(members));

            commands.add(new CreateConversationCommand(
                    UUID.randomUUID(), 2000L + i, creator, "device_" + creator, "req_cc_" + i,
                    cid, type, "Channel " + i, members
            ));
        }

        // 3. Generate randomized messages, idempotency retries, blocks, edits
        Map<String, String> usedClientIds = new LinkedHashMap<>(); // clientId -> content
        List<String> usedClientIdList = new ArrayList<>();

        while (commands.size() < targetOperations) {
            int opType = rng.nextInt(100);
            UUID convId = convIds.get(rng.nextInt(convIds.size()));
            List<String> members = convMembers.get(convId);
            String user;
            if (rng.nextInt(15) == 0) {
                // Occasional unauthorized sender
                user = users.get(rng.nextInt(users.size()));
            } else {
                // Legitimate conversation member
                user = members.get(rng.nextInt(members.size()));
            }

            if (opType < 75) {
                // Send Message
                String cmsgId = "cmsg_" + rng.nextInt(2000);
                String content = "Message content payload " + rng.nextInt(10000);

                // Maybe reuse an existing client ID with same content (idempotent retry) or different (conflict)
                if (rng.nextInt(12) == 0 && !usedClientIdList.isEmpty()) {
                    String existingId = usedClientIdList.get(rng.nextInt(usedClientIdList.size()));
                    if (rng.nextBoolean()) {
                        content = usedClientIds.get(existingId); // Exact retry
                    } else {
                        content = "Modified conflict payload " + rng.nextInt(9999); // Conflict
                    }
                    cmsgId = existingId;
                } else {
                    usedClientIds.put(cmsgId, content);
                    usedClientIdList.add(cmsgId);
                }

                commands.add(new SendMessageCommand(
                        UUID.randomUUID(), System.currentTimeMillis(), user, "device_" + user, cmsgId,
                        UUID.randomUUID(), convId, content, null
                ));
            } else if (opType < 85) {
                // Block / Unblock action
                String target = users.get(rng.nextInt(users.size()));
                if (!target.equals(user)) {
                    if (rng.nextBoolean()) {
                        commands.add(new BlockUserCommand(UUID.randomUUID(), System.currentTimeMillis(), user, "dev", "b_" + commands.size(), target));
                    } else {
                        commands.add(new UnblockUserCommand(UUID.randomUUID(), System.currentTimeMillis(), user, "dev", "ub_" + commands.size(), target));
                    }
                }
            } else {
                // Duplicate registration attempt
                commands.add(new RegisterUserCommand(
                        UUID.randomUUID(), System.currentTimeMillis(), user, "dev", "dup_" + commands.size(),
                        user, "hash", "Dup", "", ""
                ));
            }
        }

        return commands;
    }
}
