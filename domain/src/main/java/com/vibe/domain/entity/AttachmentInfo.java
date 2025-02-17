package com.vibe.domain.entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Metadata descriptor for an attachment associated with a message.
 */
public record AttachmentInfo(
        UUID attachmentId,
        String fileName,
        String mimeType,
        long sizeBytes,
        int checksumCRC32C,
        boolean completed
) {

    public AttachmentInfo {
        Objects.requireNonNull(attachmentId, "attachmentId must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");
        mimeType = mimeType != null ? mimeType : "application/octet-stream";
    }
}
