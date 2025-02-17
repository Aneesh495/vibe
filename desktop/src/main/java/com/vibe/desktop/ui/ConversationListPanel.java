package com.vibe.desktop.ui;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;
import com.vibe.domain.entity.ConversationType;
import com.vibe.sdk.storage.StoredConversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Left sidebar listing active conversations with unread indicators and action buttons.
 */
public final class ConversationListPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ConversationListPanel.class);

    private final DesktopState state;
    private final JList<StoredConversation> convList;
    private final JTextField searchField = VibeDarkTheme.createTextField();

    public ConversationListPanel(DesktopState state) {
        this.state = state;
        setLayout(new BorderLayout());
        setBackground(VibeDarkTheme.BG_PRIMARY);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, VibeDarkTheme.BORDER_COLOR));

        // Top toolbar
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(VibeDarkTheme.BG_PRIMARY);
        topPanel.setBorder(new EmptyBorder(12, 12, 8, 12));

        JLabel titleLabel = new JLabel("Messages");
        titleLabel.setFont(VibeDarkTheme.FONT_TITLE);
        titleLabel.setForeground(VibeDarkTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel btnBar = new JPanel(new GridLayout(1, 2, 6, 0));
        btnBar.setBackground(VibeDarkTheme.BG_PRIMARY);
        JButton newDirectBtn = VibeDarkTheme.createSecondaryButton("+ Direct");
        JButton newGroupBtn = VibeDarkTheme.createSecondaryButton("+ Group");
        btnBar.add(newDirectBtn);
        btnBar.add(newGroupBtn);
        btnBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        topPanel.add(titleLabel);
        topPanel.add(Box.createVerticalStrut(8));
        topPanel.add(searchField);
        topPanel.add(Box.createVerticalStrut(8));
        topPanel.add(btnBar);

        add(topPanel, BorderLayout.NORTH);

        // List
        convList = new JList<>(state.getConversationListModel());
        convList.setBackground(VibeDarkTheme.BG_PRIMARY);
        convList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        convList.setCellRenderer(new ConversationCellRenderer());

        convList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                StoredConversation selected = convList.getSelectedValue();
                if (selected != null) {
                    state.selectConversation(selected);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(convList);
        VibeDarkTheme.customizeScrollBar(scrollPane);
        add(scrollPane, BorderLayout.CENTER);

        // Button actions
        newDirectBtn.addActionListener(e -> promptNewDirectChat());
        newGroupBtn.addActionListener(e -> promptNewGroupChat());
    }

    private void promptNewDirectChat() {
        String targetUser = JOptionPane.showInputDialog(
                this,
                "Enter username to direct message:",
                "Start Direct Conversation",
                JOptionPane.QUESTION_MESSAGE
        );
        if (targetUser != null && !targetUser.trim().isEmpty()) {
            state.getClient().createConversation(
                    ConversationType.DIRECT,
                    targetUser.trim(),
                    List.of(targetUser.trim())
            ).thenAccept(res -> {
                SwingUtilities.invokeLater(() -> {
                    state.refreshConversations();
                });
            }).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Failed to create chat: " + ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                });
                return null;
            });
        }
    }

    private void promptNewGroupChat() {
        JTextField nameField = VibeDarkTheme.createTextField();
        JTextField membersField = VibeDarkTheme.createTextField();

        JPanel panel = new JPanel(new GridLayout(4, 1, 4, 4));
        panel.setBackground(VibeDarkTheme.BG_SECONDARY);
        panel.add(new JLabel("Group Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Member User IDs (comma separated):"));
        panel.add(membersField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Create Group Chat", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String title = nameField.getText().trim();
            if (title.isEmpty()) title = "New Group";

            List<String> members = Arrays.stream(membersField.getText().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            state.getClient().createConversation(ConversationType.GROUP, title, members)
                    .thenAccept(res -> SwingUtilities.invokeLater(state::refreshConversations))
                    .exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error: " + ex.getCause().getMessage()));
                        return null;
                    });
        }
    }

    private static final class ConversationCellRenderer extends JPanel implements ListCellRenderer<StoredConversation> {
        private final JLabel iconLabel = new JLabel();
        private final JLabel titleLabel = new JLabel();
        private final JLabel seqLabel = new JLabel();
        private final JLabel unreadBadge = new JLabel();

        ConversationCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setOpaque(true);

            JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 2));
            centerPanel.setOpaque(false);
            titleLabel.setFont(VibeDarkTheme.FONT_BODY_BOLD);
            seqLabel.setFont(VibeDarkTheme.FONT_SMALL);
            seqLabel.setForeground(VibeDarkTheme.TEXT_MUTED);
            centerPanel.add(titleLabel);
            centerPanel.add(seqLabel);

            unreadBadge.setFont(VibeDarkTheme.FONT_SMALL);
            unreadBadge.setForeground(Color.WHITE);
            unreadBadge.setBackground(VibeDarkTheme.ACCENT_PRIMARY);
            unreadBadge.setOpaque(true);
            unreadBadge.setBorder(new EmptyBorder(2, 6, 2, 6));

            add(iconLabel, BorderLayout.WEST);
            add(centerPanel, BorderLayout.CENTER);
            add(unreadBadge, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends StoredConversation> list,
                StoredConversation value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            if (isSelected) {
                setBackground(VibeDarkTheme.BG_TERTIARY);
                titleLabel.setForeground(Color.WHITE);
            } else {
                setBackground(VibeDarkTheme.BG_PRIMARY);
                titleLabel.setForeground(VibeDarkTheme.TEXT_PRIMARY);
            }

            iconLabel.setText(value.type() == ConversationType.DIRECT ? "👤" : "👥");
            titleLabel.setText(value.title());
            seqLabel.setText("Seq: #" + value.lastSeq());

            if (value.unreadCount() > 0) {
                unreadBadge.setText(String.valueOf(value.unreadCount()));
                unreadBadge.setVisible(true);
            } else {
                unreadBadge.setVisible(false);
            }

            return this;
        }
    }
}
