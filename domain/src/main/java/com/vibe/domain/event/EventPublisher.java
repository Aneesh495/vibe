package com.vibe.domain.event;

/**
 * Functional callback interface for publishing committed domain events to the delivery fanout layer.
 */
@FunctionalInterface
public interface EventPublisher {

    void publish(DomainEvent event);
}
