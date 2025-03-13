package com.vibe.domain.reference;

import com.vibe.domain.command.*;
import com.vibe.domain.entity.ConversationType;
import com.vibe.domain.entity.MemberRole;
import com.vibe.protocol.ErrorCode;

import java.util.*;

/**
 * Independent sequential reference model for validating conversation domain linearizability.
 *
 * <p>Serves as the oracle specification for property-based testing and history validation.
 */
public final class SequentialReferenceModel {

    public record ExpectedResult(boolean success, long seq, ErrorCode error) {}

    public static class RefMessage {
        public final UUID messageId;
        public final UUID conversationId;
        public final long seq;
        public final String sender;
        public final String clientMessageId;
        public String content;
        public boolean deleted;
        public final Map<String, Set<String>> reactions = new HashMap<>();

        public RefMessage(UUID messageId, UUID conversationId, long seq, String sender, String clientMessageId, String content) {
            this.messageId = messageId;
            this.conversationId = conversationId;
            this.seq = seq;
            this.sender = sender;
            this.clientMessageId = clientMessageId;
            this.content = content;
            this.deleted = false;
        }
    }

    public static class RefConversation {
        public final UUID id;
        public final ConversationType type;
        public long currentSeq = 0;
        public final Map<String, MemberRole> members = new HashMap<>();
        public final Map<Long, RefMessage> messages = new HashMap<>();

        public RefConversation(UUID id, ConversationType type) {
            this.id = id;
            this.type = type;
        }
    }

    private final Set<String> registeredUsers = new HashSet<>();
    private final Map<UUID, RefConversation> conversations = new HashMap<>();
    private final Map<String, Set<String>> blockedUsers = new HashMap<>(); // blocker -> set of blocked
    private final Map<String, Map<String, Long>> idempotencyCache = new HashMap<>(); // (userId:clientId) -> (contentHash -> seq)

    public synchronized ExpectedResult apply(DomainCommand cmd) {
        if (cmd instanceof RegisterUserCommand reg) {
            if (registeredUsers.contains(reg.username().toLowerCase())) {
                return new ExpectedResult(false, -1, ErrorCode.DOMAIN_USER_ALREADY_EXISTS);
            }
            registeredUsers.add(reg.username().toLowerCase());
            return new ExpectedResult(true, -1, null);
        }

        if (cmd instanceof BlockUserCommand blk) {
            blockedUsers.computeIfAbsent(blk.callerUserId(), k -> new HashSet<>()).add(blk.targetUserId());
            return new ExpectedResult(true, -1, null);
        }

        if (cmd instanceof UnblockUserCommand unblk) {
            Set<String> set = blockedUsers.get(unblk.callerUserId());
            if (set != null) set.remove(unblk.targetUserId());
            return new ExpectedResult(true, -1, null);
        }

        if (cmd instanceof CreateConversationCommand cc) {
            if (cc.convType() == ConversationType.DIRECT) {
                if (cc.memberUserIds().size() != 2) {
                    return new ExpectedResult(false, -1, ErrorCode.DOMAIN_INVALID_INPUT);
                }
                String u1 = cc.memberUserIds().get(0);
                String u2 = cc.memberUserIds().get(1);
                if (isBlocked(u1, u2) || isBlocked(u2, u1)) {
                    return new ExpectedResult(false, -1, ErrorCode.DOMAIN_USER_BLOCKED);
                }
                for (RefConversation existing : conversations.values()) {
                    if (existing.type == ConversationType.DIRECT && existing.members.containsKey(u1) && existing.members.containsKey(u2)) {
                        return new ExpectedResult(true, -1, null);
                    }
                }
            }
            RefConversation rc = new RefConversation(cc.conversationId(), cc.convType());
            if (cc.convType() == ConversationType.DIRECT) {
                for (String m : cc.memberUserIds()) rc.members.put(m, MemberRole.MEMBER);
            } else {
                rc.members.put(cc.callerUserId(), MemberRole.OWNER);
                for (String m : cc.memberUserIds()) {
                    if (!m.equals(cc.callerUserId())) rc.members.put(m, MemberRole.MEMBER);
                }
            }
            conversations.put(cc.conversationId(), rc);
            return new ExpectedResult(true, -1, null);
        }

        if (cmd instanceof SendMessageCommand sm) {
            // Idempotency check
            String idempKey = sm.callerUserId() + ":" + sm.clientMessageId();
            if (!sm.clientMessageId().isEmpty()) {
                Map<String, Long> existing = idempotencyCache.get(idempKey);
                if (existing != null) {
                    String hash = String.valueOf(sm.contentHash());
                    if (existing.containsKey(hash)) {
                        return new ExpectedResult(true, existing.get(hash), null);
                    } else {
                        return new ExpectedResult(false, -1, ErrorCode.DOMAIN_IDEMPOTENCY_CONFLICT);
                    }
                }
            }

            RefConversation conv = conversations.get(sm.conversationId());
            if (conv == null) return new ExpectedResult(false, -1, ErrorCode.DOMAIN_CONVERSATION_NOT_FOUND);
            if (!conv.members.containsKey(sm.callerUserId())) return new ExpectedResult(false, -1, ErrorCode.DOMAIN_NOT_A_MEMBER);

            if (conv.type == ConversationType.DIRECT) {
                for (String m : conv.members.keySet()) {
                    if (!m.equals(sm.callerUserId()) && (isBlocked(sm.callerUserId(), m) || isBlocked(m, sm.callerUserId()))) {
                        return new ExpectedResult(false, -1, ErrorCode.DOMAIN_USER_BLOCKED);
                    }
                }
            }

            long seq = ++conv.currentSeq;
            RefMessage msg = new RefMessage(sm.messageId(), sm.conversationId(), seq, sm.callerUserId(), sm.clientMessageId(), sm.content());
            conv.messages.put(seq, msg);

            if (!sm.clientMessageId().isEmpty()) {
                idempotencyCache.computeIfAbsent(idempKey, k -> new HashMap<>()).put(String.valueOf(sm.contentHash()), seq);
            }

            return new ExpectedResult(true, seq, null);
        }

        return new ExpectedResult(true, -1, null);
    }

    private boolean isBlocked(String u1, String u2) {
        Set<String> b = blockedUsers.get(u1);
        return b != null && b.contains(u2);
    }

    public RefConversation getConversation(UUID id) {
        return conversations.get(id);
    }
}
