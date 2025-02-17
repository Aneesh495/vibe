package com.vibe.domain.idempotency;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks committed command idempotency records to ensure deduplication on reconnects
 * and detect conflicting reuse of clientMessageIds.
 */
public final class IdempotencyTracker {

    private static final int MAX_RECORDS = 100_000;

    private final Map<IdempotencyKey, IdempotencyRecord> records = new ConcurrentHashMap<>();
    private final Deque<IdempotencyKey> evictionQueue = new ArrayDeque<>();

    public IdempotencyTracker() {
    }

    public enum Status {
        NEW_COMMAND,
        IDEMPOTENT_REPLAY,
        CONFLICT
    }

    public record CheckResult(
            Status status,
            IdempotencyRecord record
    ) {
        public static CheckResult newCommand() {
            return new CheckResult(Status.NEW_COMMAND, null);
        }

        public static CheckResult replay(IdempotencyRecord record) {
            return new CheckResult(Status.IDEMPOTENT_REPLAY, record);
        }

        public static CheckResult conflict() {
            return new CheckResult(Status.CONFLICT, null);
        }
    }

    /**
     * Checks if a command with the given key was already processed.
     */
    public CheckResult check(IdempotencyKey key, int contentHash) {
        if (!key.isIdempotencyEnabled()) {
            return CheckResult.newCommand();
        }

        IdempotencyRecord existing = records.get(key);
        if (existing == null) {
            return CheckResult.newCommand();
        }

        if (existing.contentHash() == contentHash) {
            return CheckResult.replay(existing);
        } else {
            return CheckResult.conflict();
        }
    }

    /**
     * Records a newly committed command result.
     */
    public synchronized void record(IdempotencyRecord record) {
        if (!record.key().isIdempotencyEnabled()) {
            return;
        }

        if (records.size() >= MAX_RECORDS) {
            IdempotencyKey oldest = evictionQueue.pollFirst();
            if (oldest != null) {
                records.remove(oldest);
            }
        }

        records.put(record.key(), record);
        evictionQueue.addLast(record.key());
    }

    public Map<IdempotencyKey, IdempotencyRecord> allRecords() {
        return Collections.unmodifiableMap(records);
    }

    public synchronized void restore(List<IdempotencyRecord> list) {
        records.clear();
        evictionQueue.clear();
        if (list != null) {
            for (IdempotencyRecord rec : list) {
                record(rec);
            }
        }
    }
}
