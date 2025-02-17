package com.vibe.delivery;

import com.vibe.domain.event.DomainEvent;
import com.vibe.transport.nio.NioConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeliveryConstants {
    private static final Logger log = LoggerFactory.getLogger(DeliveryConstants.class);

    public static final int DEFAULT_LANE_COUNT = 64;
    public static final int MAX_PENDING_DELIVERIES_PER_SESSION = 2048;
    public static final int MAX_PENDING_BYTES_PER_SESSION = 4 * 1024 * 1024; // 4 MiB
}
