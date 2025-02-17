package com.vibe.desktop.ui;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;
import com.vibe.protocol.payload.AuthResultPayload;
import com.vibe.sdk.client.VibeClient;
import com.vibe.sdk.config.VibeClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Authentication and connection setup panel for Vibe Desktop.
 */
public final class AuthPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(AuthPanel.class);

    private final DesktopState state;
    private final Runnable onAuthSuccess;

    private final JTextField hostField = VibeDarkTheme.createTextField();
    private final JTextField portField = VibeDarkTheme.createTextField();
    private final JCheckBox tlsCheck = new JCheckBox("Enable TLS (JSSE)");

    private final JTextField usernameField = VibeDarkTheme.createTextField();
    private final JPasswordField passwordField = VibeDarkTheme.createPasswordField();
    private final JTextField displayNameField = VibeDarkTheme.createTextField();
    private final JTextField bioField = VibeDarkTheme.createTextField();

    private final JLabel statusLabel = new JLabel(" ");
    private final JButton loginBtn = VibeDarkTheme.createPrimaryButton("Sign In");
    private final JButton registerBtn = VibeDarkTheme.createSecondaryButton("Create Account");

    public AuthPanel(DesktopState state, Runnable onAuthSuccess) {
        this.state = state;
        this.onAuthSuccess = onAuthSuccess;

        setLayout(new GridBagLayout());
        setBackground(VibeDarkTheme.BG_DARKEST);

        initUI();
    }

    private void initUI() {
        JPanel card = VibeDarkTheme.createCardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(420, 560));

        // Header
        JLabel logoLabel = new JLabel("⚡ VIBE");
        logoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        logoLabel.setForeground(VibeDarkTheme.ACCENT_PRIMARY);
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("High-Performance Distributed Messenger");
        subLabel.setFont(VibeDarkTheme.FONT_SMALL);
        subLabel.setForeground(VibeDarkTheme.TEXT_MUTED);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(Box.createVerticalStrut(10));
        card.add(logoLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(subLabel);
        card.add(Box.createVerticalStrut(20));

        // Network settings section
        JPanel netPanel = new JPanel(new GridLayout(2, 2, 8, 4));
        netPanel.setBackground(VibeDarkTheme.BG_SECONDARY);
        hostField.setText("127.0.0.1");
        portField.setText("8443");
        tlsCheck.setBackground(VibeDarkTheme.BG_SECONDARY);
        tlsCheck.setForeground(VibeDarkTheme.TEXT_SECONDARY);
        tlsCheck.setFont(VibeDarkTheme.FONT_SMALL);

        netPanel.add(new JLabel("Server Host:"));
        netPanel.add(new JLabel("Port:"));
        netPanel.add(hostField);
        netPanel.add(portField);

        card.add(netPanel);
        card.add(Box.createVerticalStrut(4));
        tlsCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(tlsCheck);
        card.add(Box.createVerticalStrut(16));

        // Credentials section
        card.add(createLabel("Username:"));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(8));

        card.add(createLabel("Password:"));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(8));

        card.add(createLabel("Display Name (for registration):"));
        card.add(displayNameField);
        card.add(Box.createVerticalStrut(8));

        card.add(createLabel("Bio (for registration):"));
        card.add(bioField);
        card.add(Box.createVerticalStrut(16));

        // Action buttons
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setBackground(VibeDarkTheme.BG_SECONDARY);
        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);
        card.add(btnPanel);
        card.add(Box.createVerticalStrut(12));

        statusLabel.setFont(VibeDarkTheme.FONT_SMALL);
        statusLabel.setForeground(VibeDarkTheme.STATUS_DANGER);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(statusLabel);

        add(card);

        // Actions
        loginBtn.addActionListener(e -> performLogin());
        registerBtn.addActionListener(e -> performRegister());
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(VibeDarkTheme.FONT_SMALL);
        l.setForeground(VibeDarkTheme.TEXT_SECONDARY);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Username and password are required", true);
            return;
        }

        setBusy(true, "Connecting to server...");
        initClient().thenCompose(client -> {
            setStatus("Authenticating...", false);
            return client.login(username, password);
        }).thenAccept(authRes -> {
            SwingUtilities.invokeLater(() -> {
                setBusy(false, "");
                if (authRes.isSuccess()) {
                    state.setCurrentUsername(username);
                    onAuthSuccess.run();
                } else {
                    setStatus("Login failed: " + authRes.errorMessage(), true);
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                setBusy(false, "");
                setStatus("Error: " + ex.getCause().getMessage(), true);
            });
            return null;
        });
    }

    private void performRegister() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        String displayName = displayNameField.getText().trim();
        String bio = bioField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            setStatus("Username and password are required", true);
            return;
        }

        if (displayName.isEmpty()) {
            displayName = username;
        }

        final String finalDisplayName = displayName;
        setBusy(true, "Registering new account...");
        initClient().thenCompose(client -> {
            return client.register(username, password, finalDisplayName, bio, "avatar.png");
        }).thenAccept(authRes -> {
            SwingUtilities.invokeLater(() -> {
                setBusy(false, "");
                if (authRes.isSuccess()) {
                    state.setCurrentUsername(username);
                    onAuthSuccess.run();
                } else {
                    setStatus("Registration rejected: " + authRes.errorMessage(), true);
                }
            });
        }).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> {
                setBusy(false, "");
                setStatus("Registration failed: " + ex.getCause().getMessage(), true);
            });
            return null;
        });
    }

    private CompletableFuture<VibeClient> initClient() {
        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            boolean tls = tlsCheck.isSelected();

            VibeClient existing = state.getClient();
            if (existing != null && existing.isConnected()) {
                return CompletableFuture.completedFuture(existing);
            }

            Path storageDir = Path.of(System.getProperty("user.home"), ".vibe-desktop");
            VibeClientConfig config = VibeClientConfig.builder()
                    .host(host)
                    .port(port)
                    .tlsEnabled(tls)
                    .storageDir(storageDir)
                    .build();

            VibeClient client = new VibeClient(config);
            state.setClient(client);

            return client.start().thenApply(v -> client);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void setBusy(boolean busy, String message) {
        loginBtn.setEnabled(!busy);
        registerBtn.setEnabled(!busy);
        hostField.setEnabled(!busy);
        portField.setEnabled(!busy);
        usernameField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
        setStatus(message, false);
    }

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message != null ? message : " ");
        statusLabel.setForeground(isError ? VibeDarkTheme.STATUS_DANGER : VibeDarkTheme.STATUS_INFO);
    }
}
