package com.vibe.storage.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Crash recovery engine for the segmented write-ahead log.
 *
 * <p>Validates committed prefixes, identifies and truncates torn writes at the log tail,
 * and fails loudly on corruption detected within committed history.
 */
public final class WalRecoveryEngine {

    private static final Logger log = LoggerFactory.getLogger(WalRecoveryEngine.class);

    private WalRecoveryEngine() {
    }

    /**
     * Recovers WAL state across all segments, populating the sparse index and advancing nextLogIndex.
     */
    public static void recover(
            SegmentedWal wal,
            WalSegment activeSegment,
            SparseIndex sparseIndex,
            AtomicLong nextLogIndex
    ) throws IOException {
        long highestIndex = 0L;

        List<WalSegment> sortedSegments = new ArrayList<>(wal.segments().values());
        sortedSegments.sort((a, b) -> Long.compare(a.segmentId(), b.segmentId()));

        for (int i = 0; i < sortedSegments.size(); i++) {
            WalSegment segment = sortedSegments.get(i);
            boolean isTailSegment = (i == sortedSegments.size() - 1);

            long segHighestIndex = recoverSegment(segment, sparseIndex, isTailSegment);
            segment.setLastIndex(segHighestIndex);
            if (segHighestIndex > highestIndex) {
                highestIndex = segHighestIndex;
            }
        }

        nextLogIndex.set(highestIndex + 1);
        log.info("WAL recovery complete: highest committed index={}, nextIndex={}", highestIndex, nextLogIndex.get());
    }

    private static long recoverSegment(
            WalSegment segment,
            SparseIndex sparseIndex,
            boolean isTailSegment
    ) throws IOException {
        Path path = segment.filePath();
        long fileSize = Files.size(path);
        if (fileSize < WalConstants.SEGMENT_HEADER_SIZE) {
            throw new StorageCorruptionException("Segment file " + path + " smaller than header size");
        }

        long offset = WalConstants.SEGMENT_HEADER_SIZE;
        long lastValidOffset = offset;
        long highestIndex = segment.startIndex() - 1;

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer headerBuf = ByteBuffer.allocate(WalConstants.RECORD_HEADER_SIZE);

            while (offset < fileSize) {
                long remainingBytes = fileSize - offset;
                if (remainingBytes < WalConstants.RECORD_HEADER_SIZE) {
                    if (isTailSegment) {
                        // Incomplete record header at EOF: torn tail
                        log.warn("Torn tail detected at offset {} in tail segment {}; truncating to {} bytes",
                                offset, path.getFileName(), lastValidOffset);
                        segment.truncate(lastValidOffset);
                        break;
                    } else {
                        throw new StorageCorruptionException(
                                "Torn record header in non-tail committed segment: " + path.getFileName());
                    }
                }

                headerBuf.clear();
                channel.position(offset);
                channel.read(headerBuf);
                headerBuf.flip();

                short marker = headerBuf.getShort();
                if (marker != WalConstants.RECORD_MARKER) {
                    if (isTailSegment) {
                        log.warn("Invalid record marker 0x{:04X} at tail offset {}; truncating to {}",
                                marker, offset, lastValidOffset);
                        segment.truncate(lastValidOffset);
                        break;
                    } else {
                        throw new StorageCorruptionException(String.format(
                                "Corrupt record marker 0x%04X in committed segment %s at offset %d",
                                marker, path.getFileName(), offset));
                    }
                }

                byte typeCode = headerBuf.get();
                byte version = headerBuf.get();
                long logIndex = headerBuf.getLong();
                int payloadLength = headerBuf.getInt();
                int expectedCrc = headerBuf.getInt();

                int totalRecordSize = WalConstants.RECORD_HEADER_SIZE + payloadLength;
                if (remainingBytes < totalRecordSize) {
                    if (isTailSegment) {
                        // Torn payload at EOF
                        log.warn("Torn payload at EOF offset {} in {}; truncating to {}",
                                offset, path.getFileName(), lastValidOffset);
                        segment.truncate(lastValidOffset);
                        break;
                    } else {
                        throw new StorageCorruptionException(
                                "Incomplete payload in committed history of " + path.getFileName());
                    }
                }

                ByteBuffer fullBuf = ByteBuffer.allocate(totalRecordSize);
                channel.position(offset);
                channel.read(fullBuf);
                fullBuf.flip();

                try {
                    WalRecord record = WalRecord.decode(fullBuf);
                    sparseIndex.record(segment.segmentId(), offset, record.logIndex());
                    if (record.logIndex() > highestIndex) {
                        highestIndex = record.logIndex();
                    }
                    offset += totalRecordSize;
                    lastValidOffset = offset;
                } catch (StorageCorruptionException e) {
                    if (isTailSegment && offset + totalRecordSize == fileSize) {
                        // Corrupt checksum on final uncommitted tail write
                        log.warn("Checksum corruption at tail offset {}; truncating to {}", offset, lastValidOffset);
                        segment.truncate(lastValidOffset);
                        break;
                    } else {
                        // Corruption in committed history!
                        throw new StorageCorruptionException(
                                "Unrecoverable corruption in committed WAL history at offset " + offset, e);
                    }
                }
            }
        }

        return highestIndex;
    }
}
