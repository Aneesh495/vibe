package com.vibe.domain.entity;

/**
 * Membership role within a group conversation.
 */
public enum MemberRole {
    OWNER(3),
    ADMIN(2),
    MEMBER(1);

    private final int level;

    MemberRole(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public boolean canManage(MemberRole other) {
        return this.level > other.level;
    }
}
