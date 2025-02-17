package com.vibe.storage.local;

/**
 * Thrown when persistent committed history is corrupted.
 * Must fail loudly rather than silently ignoring data loss.
 */
public final class StorageCorruptionException extends StorageException {

    private static final long serialVersionUID = 1L;

    public StorageCorruptionException(String message) {
        super(message);
    }

    public StorageCorruptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
