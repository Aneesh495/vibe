package com.vibe.desktop.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record ProtocolFrameLogEntry(
        long timestamp,
        String direction,
        String frameType,
        long correlationId,
        int payloadSize,
        String summary
) {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    public String formattedTime() {
        return TIME_FMT.format(Instant.ofEpochMilli(timestamp));
    }
}
