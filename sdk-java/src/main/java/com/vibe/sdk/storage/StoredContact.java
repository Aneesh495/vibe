package com.vibe.sdk.storage;

public record StoredContact(
        String userId,
        String username,
        String displayName,
        String bio,
        String avatarUrl,
        boolean isFriend,
        boolean isBlocked
) {
}
