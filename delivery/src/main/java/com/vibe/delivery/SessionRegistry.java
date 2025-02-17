package com.vibe.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of active user connections and multi-device sessions.
 */
public final class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    // userId -> (deviceId -> UserSession)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, UserSession>> sessionsByUser = new ConcurrentHashMap<>();

    // connectionId -> UserSession
    private final ConcurrentHashMap<Long, UserSession> sessionsByConnectionId = new ConcurrentHashMap<>();

    private final DeliveryMetrics metrics;

    public SessionRegistry(DeliveryMetrics metrics) {
        this.metrics = metrics;
    }

    public void registerSession(UserSession session) {
        sessionsByUser.computeIfAbsent(session.userId(), k -> new ConcurrentHashMap<>())
                .put(session.deviceId(), session);
        sessionsByConnectionId.put(session.connection().connectionId(), session);
        metrics.sessionConnected();
        log.info("Registered session for user={} device={} connectionId={}",
                session.userId(), session.deviceId(), session.connection().connectionId());
    }

    public UserSession unregisterSession(long connectionId) {
        UserSession session = sessionsByConnectionId.remove(connectionId);
        if (session != null) {
            ConcurrentHashMap<String, UserSession> userDevices = sessionsByUser.get(session.userId());
            if (userDevices != null) {
                userDevices.remove(session.deviceId());
                if (userDevices.isEmpty()) {
                    sessionsByUser.remove(session.userId());
                }
            }
            metrics.sessionDisconnected();
            log.info("Unregistered session for user={} device={} connectionId={}",
                    session.userId(), session.deviceId(), connectionId);
        }
        return session;
    }

    public List<UserSession> getSessionsForUser(String userId) {
        Map<String, UserSession> userDevices = sessionsByUser.get(userId);
        if (userDevices == null || userDevices.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(userDevices.values());
    }

    public UserSession getSessionByConnectionId(long connectionId) {
        return sessionsByConnectionId.get(connectionId);
    }

    public Collection<UserSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessionsByConnectionId.values());
    }

    public int activeUserCount() {
        return sessionsByUser.size();
    }

    public int activeSessionCount() {
        return sessionsByConnectionId.size();
    }
}
