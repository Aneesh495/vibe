package com.vibe.desktop;

import com.vibe.desktop.model.DesktopState;
import com.vibe.domain.entity.ConversationType;
import com.vibe.sdk.storage.StoredConversation;
import com.vibe.transport.nio.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DesktopStateTest {

    @Test
    void testStateUpdatesAndFrameLogs() {
        DesktopState state = new DesktopState();

        AtomicBoolean listenerFired = new AtomicBoolean(false);
        state.addStateListener(() -> listenerFired.set(true));

        state.setCurrentUsername("alice");
        assertThat(state.getCurrentUsername()).isEqualTo("alice");

        state.onConnectionStateChanged(ConnectionState.ESTABLISHED);
        assertThat(state.getConnectionState()).isEqualTo(ConnectionState.ESTABLISHED);

        // Frame logs
        state.appendFrameLog("TX ->", "COMMAND", 101L, 64, "SendMessage");
        state.appendFrameLog("RX <-", "COMMAND_RESULT", 101L, 32, "Success");

        assertThat(state.getFrameLogTableModel().getRowCount()).isGreaterThanOrEqualTo(2);
        assertThat(state.getFrameLogTableModel().getValueAt(0, 1)).isEqualTo("RX <-");
        assertThat(state.getFrameLogTableModel().getValueAt(0, 2)).isEqualTo("COMMAND_RESULT");

        // Selected conversation
        UUID convId = UUID.randomUUID();
        StoredConversation conv = new StoredConversation(
                convId, ConversationType.GROUP, "Dev Team",
                System.currentTimeMillis(), 5L, 0
        );
        state.selectConversation(conv);
        assertThat(state.getSelectedConversation()).isEqualTo(conv);
        assertThat(listenerFired.get()).isTrue();
    }
}
