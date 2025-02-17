package com.vibe.domain.entity;

import java.util.Objects;

/**
 * Represents a user's participation and delivery cursors in a conversation.
 */
public final class ConversationMember {

    private final String userId;
    private MemberRole role;
    private final long joinedAt;
    private long lastDeliveredSeq;
    private long lastReadSeq;

    public ConversationMember(String userId, MemberRole role, long joinedAt, long lastDeliveredSeq, long lastReadSeq) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.joinedAt = joinedAt > 0 ? joinedAt : System.currentTimeMillis();
        this.lastDeliveredSeq = lastDeliveredSeq;
        this.lastReadSeq = lastReadSeq;
    }

    public static ConversationMember of(String userId, MemberRole role) {
        return new ConversationMember(userId, role, System.currentTimeMillis(), 0L, 0L);
    }

    public String userId() {
        return userId;
    }

    public MemberRole role() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = Objects.requireNonNull(role);
    }

    public long joinedAt() {
        return joinedAt;
    }

    public long lastDeliveredSeq() {
        return lastDeliveredSeq;
    }

    public void setLastDeliveredSeq(long lastDeliveredSeq) {
        if (lastDeliveredSeq > this.lastDeliveredSeq) {
            this.lastDeliveredSeq = lastDeliveredSeq;
        }
    }

    public long lastReadSeq() {
        return lastReadSeq;
    }

    public void setLastReadSeq(long lastReadSeq) {
        if (lastReadSeq > this.lastReadSeq) {
            this.lastReadSeq = lastReadSeq;
            if (this.lastReadSeq > this.lastDeliveredSeq) {
                this.lastDeliveredSeq = this.lastReadSeq;
            }
        }
    }
}
