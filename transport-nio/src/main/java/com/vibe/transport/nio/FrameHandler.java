package com.vibe.transport.nio;

import com.vibe.protocol.Frame;

/**
 * Handler interface for dispatching decoded protocol frames received on a connection.
 */
@FunctionalInterface
public interface FrameHandler {

    void handleFrame(TransportConnection connection, Frame frame);
}
