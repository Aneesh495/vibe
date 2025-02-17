package com.vibe.sdk.config;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable configuration for {@link com.vibe.sdk.client.VibeClient}.
 */
public record VibeClientConfig(
        String host,
        int port,
        boolean tlsEnabled,
        SSLContext sslContext,
        String deviceId,
        String clientVersion,
        Path storageDir,
        long heartbeatIntervalMs,
        long ackTimeoutMs,
        long reconnectInitialDelayMs,
        long reconnectMaxDelayMs,
        double reconnectBackoffMultiplier,
        int maxReconnectAttempts
) {
    public VibeClientConfig {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(clientVersion, "clientVersion cannot be null");
        Objects.requireNonNull(storageDir, "storageDir cannot be null");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in 1..65535: " + port);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = "127.0.0.1";
        private int port = 8443;
        private boolean tlsEnabled = false;
        private SSLContext sslContext;
        private String deviceId = "java-sdk-" + UUID.randomUUID();
        private String clientVersion = "vibe-java-sdk-2.0.0";
        private Path storageDir = Path.of(System.getProperty("user.home"), ".vibe-client");
        private long heartbeatIntervalMs = 15_000L;
        private long ackTimeoutMs = 10_000L;
        private long reconnectInitialDelayMs = 500L;
        private long reconnectMaxDelayMs = 30_000L;
        private double reconnectBackoffMultiplier = 2.0;
        private int maxReconnectAttempts = 50;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder tlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
            return this;
        }

        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = deviceId;
            return this;
        }

        public Builder clientVersion(String clientVersion) {
            this.clientVersion = clientVersion;
            return this;
        }

        public Builder storageDir(Path storageDir) {
            this.storageDir = storageDir;
            return this;
        }

        public Builder heartbeatIntervalMs(long intervalMs) {
            this.heartbeatIntervalMs = intervalMs;
            return this;
        }

        public Builder ackTimeoutMs(long timeoutMs) {
            this.ackTimeoutMs = timeoutMs;
            return this;
        }

        public Builder reconnectInitialDelayMs(long delayMs) {
            this.reconnectInitialDelayMs = delayMs;
            return this;
        }

        public Builder reconnectMaxDelayMs(long maxDelayMs) {
            this.reconnectMaxDelayMs = maxDelayMs;
            return this;
        }

        public Builder reconnectBackoffMultiplier(double multiplier) {
            this.reconnectBackoffMultiplier = multiplier;
            return this;
        }

        public Builder maxReconnectAttempts(int maxAttempts) {
            this.maxReconnectAttempts = maxAttempts;
            return this;
        }

        public VibeClientConfig build() {
            return new VibeClientConfig(
                    host,
                    port,
                    tlsEnabled,
                    sslContext,
                    deviceId,
                    clientVersion,
                    storageDir,
                    heartbeatIntervalMs,
                    ackTimeoutMs,
                    reconnectInitialDelayMs,
                    reconnectMaxDelayMs,
                    reconnectBackoffMultiplier,
                    maxReconnectAttempts
            );
        }
    }
}
