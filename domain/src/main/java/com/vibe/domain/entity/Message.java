package com.vibe.domain.entity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain entity representing a persistent, ordered message within a conversation.
 */
public final class Message {

    private final UUID messageId;
    private final UUID conversationId;
    private final long seq;
    private final String senderUserId;
    private final String clientMessageId;
    private String content;
    private final long sentAt;
    private long editedAt;
    private boolean deleted;
    private final Map<String, Set<String>> reactions = new ConcurrentHashMap<>();
    private AttachmentInfo attachment;

    public Message(
            UUID messageId,
            UUID conversationId,
            long seq,
            String senderUserId,
            String clientMessageId,
            String content,
            long sentAt,
            long editedAt,
            boolean deleted,
            AttachmentInfo attachment
    ) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.seq = seq;
        this.senderUserId = Objects.requireNonNull(senderUserId, "senderUserId must not be null");
        this.clientMessageId = clientMessageId != null ? clientMessageId : "";
        this.content = content != null ? content : "";
        this.sentAt = sentAt > 0 ? sentAt : System.currentTimeMillis();
        this.editedAt = editedAt;
        this.deleted = deleted;
        this.attachment = attachment;
    }

    public UUID messageId() {
        return messageId;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public long seq() {
        return seq;
    }

    public String senderUserId() {
        return senderUserId;
    }

    public String clientMessageId() {
        return clientMessageId;
    }

    public String content() {
        return content;
    }

    public long sentAt() {
        return sentAt;
    }

    public long editedAt() {
        return editedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public AttachmentInfo attachment() {
        return attachment;
    }

    public void setAttachment(AttachmentInfo attachment) {
        this.attachment = attachment;
    }

    public Map<String, Set<String>> reactions() {
        return Collections.unmodifiableMap(reactions);
    }

    public void edit(String newContent, long timestamp) {
        if (!deleted) {
            this.content = Objects.requireNonNull(newContent, "content must not be null");
            this.editedAt = timestamp;
        }
    }

    public void delete(long timestamp) {
        this.deleted = true;
        this.content = "[Message deleted]";
        this.editedAt = timestamp;
        this.attachment = null;
    }

    public void addReaction(String emoji, String userId) {
        reactions.computeIfAbsent(emoji, k -> Collections.synchronizedSet(new LinkedHashSet<>())).add(userId);
    }

    public void removeReaction(String emoji, String userId) {
        Set<String> users = reactions.get(emoji);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                reactions.remove(emoji);
            }
        }
    }

    public MessageSnapshot toSnapshot() {
        Map<String, List<String>> copiedReactions = new HashMap<>();
        reactions.forEach((k, v) -> copiedReactions.put(k, new ArrayList<>(v)));
        return new MessageSnapshot(
                messageId, conversationId, seq, senderUserId, clientMessageId,
                content, sentAt, editedAt, deleted, attachment, copiedReactions
        );
    }

    public record MessageSnapshot(
            UUID messageId,
            UUID conversationId,
            long seq,
            String senderUserId,
            String clientMessageId,
            String content,
            long sentAt,
            long editedAt,
            boolean deleted,
            AttachmentInfo attachment,
            Map<String, List<String>> reactions
    ) {
    }
}
