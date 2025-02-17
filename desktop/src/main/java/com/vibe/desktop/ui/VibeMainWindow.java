package com.vibe.desktop.ui;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Main application window for Vibe Desktop.
 */
public final class VibeMainWindow extends JFrame {

    private final DesktopState state;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);

    private final JLabel statusUserLabel = new JLabel("Offline");
    private final JLabel statusConnLabel = new JLabel("● Disconnected");

    public VibeMainWindow(DesktopState state) {
        super("Vibe Social Messenger & Protocol Inspector v2.0");
        this.state = state;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(850, 550));
        setLocationRelativeTo(null);

        initUI();
    }

    private void initUI() {
        // 1. Auth View
        AuthPanel authPanel = new AuthPanel(state, this::showMainAppView);

        // 2. Main App View with Tabs
        JPanel mainView = new JPanel(new BorderLayout());
        mainView.setBackground(VibeDarkTheme.BG_PRIMARY);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(VibeDarkTheme.BG_DARKEST);
        tabbedPane.setForeground(VibeDarkTheme.TEXT_PRIMARY);

        // Tab 1: Messenger (Split pane)
        ConversationListPanel leftList = new ConversationListPanel(state);
        ChatThreadPanel rightThread = new ChatThreadPanel(state);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftList, rightThread);
        splitPane.setDividerLocation(300);
        splitPane.setDividerSize(1);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        tabbedPane.addTab("💬 Messenger", splitPane);

        // Tab 2: Contacts & Network
        ContactsPanel contactsPanel = new ContactsPanel(state);
        tabbedPane.addTab("👥 Contacts", contactsPanel);

        // Tab 3: Operational Inspector
        OperationalInspectorPanel inspectorPanel = new OperationalInspectorPanel(state);
        tabbedPane.addTab("🛠️ Operational Inspector", inspectorPanel);

        mainView.add(tabbedPane, BorderLayout.CENTER);

        // Bottom Status Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(VibeDarkTheme.BG_DARKEST);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, VibeDarkTheme.BORDER_COLOR),
                new EmptyBorder(4, 12, 4, 12)
        ));

        statusUserLabel.setFont(VibeDarkTheme.FONT_SMALL);
        statusUserLabel.setForeground(VibeDarkTheme.TEXT_SECONDARY);

        statusConnLabel.setFont(VibeDarkTheme.FONT_SMALL);
        statusConnLabel.setForeground(VibeDarkTheme.STATUS_DANGER);

        statusBar.add(statusUserLabel, BorderLayout.WEST);
        statusBar.add(statusConnLabel, BorderLayout.EAST);
        mainView.add(statusBar, BorderLayout.SOUTH);

        // Card Container
        rootPanel.add(authPanel, "AUTH");
        rootPanel.add(mainView, "MAIN");
        add(rootPanel);

        cardLayout.show(rootPanel, "AUTH");

        state.addStateListener(this::updateStatusBar);
    }

    private void showMainAppView() {
        cardLayout.show(rootPanel, "MAIN");
        state.refreshConversations();
        state.refreshContacts();
        updateStatusBar();
    }

    private void updateStatusBar() {
        if (state.getClient() != null && state.getClient().isAuthenticated()) {
            statusUserLabel.setText("Signed in as @" + state.getCurrentUsername() + " (Device: " + state.getClient().config().deviceId() + ")");
            statusConnLabel.setText("● Connected (" + state.getConnectionState() + ")");
            statusConnLabel.setForeground(VibeDarkTheme.STATUS_SUCCESS);
        } else {
            statusUserLabel.setText("Not authenticated");
            statusConnLabel.setText("● " + state.getConnectionState());
            statusConnLabel.setForeground(VibeDarkTheme.STATUS_DANGER);
        }
    }
}
