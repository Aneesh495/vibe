package com.vibe.protocol.payload;

import com.vibe.protocol.CRC32CUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Payload for ATTACHMENT_CHUNK frame (0x0D).
 */
public record AttachmentChunkPayload(
        UUID attachmentId,
        int chunkIndex,
        int totalChunks,
        int checksumCRC32C,
        byte[] chunkData
) {

    public AttachmentChunkPayload {
        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
        Objects.requireNonNull(chunkData, "chunkData must not be null");
    }

    public static AttachmentChunkPayload create(UUID attachmentId, int chunkIndex, int totalChunks, byte[] chunkData) {
        int crc = CRC32CUtil.compute(chunkData, 0, chunkData.length);
        return new AttachmentChunkPayload(attachmentId, chunkIndex, totalChunks, crc, chunkData);
    }

    public boolean verifyChecksum() {
        int computed = CRC32CUtil.compute(chunkData, 0, chunkData.length);
        return computed == checksumCRC32C;
    }

    public ByteBuffer encode() {
        int size = 16 + 4 + 4 + 4 + PayloadCodecUtil.bytesLength(chunkData);
        ByteBuffer buf = ByteBuffer.allocate(size);
        PayloadCodecUtil.writeUUID(buf, attachmentId);
        buf.putInt(chunkIndex);
        buf.putInt(totalChunks);
        buf.putInt(checksumCRC32C);
        PayloadCodecUtil.writeBytes(buf, chunkData);
        buf.flip();
        return buf;
    }

    public static AttachmentChunkPayload decode(ByteBuffer src) {
        UUID attachmentId = PayloadCodecUtil.readUUID(src);
        int chunkIndex = src.getInt();
        int totalChunks = src.getInt();
        int checksumCRC32C = src.getInt();
        byte[] chunkData = PayloadCodecUtil.readBytes(src);
        return new AttachmentChunkPayload(attachmentId, chunkIndex, totalChunks, checksumCRC32C, chunkData);
    }
}
