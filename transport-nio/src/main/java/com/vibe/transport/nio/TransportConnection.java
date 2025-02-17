package com.vibe.transport.nio;

import com.vibe.protocol.Frame;

/**
 * Unified connection contract representing an active client transport session.
 *
 * <p>Implemented by both raw TCP {@link NioConnection} and WebSocket bridge adapters.
 */
public interface TransportConnection {

    long connectionId();

    boolean send(Frame frame);

    boolean isOpen();

    boolean isClosed();

    void close();

    String userId();

    void setUserId(String userId);

    String deviceId();

    void setDeviceId(String deviceId);

    String username();

    void setUsername(String username);
}
