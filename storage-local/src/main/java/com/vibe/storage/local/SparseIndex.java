package com.vibe.storage.local;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * In-memory sparse index mapping logIndex -> (segmentId, byteOffset) for O(1) random access seeks.
 */
public final class SparseIndex {

    private final int indexInterval;
    private final ConcurrentSkipListMap<Long, IndexEntry> index = new ConcurrentSkipListMap<>();

    public record IndexEntry(
            long segmentId,
            long fileOffset,
            long logIndex
    ) {
    }

    public SparseIndex(int indexInterval) {
        this.indexInterval = indexInterval > 0 ? indexInterval : 16;
    }

    public SparseIndex() {
        this(16);
    }

    /**
     * Conditionally indexes a record if it falls on the indexing interval or is the first entry.
     */
    public void record(long segmentId, long fileOffset, long logIndex) {
        if (logIndex == 1 || logIndex % indexInterval == 0) {
            index.put(logIndex, new IndexEntry(segmentId, fileOffset, logIndex));
        }
    }

    /**
     * Looks up the closest indexed entry at or before target logIndex.
     */
    public IndexEntry lookupFloor(long logIndex) {
        Map.Entry<Long, IndexEntry> entry = index.floorEntry(logIndex);
        return entry != null ? entry.getValue() : null;
    }

    public void clear() {
        index.clear();
    }

    public int size() {
        return index.size();
    }
}
