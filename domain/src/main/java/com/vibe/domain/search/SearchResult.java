package com.vibe.domain.search;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of a search query against the conversation search index.
 */
public record SearchResult(
        String query,
        List<SearchMatch> matches,
        long searchDurationNanos,
        long lastIndexedSeq,
        boolean isFresh
) {
    public SearchResult {
        Objects.requireNonNull(query, "query must not be null");
        matches = List.copyOf(matches);
    }

    public record SearchMatch(
            UUID messageId,
            UUID conversationId,
            long seq,
            String senderUserId,
            String contentSnippet,
            long sentAt,
            double relevanceScore
    ) {
    }
}
