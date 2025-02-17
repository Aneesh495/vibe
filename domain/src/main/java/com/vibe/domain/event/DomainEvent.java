package com.vibe.domain.event;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Deterministic domain event produced by applying a committed command to the state machine.
 */
public interface DomainEvent {

    UUID eventId();

    UUID conversationId();

    long seqNumber();

    short eventType();

    String senderUserId();

    long timestamp();

    ByteBuffer encodePayload();
}
