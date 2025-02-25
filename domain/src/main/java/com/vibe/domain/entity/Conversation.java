package com.vibe.domain.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain entity representing an isolated conversation channel with its own monotonic sequence space.
 */
public final class Conversation {

    private final UUID conversationId;
    private final ConversationType type;
    private String name;
    private final long createdAt;
    private final String creatorUserId;
    private long currentSeq;
    private HistoryVisibility historyVisibility = HistoryVisibility.ALL;
    private final Map<String, ConversationMember> members = new ConcurrentHashMap<>();

    public HistoryVisibility historyVisibility() {
        return historyVisibility;
    }

    public void setHistoryVisibility(HistoryVisibility historyVisibility) {
        this.historyVisibility = Objects.requireNonNull(historyVisibility);
    }

    public Conversation(
            UUID conversationId,
            ConversationType type,
            String name,
            long createdAt,
            String creatorUserId,
            long currentSeq
    ) {
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.name = name != null ? name : "";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.creatorUserId = Objects.requireNonNull(creatorUserId, "creatorUserId must not be null");
        this.currentSeq = currentSeq;
    }

    public static Conversation createDirect(UUID id, String userA, String userB) {
        long now = System.currentTimeMillis();
        Conversation conv = new Conversation(id, ConversationType.DIRECT, "", now, userA, 0L);
        conv.addMember(ConversationMember.of(userA, MemberRole.MEMBER));
        conv.addMember(ConversationMember.of(userB, MemberRole.MEMBER));
        return conv;
    }

    public static Conversation createGroup(UUID id, String title, String creatorUserId, List<String> initialMembers) {
        long now = System.currentTimeMillis();
        Conversation conv = new Conversation(id, ConversationType.GROUP, title, now, creatorUserId, 0L);
        conv.addMember(ConversationMember.of(creatorUserId, MemberRole.OWNER));
        if (initialMembers != null) {
            for (String memberId : initialMembers) {
                if (!memberId.equals(creatorUserId)) {
                    conv.addMember(ConversationMember.of(memberId, MemberRole.MEMBER));
                }
            }
        }
        return conv;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public ConversationType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public long createdAt() {
        return createdAt;
    }

    public String creatorUserId() {
        return creatorUserId;
    }

    public synchronized long currentSeq() {
        return currentSeq;
    }

    /**
     * Atomically advances and returns the next conversation sequence number during state-machine mutation.
     */
    public synchronized long nextSeq() {
        return ++currentSeq;
    }

    public synchronized void setCurrentSeq(long seq) {
        if (seq > this.currentSeq) {
            this.currentSeq = seq;
        }
    }

    public Map<String, ConversationMember> members() {
        return Collections.unmodifiableMap(members);
    }

    public boolean isMember(String userId) {
        return members.containsKey(userId);
    }

    public ConversationMember getMember(String userId) {
        return members.get(userId);
    }

    public void addMember(ConversationMember member) {
        members.put(member.userId(), member);
    }

    public void removeMember(String userId) {
        members.remove(userId);
    }

    public ConversationSnapshot toSnapshot() {
        List<MemberSnapshot> memberSnapshots = new ArrayList<>();
        members.values().forEach(m -> memberSnapshots.add(
                new MemberSnapshot(m.userId(), m.role(), m.joinedAt(), m.lastDeliveredSeq(), m.lastReadSeq())
        ));
        return new ConversationSnapshot(conversationId, type, name, createdAt, creatorUserId, currentSeq, memberSnapshots);
    }

    public record MemberSnapshot(
            String userId,
            MemberRole role,
            long joinedAt,
            long lastDeliveredSeq,
            long lastReadSeq
    ) {
    }

    public record ConversationSnapshot(
            UUID conversationId,
            ConversationType type,
            String name,
            long createdAt,
            String creatorUserId,
            long currentSeq,
            List<MemberSnapshot> members
    ) {
    }
}
