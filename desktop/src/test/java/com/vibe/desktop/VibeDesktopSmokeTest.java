package com.vibe.desktop;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;
import com.vibe.desktop.ui.VibeMainWindow;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.assertj.core.api.Assertions.assertThat;

class VibeDesktopSmokeTest {

    @Test
    void testThemeAndUIInstantiationHeadlessSafe() {
        VibeDarkTheme.applyGlobalTheme();
        DesktopState state = new DesktopState();

        if (!GraphicsEnvironment.isHeadless()) {
            VibeMainWindow window = new VibeMainWindow(state);
            assertThat(window.getTitle()).contains("Vibe Social Messenger");
            window.dispose();
        } else {
            // In headless CI/test environments, verify model integrity without opening display window
            assertThat(state.getConnectionState()).isNotNull();
            assertThat(state.getConversationListModel()).isNotNull();
        }
    }
}
