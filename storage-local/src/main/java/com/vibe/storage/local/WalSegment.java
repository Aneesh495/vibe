package com.vibe.storage.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Encapsulates a single on-disk WAL segment file.
 */
public final class WalSegment implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WalSegment.class);

    private final Path filePath;
    private final FileChannel channel;
    private final SegmentHeader header;
    private long writePosition;
    private long lastIndex;
    private boolean isClosed = false;

    private WalSegment(Path filePath, FileChannel channel, SegmentHeader header, long initialPosition, long lastIndex) {
        this.filePath = Objects.requireNonNull(filePath);
        this.channel = Objects.requireNonNull(channel);
        this.header = Objects.requireNonNull(header);
        this.writePosition = initialPosition;
        this.lastIndex = lastIndex;
    }

    public static WalSegment createNew(Path filePath, long segmentId, long startIndex) throws IOException {
        FileChannel ch = FileChannel.open(
                filePath,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );

        SegmentHeader header = SegmentHeader.create(segmentId, startIndex);
        ByteBuffer headerBuf = ByteBuffer.allocate(WalConstants.SEGMENT_HEADER_SIZE);
        header.encode(headerBuf);
        headerBuf.flip();
        while (headerBuf.hasRemaining()) {
            ch.write(headerBuf);
        }
        ch.force(true);

        return new WalSegment(filePath, ch, header, WalConstants.SEGMENT_HEADER_SIZE, startIndex - 1);
    }

    public static WalSegment openExisting(Path filePath) throws IOException {
        FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE);
        ByteBuffer headerBuf = ByteBuffer.allocate(WalConstants.SEGMENT_HEADER_SIZE);
        while (headerBuf.hasRemaining()) {
            if (ch.read(headerBuf) == -1) {
                ch.close();
                throw new StorageCorruptionException("Premature EOF reading segment header from " + filePath);
            }
        }
        headerBuf.flip();
        SegmentHeader header = SegmentHeader.decode(headerBuf);
        long fileSize = ch.size();

        return new WalSegment(filePath, ch, header, fileSize, header.startIndex() - 1);
    }

    public Path filePath() {
        return filePath;
    }

    public SegmentHeader header() {
        return header;
    }

    public long segmentId() {
        return header.segmentId();
    }

    public long startIndex() {
        return header.startIndex();
    }

    public synchronized long lastIndex() {
        return lastIndex;
    }

    public synchronized void setLastIndex(long lastIndex) {
        if (lastIndex > this.lastIndex) {
            this.lastIndex = lastIndex;
        }
    }

    public synchronized long writePosition() {
        return writePosition;
    }

    /**
     * Appends a record to the segment and returns the starting file offset of the record.
     */
    public synchronized long append(WalRecord record) throws IOException {
        if (isClosed) {
            throw new StorageException("Segment is closed: " + filePath);
        }

        long recordOffset = writePosition;
        ByteBuffer buf = ByteBuffer.allocate(record.totalSize());
        record.encode(buf);
        buf.flip();

        while (buf.hasRemaining()) {
            writePosition += channel.write(buf, writePosition);
        }

        if (record.logIndex() > lastIndex) {
            lastIndex = record.logIndex();
        }

        return recordOffset;
    }

    /**
     * Flushes buffered writes to disk (fsync).
     */
    public synchronized void force(boolean metadata) throws IOException {
        if (!isClosed && channel.isOpen()) {
            channel.force(metadata);
        }
    }

    /**
     * Truncates the segment to strip torn writes at the tail.
     */
    public synchronized void truncate(long newSize) throws IOException {
        channel.truncate(newSize);
        channel.force(true);
        this.writePosition = newSize;
        log.info("Truncated WAL segment {} to {} bytes", filePath.getFileName(), newSize);
    }

    public synchronized WalRecord readRecord(long offset) throws IOException {
        ByteBuffer headerBuf = ByteBuffer.allocate(WalConstants.RECORD_HEADER_SIZE);
        channel.position(offset);
        while (headerBuf.hasRemaining()) {
            if (channel.read(headerBuf) == -1) {
                return null;
            }
        }
        headerBuf.flip();

        // Read payload length to size buffer
        int headerPos = headerBuf.position();
        headerBuf.position(headerPos + 12);
        int payloadLen = headerBuf.getInt();
        headerBuf.position(headerPos);

        ByteBuffer fullBuf = ByteBuffer.allocate(WalConstants.RECORD_HEADER_SIZE + payloadLen);
        fullBuf.put(headerBuf);
        while (fullBuf.hasRemaining()) {
            if (channel.read(fullBuf) == -1) {
                throw new StorageCorruptionException("Premature EOF reading record body at offset " + offset);
            }
        }
        fullBuf.flip();
        return WalRecord.decode(fullBuf);
    }

    @Override
    public synchronized void close() throws IOException {
        if (!isClosed) {
            isClosed = true;
            try {
                if (channel.isOpen()) {
                    channel.force(true);
                    channel.close();
                }
            } catch (Exception e) {
                log.warn("Error closing segment channel {}: {}", filePath, e.getMessage());
            }
        }
    }
}
