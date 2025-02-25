package com.vibe.domain.entity;

/**
 * Policy governing the historical visibility of messages for newly joined members.
 */
public enum HistoryVisibility {
    /**
     * Newly joined members can view all past messages in the conversation.
     */
    ALL,

    /**
     * Newly joined members can only view messages starting from the sequence number at which they joined.
     */
    FROM_JOIN
}
