package com.vibe.domain.search;

import com.vibe.domain.entity.Conversation;
import com.vibe.domain.entity.ConversationMember;
import com.vibe.domain.entity.HistoryVisibility;
import com.vibe.domain.entity.Message;
import com.vibe.domain.state.DomainStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Thread-safe materialized search index for conversation messages.
 *
 * <p>Enforces strict authorization filtering: users can only search within
 * conversations where they hold active membership, messages from blocked users
 * are omitted, and historical visibility rules are respected.
 */
public final class ConversationSearchIndex {

    private static final Logger log = LoggerFactory.getLogger(ConversationSearchIndex.class);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s\\p{Punct}]+");

    public record IndexEntry(
            UUID messageId,
            UUID conversationId,
            long seq,
            String senderUserId,
            String content,
            long sentAt
    ) {
    }

    private final Map<String, Set<IndexEntry>> termIndex = new ConcurrentHashMap<>();
    private final Map<UUID, IndexEntry> entriesByMessageId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastIndexedSeqByConv = new ConcurrentHashMap<>();

    public ConversationSearchIndex() {
    }

    /**
     * Incrementally indexes a new committed message.
     */
    public synchronized void indexMessage(Message message) {
        if (message == null || message.isDeleted()) {
            return;
        }

        IndexEntry entry = new IndexEntry(
                message.messageId(),
                message.conversationId(),
                message.seq(),
                message.senderUserId(),
                message.content(),
                message.sentAt()
        );

        entriesByMessageId.put(entry.messageId(), entry);
        lastIndexedSeqByConv.compute(entry.conversationId(), (k, v) -> v == null ? entry.seq() : Math.max(v, entry.seq()));

        Set<String> terms = tokenize(entry.content());
        for (String term : terms) {
            termIndex.computeIfAbsent(term, k -> Collections.synchronizedSet(new HashSet<>())).add(entry);
        }
    }

    /**
     * Updates an edited message in the search index.
     */
    public synchronized void updateMessage(UUID messageId, String newContent, long editedAt) {
        IndexEntry existing = entriesByMessageId.get(messageId);
        if (existing == null) {
            return;
        }

        removeMessage(messageId);

        IndexEntry updated = new IndexEntry(
                existing.messageId(),
                existing.conversationId(),
                existing.seq(),
                existing.senderUserId(),
                newContent,
                existing.sentAt()
        );

        entriesByMessageId.put(updated.messageId(), updated);
        Set<String> terms = tokenize(updated.content());
        for (String term : terms) {
            termIndex.computeIfAbsent(term, k -> Collections.synchronizedSet(new HashSet<>())).add(updated);
        }
    }

    /**
     * Removes a deleted message from the search index.
     */
    public synchronized void removeMessage(UUID messageId) {
        IndexEntry existing = entriesByMessageId.remove(messageId);
        if (existing == null) {
            return;
        }

        Set<String> terms = tokenize(existing.content());
        for (String term : terms) {
            Set<IndexEntry> entries = termIndex.get(term);
            if (entries != null) {
                entries.remove(existing);
                if (entries.isEmpty()) {
                    termIndex.remove(term);
                }
            }
        }
    }

    /**
     * Executes an authorized search query against the materialized index.
     */
    public SearchResult search(SearchQuery query, DomainStateMachine state) {
        long startNanos = System.nanoTime();
        Objects.requireNonNull(query, "SearchQuery must not be null");
        Objects.requireNonNull(state, "DomainStateMachine must not be null");

        Set<String> queryTerms = tokenize(query.queryText());
        if (queryTerms.isEmpty()) {
            return new SearchResult(query.queryText(), List.of(), System.nanoTime() - startNanos, 0L, true);
        }

        Map<IndexEntry, Integer> termMatchCounts = new HashMap<>();
        for (String term : queryTerms) {
            Set<IndexEntry> matched = termIndex.get(term);
            if (matched != null) {
                synchronized (matched) {
                    for (IndexEntry entry : matched) {
                        termMatchCounts.merge(entry, 1, Integer::sum);
                    }
                }
            }
        }

        List<SearchResult.SearchMatch> matches = new ArrayList<>();
        long maxIndexedSeq = 0L;
        boolean allFresh = true;

        for (Map.Entry<IndexEntry, Integer> e : termMatchCounts.entrySet()) {
            IndexEntry entry = e.getKey();
            int matchCount = e.getValue();

            // 1. Target conversation filter if specified
            if (query.targetConversationId() != null && !query.targetConversationId().equals(entry.conversationId())) {
                continue;
            }

            // 2. Authorization check: querying user must be an active member
            Conversation conv = state.getConversation(entry.conversationId());
            if (conv == null || !conv.isMember(query.queryingUserId())) {
                continue;
            }

            // 3. Block check: omit messages from users who block or are blocked by searcher
            if (state.socialGraph().isBlockedEitherWay(query.queryingUserId(), entry.senderUserId())) {
                continue;
            }

            // 4. Historical visibility check
            if (conv.historyVisibility() == HistoryVisibility.FROM_JOIN) {
                ConversationMember member = conv.getMember(query.queryingUserId());
                if (member != null && entry.seq() < member.joinedSeq()) {
                    continue;
                }
            }

            // Freshness tracking
            long convLastSeq = conv.currentSeq();
            long convIndexedSeq = lastIndexedSeqByConv.getOrDefault(entry.conversationId(), 0L);
            if (convIndexedSeq < convLastSeq) {
                allFresh = false;
            }
            maxIndexedSeq = Math.max(maxIndexedSeq, convIndexedSeq);

            double score = (double) matchCount / queryTerms.size();
            matches.add(new SearchResult.SearchMatch(
                    entry.messageId(),
                    entry.conversationId(),
                    entry.seq(),
                    entry.senderUserId(),
                    createSnippet(entry.content(), queryTerms),
                    entry.sentAt(),
                    score
            ));
        }

        // Sort by relevance score desc, then sentAt desc
        matches.sort((a, b) -> {
            int c = Double.compare(b.relevanceScore(), a.relevanceScore());
            if (c != 0) return c;
            return Long.compare(b.sentAt(), a.sentAt());
        });

        int fromIndex = Math.min(query.offset(), matches.size());
        int toIndex = Math.min(fromIndex + query.limit(), matches.size());
        List<SearchResult.SearchMatch> paged = matches.subList(fromIndex, toIndex);

        long duration = System.nanoTime() - startNanos;
        return new SearchResult(query.queryText(), paged, duration, maxIndexedSeq, allFresh);
    }

    /**
     * Completely rebuilds the index from the state machine snapshot or replay stream.
     */
    public synchronized void rebuild(DomainStateMachine state) {
        termIndex.clear();
        entriesByMessageId.clear();
        lastIndexedSeqByConv.clear();

        for (Conversation conv : state.allConversations().values()) {
            List<Message> msgs = state.getMessages(conv.conversationId(), 1L, Integer.MAX_VALUE);
            for (Message m : msgs) {
                indexMessage(m);
            }
        }
        log.info("Search index rebuilt: {} messages indexed across {} terms",
                entriesByMessageId.size(), termIndex.size());
    }

    public long getLastIndexedSeq(UUID conversationId) {
        return lastIndexedSeqByConv.getOrDefault(conversationId, 0L);
    }

    public int totalIndexedMessages() {
        return entriesByMessageId.size();
    }

    public int totalIndexedTerms() {
        return termIndex.size();
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        String[] tokens = TOKEN_SPLIT.split(text.toLowerCase());
        Set<String> set = new HashSet<>(tokens.length);
        for (String t : tokens) {
            if (t.length() >= 2 && !STOP_WORDS.contains(t)) {
                set.add(t);
            }
        }
        return set;
    }

    private static String createSnippet(String content, Set<String> matchedTerms) {
        if (content.length() <= 120) {
            return content;
        }
        for (String term : matchedTerms) {
            int idx = content.toLowerCase().indexOf(term);
            if (idx >= 0) {
                int start = Math.max(0, idx - 30);
                int end = Math.min(content.length(), idx + term.length() + 60);
                String prefix = start > 0 ? "..." : "";
                String suffix = end < content.length() ? "..." : "";
                return prefix + content.substring(start, end).trim() + suffix;
            }
        }
        return content.substring(0, 120) + "...";
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "a", "an", "is", "in", "it", "to", "of", "for", "with", "on", "at", "by", "this"
    );
}
