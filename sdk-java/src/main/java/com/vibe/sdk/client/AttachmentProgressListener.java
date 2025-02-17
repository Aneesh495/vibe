package com.vibe.sdk.client;

import java.util.UUID;

/**
 * Progress callback for streaming attachment uploads and downloads.
 */
public interface AttachmentProgressListener {

    void onProgress(UUID attachmentId, long bytesTransferred, long totalBytes, double percent);

    void onComplete(UUID attachmentId);

    void onError(UUID attachmentId, Throwable cause);
}
