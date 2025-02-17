package com.vibe.desktop.ui;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;
import com.vibe.domain.entity.AttachmentInfo;
import com.vibe.sdk.client.AttachmentProgressListener;
import com.vibe.sdk.storage.MessageDeliveryStatus;
import com.vibe.sdk.storage.StoredConversation;
import com.vibe.sdk.storage.StoredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Main chat thread pane displaying messages, delivery checkmarks, emoji reactions,
 * attachment streaming, and message input.
 */
public final class ChatThreadPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ChatThreadPanel.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    private final DesktopState state;

    private final JLabel headerTitle = new JLabel("Select a conversation");
    private final JLabel typingLabel = new JLabel(" ");
    private final JList<StoredMessage> messageList;
    private final JTextField inputField = VibeDarkTheme.createTextField();
    private final JButton sendBtn = VibeDarkTheme.createPrimaryButton("Send");
    private final JButton attachBtn = VibeDarkTheme.createSecondaryButton("📎");
    private final JProgressBar uploadProgress = new JProgressBar(0, 100);

    private volatile AttachmentInfo stagedAttachment = null;
    private final JLabel attachmentPreview = new JLabel(" ");

    public ChatThreadPanel(DesktopState state) {
        this.state = state;
        setLayout(new BorderLayout());
        setBackground(VibeDarkTheme.BG_DARKEST);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(VibeDarkTheme.BG_PRIMARY);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, VibeDarkTheme.BORDER_COLOR),
                new EmptyBorder(12, 16, 12, 16)
        ));

        headerTitle.setFont(VibeDarkTheme.FONT_HEADER);
        headerTitle.setForeground(VibeDarkTheme.TEXT_PRIMARY);

        typingLabel.setFont(VibeDarkTheme.FONT_SMALL);
        typingLabel.setForeground(VibeDarkTheme.ACCENT_PRIMARY);

        header.add(headerTitle, BorderLayout.WEST);
        header.add(typingLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Message List
        messageList = new JList<>(state.getMessageListModel());
        messageList.setBackground(VibeDarkTheme.BG_DARKEST);
        messageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        messageList.setCellRenderer(new MessageCellRenderer(state));

        JScrollPane scrollPane = new JScrollPane(messageList);
        VibeDarkTheme.customizeScrollBar(scrollPane);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Input Area
        JPanel bottomContainer = new JPanel();
        bottomContainer.setLayout(new BoxLayout(bottomContainer, BoxLayout.Y_AXIS));
        bottomContainer.setBackground(VibeDarkTheme.BG_PRIMARY);
        bottomContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, VibeDarkTheme.BORDER_COLOR),
                new EmptyBorder(8, 12, 8, 12)
        ));

        uploadProgress.setVisible(false);
        uploadProgress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        uploadProgress.setForeground(VibeDarkTheme.ACCENT_PRIMARY);
        uploadProgress.setBackground(VibeDarkTheme.BG_SECONDARY);

        attachmentPreview.setFont(VibeDarkTheme.FONT_SMALL);
        attachmentPreview.setForeground(VibeDarkTheme.STATUS_INFO);
        attachmentPreview.setVisible(false);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBackground(VibeDarkTheme.BG_PRIMARY);
        inputRow.add(attachBtn, BorderLayout.WEST);
        inputRow.add(inputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        bottomContainer.add(uploadProgress);
        bottomContainer.add(attachmentPreview);
        bottomContainer.add(inputRow);
        add(bottomContainer, BorderLayout.SOUTH);

        // Event Bindings
        sendBtn.addActionListener(e -> sendMessage());
        attachBtn.addActionListener(e -> chooseAttachment());

        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                } else {
                    notifyTyping();
                }
            }
        });

        state.addStateListener(this::onStateChanged);
    }

    private void onStateChanged() {
        StoredConversation conv = state.getSelectedConversation();
        if (conv != null) {
            headerTitle.setText(conv.title() + " (" + conv.type() + ")");
            inputField.setEnabled(true);
            sendBtn.setEnabled(true);
            attachBtn.setEnabled(true);
        } else {
            headerTitle.setText("Select a conversation");
            inputField.setEnabled(false);
            sendBtn.setEnabled(false);
            attachBtn.setEnabled(false);
        }
    }

    private void notifyTyping() {
        StoredConversation conv = state.getSelectedConversation();
        if (conv != null && state.getClient() != null) {
            state.getClient().sendTyping(conv.conversationId(), true);
        }
    }

    private void chooseAttachment() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            uploadProgress.setValue(0);
            uploadProgress.setVisible(true);
            attachmentPreview.setText("Uploading " + selected.getName() + "...");
            attachmentPreview.setVisible(true);

            state.getClient().uploadAttachment(selected.toPath(), "application/octet-stream", new AttachmentProgressListener() {
                @Override
                public void onProgress(UUID attachmentId, long bytesTransferred, long totalBytes, double percent) {
                    SwingUtilities.invokeLater(() -> uploadProgress.setValue((int) percent));
                }

                @Override
                public void onComplete(UUID attachmentId) {
                    SwingUtilities.invokeLater(() -> {
                        uploadProgress.setVisible(false);
                        attachmentPreview.setText("Attached: " + selected.getName() + " (" + selected.length() + " bytes)");
                        stagedAttachment = new AttachmentInfo(
                                attachmentId, selected.getName(), "application/octet-stream", selected.length(), 0, true
                        );
                    });
                }

                @Override
                public void onError(UUID attachmentId, Throwable cause) {
                    SwingUtilities.invokeLater(() -> {
                        uploadProgress.setVisible(false);
                        attachmentPreview.setText("Attachment failed: " + cause.getMessage());
                        attachmentPreview.setForeground(VibeDarkTheme.STATUS_DANGER);
                    });
                }
            });
        }
    }

    private void sendMessage() {
        StoredConversation conv = state.getSelectedConversation();
        String text = inputField.getText().trim();
        if ((text.isEmpty() && stagedAttachment == null) || conv == null) {
            return;
        }

        inputField.setText("");
        AttachmentInfo att = stagedAttachment;
        stagedAttachment = null;
        attachmentPreview.setVisible(false);

        state.getClient().sendMessage(conv.conversationId(), text, att).thenAccept(res -> {
            SwingUtilities.invokeLater(() -> {
                state.refreshMessagesForSelected();
                state.refreshConversations();
            });
        }).exceptionally(ex -> {
            log.error("Failed to send message", ex);
            return null;
        });
    }

    // Message Cell Renderer
    private static final class MessageCellRenderer extends JPanel implements ListCellRenderer<StoredMessage> {
        private final DesktopState state;
        private final JPanel bubble = new JPanel();
        private final JLabel senderLabel = new JLabel();
        private final JLabel textLabel = new JLabel();
        private final JLabel timeAndStatus = new JLabel();
        private final JLabel attachmentLabel = new JLabel();

        MessageCellRenderer(DesktopState state) {
            this.state = state;
            setLayout(new BorderLayout());
            setOpaque(true);
            setBackground(VibeDarkTheme.BG_DARKEST);
            setBorder(new EmptyBorder(4, 16, 4, 16));

            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            bubble.setBorder(new EmptyBorder(8, 12, 8, 12));

            senderLabel.setFont(VibeDarkTheme.FONT_SMALL);
            senderLabel.setForeground(VibeDarkTheme.TEXT_MUTED);

            textLabel.setFont(VibeDarkTheme.FONT_BODY);

            timeAndStatus.setFont(VibeDarkTheme.FONT_SMALL);
            timeAndStatus.setForeground(VibeDarkTheme.TEXT_MUTED);

            attachmentLabel.setFont(VibeDarkTheme.FONT_SMALL);
            attachmentLabel.setForeground(VibeDarkTheme.STATUS_INFO);
            attachmentLabel.setVisible(false);

            bubble.add(senderLabel);
            bubble.add(Box.createVerticalStrut(2));
            bubble.add(textLabel);
            bubble.add(Box.createVerticalStrut(4));
            bubble.add(attachmentLabel);
            bubble.add(Box.createVerticalStrut(2));
            bubble.add(timeAndStatus);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends StoredMessage> list,
                StoredMessage msg,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            removeAll();
            boolean isSelf = state.getClient() != null && state.getClient().authenticatedUserId() != null
                    && state.getClient().authenticatedUserId().equals(msg.senderId());

            if (isSelf) {
                bubble.setBackground(VibeDarkTheme.BG_TERTIARY);
                textLabel.setForeground(Color.WHITE);
                senderLabel.setText("You [#" + msg.seqNumber() + "]");
                add(bubble, BorderLayout.EAST);
            } else {
                bubble.setBackground(VibeDarkTheme.BG_SECONDARY);
                textLabel.setForeground(VibeDarkTheme.TEXT_PRIMARY);
                senderLabel.setText(msg.senderId() + " [#" + msg.seqNumber() + "]");
                add(bubble, BorderLayout.WEST);
            }

            textLabel.setText("<html><p style=\"width: 320px;\">" + escapeHtml(msg.content()) + "</p></html>");

            String timeStr = TIME_FMT.format(Instant.ofEpochMilli(msg.timestamp()));
            String statusIcon = switch (msg.status()) {
                case PENDING_OUTBOX -> " ⏳";
                case COMMITTED -> " ✓";
                case DELIVERED -> " ✓✓";
                case READ -> " ✓✓";
                case FAILED -> " ⚠️ Failed";
            };
            timeAndStatus.setText(timeStr + (isSelf ? statusIcon : ""));

            if (msg.attachmentId() != null) {
                attachmentLabel.setText("📎 " + msg.attachmentName() + " (" + (msg.attachmentSize() / 1024) + " KB)");
                attachmentLabel.setVisible(true);
            } else {
                attachmentLabel.setVisible(false);
            }

            return this;
        }

        private String escapeHtml(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
