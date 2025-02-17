package com.vibe.desktop.model;

import com.vibe.protocol.payload.ConversationEventPayload;
import com.vibe.sdk.client.VibeClient;
import com.vibe.sdk.client.VibeClientListener;
import com.vibe.sdk.storage.StoredContact;
import com.vibe.sdk.storage.StoredConversation;
import com.vibe.sdk.storage.StoredMessage;
import com.vibe.transport.nio.ConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central UI state model driving the Vibe Desktop Client and Operational Inspector.
 */
public final class DesktopState implements VibeClientListener {

    private static final Logger log = LoggerFactory.getLogger(DesktopState.class);
    private static final int MAX_FRAME_LOG_ENTRIES = 500;

    private VibeClient client;
    private ConnectionState connectionState = ConnectionState.CLOSED;
    private String currentUsername = "";

    // Swing Models
    private final DefaultListModel<StoredConversation> conversationListModel = new DefaultListModel<>();
    private final DefaultListModel<StoredMessage> messageListModel = new DefaultListModel<>();
    private final DefaultListModel<StoredContact> contactsListModel = new DefaultListModel<>();

    private StoredConversation selectedConversation;

    // Frame Log Table Model
    private final List<ProtocolFrameLogEntry> frameLogs = new ArrayList<>();
    private final FrameLogTableModel frameLogTableModel = new FrameLogTableModel();

    // Change Listeners
    public interface StateListener {
        void onStateChanged();
    }
    private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();

    public DesktopState() {}

    public void setClient(VibeClient client) {
        if (this.client != null) {
            this.client.removeListener(this);
        }
        this.client = client;
        if (this.client != null) {
            this.client.addListener(this);
        }
    }

    public VibeClient getClient() {
        return client;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public ConnectionState getConnectionState() {
        return connectionState;
    }

    public DefaultListModel<StoredConversation> getConversationListModel() {
        return conversationListModel;
    }

    public DefaultListModel<StoredMessage> getMessageListModel() {
        return messageListModel;
    }

    public DefaultListModel<StoredContact> getContactsListModel() {
        return contactsListModel;
    }

    public StoredConversation getSelectedConversation() {
        return selectedConversation;
    }

    public FrameLogTableModel getFrameLogTableModel() {
        return frameLogTableModel;
    }

    public void addStateListener(StateListener listener) {
        stateListeners.add(listener);
    }

    public void selectConversation(StoredConversation conv) {
        this.selectedConversation = conv;
        refreshMessagesForSelected();
        notifyStateChanged();
    }

    public void refreshConversations() {
        if (client == null) return;
        SwingUtilities.invokeLater(() -> {
            try {
                List<StoredConversation> list = client.storage().getAllConversations();
                conversationListModel.clear();
                for (StoredConversation c : list) {
                    conversationListModel.addElement(c);
                }
                notifyStateChanged();
            } catch (Exception e) {
                log.warn("Error refreshing conversations: {}", e.getMessage());
            }
        });
    }

    public void refreshMessagesForSelected() {
        if (client == null || selectedConversation == null) {
            SwingUtilities.invokeLater(messageListModel::clear);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                List<StoredMessage> messages = client.storage().getMessages(selectedConversation.conversationId(), 100, 0);
                messageListModel.clear();
                // SQLite returns DESC order, so reverse to display chronologically in chat thread
                for (int i = messages.size() - 1; i >= 0; i--) {
                    messageListModel.addElement(messages.get(i));
                }
                notifyStateChanged();
            } catch (Exception e) {
                log.warn("Error loading messages: {}", e.getMessage());
            }
        });
    }

    public void refreshContacts() {
        if (client == null) return;
        SwingUtilities.invokeLater(() -> {
            try {
                List<StoredContact> contacts = client.storage().getAllContacts();
                contactsListModel.clear();
                for (StoredContact c : contacts) {
                    contactsListModel.addElement(c);
                }
                notifyStateChanged();
            } catch (Exception e) {
                log.warn("Error loading contacts: {}", e.getMessage());
            }
        });
    }

    public void appendFrameLog(String direction, String frameType, long correlationId, int size, String summary) {
        ProtocolFrameLogEntry entry = new ProtocolFrameLogEntry(
                System.currentTimeMillis(), direction, frameType, correlationId, size, summary
        );
        synchronized (frameLogs) {
            frameLogs.add(0, entry);
            if (frameLogs.size() > MAX_FRAME_LOG_ENTRIES) {
                frameLogs.remove(frameLogs.size() - 1);
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            frameLogTableModel.fireTableDataChanged();
        } else {
            SwingUtilities.invokeLater(frameLogTableModel::fireTableDataChanged);
        }
    }

    // --- VibeClientListener Callbacks ---

    @Override
    public void onConnectionStateChanged(ConnectionState state) {
        this.connectionState = state;
        appendFrameLog("SYS", "STATE_CHANGED", 0, 0, "Connection state -> " + state);
        SwingUtilities.invokeLater(this::notifyStateChanged);
    }

    @Override
    public void onAuthenticated(String userId, String token) {
        appendFrameLog("SYS", "AUTH_SUCCESS", 0, 0, "Authenticated userId=" + userId);
        refreshConversations();
        refreshContacts();
    }

    @Override
    public void onMessageReceived(ConversationEventPayload event) {
        appendFrameLog("RX <-", "CONVERSATION_EVENT", 0, event.payload().length,
                "conv=" + event.conversationId() + " seq=" + event.seqNumber());
        refreshConversations();
        if (selectedConversation != null && selectedConversation.conversationId().equals(event.conversationId())) {
            refreshMessagesForSelected();
        }
    }

    @Override
    public void onError(String errorCode, String errorMessage) {
        appendFrameLog("RX <-", "ERROR", 0, 0, errorCode + ": " + errorMessage);
    }

    private void notifyStateChanged() {
        for (StateListener l : stateListeners) {
            try {
                l.onStateChanged();
            } catch (Exception ignored) {}
        }
    }

    // --- Table Model for Inspector ---

    public final class FrameLogTableModel extends AbstractTableModel {
        private final String[] columns = {"Time", "Dir", "Frame Type", "Correlation ID", "Bytes", "Summary"};

        @Override
        public int getRowCount() {
            synchronized (frameLogs) {
                return frameLogs.size();
            }
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ProtocolFrameLogEntry entry;
            synchronized (frameLogs) {
                if (rowIndex >= frameLogs.size()) return null;
                entry = frameLogs.get(rowIndex);
            }
            return switch (columnIndex) {
                case 0 -> entry.formattedTime();
                case 1 -> entry.direction();
                case 2 -> entry.frameType();
                case 3 -> entry.correlationId();
                case 4 -> entry.payloadSize();
                case 5 -> entry.summary();
                default -> "";
            };
        }
    }
}
