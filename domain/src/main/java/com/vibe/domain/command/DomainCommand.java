package com.vibe.domain.command;

import com.vibe.protocol.payload.CommandType;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Deterministic command committed to the authoritative durability log
 * and applied to the domain state machine.
 */
public interface DomainCommand {

    CommandType type();

    UUID commandId();

    long timestamp();

    String callerUserId();

    String callerDeviceId();

    String clientMessageId();

    /**
     * Hash code of payload parameters used to detect duplicate idempotent retries versus conflicting reuse.
     */
    int contentHash();

    /**
     * Serializes this command into a ByteBuffer for WAL or Raft log storage.
     */
    ByteBuffer encode();
}
