package com.vibe.domain.entity;

import java.util.Objects;

/**
 * Domain entity representing an authenticated user account.
 */
public final class User {

    private final String userId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String bio;
    private String avatarUrl;
    private final long createdAt;
    private UserStatus status;
    private long lastSeenAt;

    public User(
            String userId,
            String username,
            String passwordHash,
            String displayName,
            String bio,
            String avatarUrl,
            long createdAt,
            UserStatus status,
            long lastSeenAt
    ) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.displayName = displayName != null ? displayName : username;
        this.bio = bio != null ? bio : "";
        this.avatarUrl = avatarUrl != null ? avatarUrl : "default.png";
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.status = status != null ? status : UserStatus.OFFLINE;
        this.lastSeenAt = lastSeenAt > 0 ? lastSeenAt : this.createdAt;
    }

    public static User createNew(String userId, String username, String passwordHash, String displayName, String bio, String avatarUrl) {
        long now = System.currentTimeMillis();
        return new User(userId, username, passwordHash, displayName, bio, avatarUrl, now, UserStatus.OFFLINE, now);
    }

    public String userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public void setUsername(String username) {
        this.username = Objects.requireNonNull(username);
    }

    public String passwordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String bio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public long createdAt() {
        return createdAt;
    }

    public UserStatus status() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    /**
     * Creates a safe view of the user profile without sensitive fields (e.g. passwordHash).
     */
    public UserSnapshot toSnapshot() {
        return new UserSnapshot(userId, username, passwordHash, displayName, bio, avatarUrl, createdAt, status, lastSeenAt);
    }

    public record UserSnapshot(
            String userId,
            String username,
            String passwordHash,
            String displayName,
            String bio,
            String avatarUrl,
            long createdAt,
            UserStatus status,
            long lastSeenAt
    ) {
    }

    public record UserProfile(
            String userId,
            String username,
            String displayName,
            String bio,
            String avatarUrl,
            long createdAt,
            UserStatus status,
            long lastSeenAt
    ) {
    }
}
