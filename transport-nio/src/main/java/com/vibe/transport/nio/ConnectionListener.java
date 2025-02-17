package com.vibe.transport.nio;

/**
 * Lifecycle listener for network connections.
 */
public interface ConnectionListener {

    void onConnected(TransportConnection connection);

    default void onStateChanged(TransportConnection connection, ConnectionState oldState, ConnectionState newState) {}

    void onClosed(TransportConnection connection, Throwable cause);
}
