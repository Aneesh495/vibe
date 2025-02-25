package com.vibe.domain.search;

import java.util.Objects;
import java.util.UUID;

/**
 * Parameters for a search query.
 */
public record SearchQuery(
        String queryingUserId,
        String queryText,
        UUID targetConversationId,
        int limit,
        int offset
) {
    public SearchQuery {
        Objects.requireNonNull(queryingUserId, "queryingUserId must not be null");
        Objects.requireNonNull(queryText, "queryText must not be null");
        if (limit <= 0) limit = 20;
        if (limit > 100) limit = 100;
        if (offset < 0) offset = 0;
    }

    public static SearchQuery of(String queryingUserId, String queryText) {
        return new SearchQuery(queryingUserId, queryText, null, 20, 0);
    }

    public static SearchQuery inConversation(String queryingUserId, UUID convId, String queryText) {
        return new SearchQuery(queryingUserId, queryText, convId, 20, 0);
    }
}
