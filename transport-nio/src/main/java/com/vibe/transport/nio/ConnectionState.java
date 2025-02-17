package com.vibe.transport.nio;

/**
 * State of a network connection in the Vibe transport layer.
 */
public enum ConnectionState {
    /** Channel created, awaiting socket connection completion. */
    CONNECTING,

    /** Socket connected, undergoing TLS 1.3 handshake negotiation. */
    HANDSHAKING_TLS,

    /** TLS established, awaiting protocol HANDSHAKE and HANDSHAKE_ACK. */
    HANDSHAKING_PROTOCOL,

    /** Protocol acknowledged, awaiting AUTHENTICATE credentials. */
    AUTHENTICATING,

    /** Fully authenticated and ready for bidirectional commands and events. */
    ESTABLISHED,

    /** Graceful shutdown initiated (draining outbound queue and TLS close_notify). */
    CLOSING,

    /** Socket channel closed and resources released. */
    CLOSED;

    public boolean isActive() {
        return this == HANDSHAKING_TLS
                || this == HANDSHAKING_PROTOCOL
                || this == AUTHENTICATING
                || this == ESTABLISHED;
    }
}
