package com.vibe.domain.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages bidirectional friendship associations and unidirectional block relationships.
 */
public final class SocialGraph {

    private final Map<String, Set<String>> friends = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> blocked = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> pendingRequests = new ConcurrentHashMap<>();

    public SocialGraph() {
    }

    public synchronized boolean sendFriendRequest(String fromUser, String toUser) {
        if (fromUser.equals(toUser) || isBlockedEitherWay(fromUser, toUser) || isFriend(fromUser, toUser)) {
            return false;
        }
        // If the other user already requested friendship, automatically establish mutual friend relation
        Set<String> fromUserInbox = pendingRequests.get(fromUser);
        if (fromUserInbox != null && fromUserInbox.remove(toUser)) {
            addFriend(fromUser, toUser);
            return true;
        }
        pendingRequests.computeIfAbsent(toUser, k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(fromUser);
        return true;
    }

    public synchronized boolean acceptFriendRequest(String recipient, String requester) {
        Set<String> requests = pendingRequests.get(recipient);
        if (requests != null && requests.remove(requester)) {
            if (!isBlockedEitherWay(recipient, requester)) {
                addFriend(recipient, requester);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean rejectFriendRequest(String recipient, String requester) {
        Set<String> requests = pendingRequests.get(recipient);
        return requests != null && requests.remove(requester);
    }

    public Set<String> getPendingRequests(String userId) {
        Set<String> reqs = pendingRequests.get(userId);
        return reqs != null ? Collections.unmodifiableSet(new LinkedHashSet<>(reqs)) : Collections.emptySet();
    }

    public synchronized void addFriend(String userA, String userB) {
        if (userA.equals(userB)) {
            return;
        }
        friends.computeIfAbsent(userA, k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(userB);
        friends.computeIfAbsent(userB, k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(userA);
    }

    public synchronized void removeFriend(String userA, String userB) {
        Set<String> fA = friends.get(userA);
        if (fA != null) {
            fA.remove(userB);
        }
        Set<String> fB = friends.get(userB);
        if (fB != null) {
            fB.remove(userA);
        }
    }

    public boolean isFriend(String userA, String userB) {
        Set<String> f = friends.get(userA);
        return f != null && f.contains(userB);
    }

    public Set<String> getFriends(String userId) {
        Set<String> f = friends.get(userId);
        return f != null ? Collections.unmodifiableSet(new LinkedHashSet<>(f)) : Collections.emptySet();
    }

    public synchronized void blockUser(String blocker, String blockedUser) {
        if (blocker.equals(blockedUser)) {
            return;
        }
        blocked.computeIfAbsent(blocker, k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(blockedUser);
        // Automatically unfriend if they were friends
        removeFriend(blocker, blockedUser);
    }

    public synchronized void unblockUser(String blocker, String blockedUser) {
        Set<String> b = blocked.get(blocker);
        if (b != null) {
            b.remove(blockedUser);
        }
    }

    public boolean isBlocked(String blocker, String blockedUser) {
        Set<String> b = blocked.get(blocker);
        return b != null && b.contains(blockedUser);
    }

    public boolean isBlockedEitherWay(String userA, String userB) {
        return isBlocked(userA, userB) || isBlocked(userB, userA);
    }

    public Set<String> getBlocked(String userId) {
        Set<String> b = blocked.get(userId);
        return b != null ? Collections.unmodifiableSet(new LinkedHashSet<>(b)) : Collections.emptySet();
    }

    public SocialGraphSnapshot toSnapshot() {
        Map<String, List<String>> friendsCopy = new HashMap<>();
        friends.forEach((u, set) -> friendsCopy.put(u, new ArrayList<>(set)));

        Map<String, List<String>> blockedCopy = new HashMap<>();
        blocked.forEach((u, set) -> blockedCopy.put(u, new ArrayList<>(set)));

        Map<String, List<String>> pendingCopy = new HashMap<>();
        pendingRequests.forEach((u, set) -> pendingCopy.put(u, new ArrayList<>(set)));

        return new SocialGraphSnapshot(friendsCopy, blockedCopy, pendingCopy);
    }

    public synchronized void restore(SocialGraphSnapshot snapshot) {
        friends.clear();
        blocked.clear();
        pendingRequests.clear();
        if (snapshot != null) {
            if (snapshot.friends() != null) {
                snapshot.friends().forEach((u, list) ->
                        friends.put(u, Collections.synchronizedSet(new LinkedHashSet<>(list))));
            }
            if (snapshot.blocked() != null) {
                snapshot.blocked().forEach((u, list) ->
                        blocked.put(u, Collections.synchronizedSet(new LinkedHashSet<>(list))));
            }
            if (snapshot.pendingRequests() != null) {
                snapshot.pendingRequests().forEach((u, list) ->
                        pendingRequests.put(u, Collections.synchronizedSet(new LinkedHashSet<>(list))));
            }
        }
    }

    public record SocialGraphSnapshot(
            Map<String, List<String>> friends,
            Map<String, List<String>> blocked,
            Map<String, List<String>> pendingRequests
    ) {
    }
}
