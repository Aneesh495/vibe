package com.vibe.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Striped pool of delivery lanes hashing conversations across dedicated serialization workers.
 */
public final class DeliveryLanePool implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(DeliveryLanePool.class);

    private final int poolSize;
    private final List<DeliveryLane> lanes;

    public DeliveryLanePool(int poolSize) {
        if (poolSize <= 0) {
            throw new IllegalArgumentException("poolSize must be positive: " + poolSize);
        }
        this.poolSize = poolSize;
        this.lanes = new ArrayList<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            lanes.add(new DeliveryLane(i));
        }
        log.info("Initialized DeliveryLanePool with {} serialized lanes", poolSize);
    }

    public DeliveryLane getLane(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        int hash = conversationId.hashCode() & 0x7FFFFFFF;
        return lanes.get(hash % poolSize);
    }

    public int poolSize() {
        return poolSize;
    }

    @Override
    public void close() {
        log.info("Shutting down DeliveryLanePool...");
        for (DeliveryLane lane : lanes) {
            lane.close();
        }
    }
}
