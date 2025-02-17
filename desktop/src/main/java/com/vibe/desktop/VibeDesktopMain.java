package com.vibe.desktop;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;
import com.vibe.desktop.ui.VibeMainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.GraphicsEnvironment;

/**
 * Entry point for launching the Vibe Desktop Messenger & Operational Inspector.
 */
public final class VibeDesktopMain {

    private static final Logger log = LoggerFactory.getLogger(VibeDesktopMain.class);

    public static void main(String[] args) {
        log.info("Launching Vibe Desktop Messenger v2.0...");

        // Ensure headless safety
        if (GraphicsEnvironment.isHeadless()) {
            log.warn("GraphicsEnvironment is headless; exiting graphical launcher.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            VibeDarkTheme.applyGlobalTheme();
            DesktopState state = new DesktopState();
            VibeMainWindow window = new VibeMainWindow(state);
            window.setVisible(true);
        });
    }
}
