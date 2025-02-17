package com.vibe.desktop.ui;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;
import com.vibe.domain.entity.ConversationType;
import com.vibe.sdk.storage.StoredContact;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Contacts and social graph management panel (friends, blocked users).
 */
public final class ContactsPanel extends JPanel {

    private final DesktopState state;
    private final JList<StoredContact> contactList;

    public ContactsPanel(DesktopState state) {
        this.state = state;
        setLayout(new BorderLayout());
        setBackground(VibeDarkTheme.BG_PRIMARY);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // Header
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(VibeDarkTheme.BG_PRIMARY);
        topPanel.setBorder(new EmptyBorder(0, 0, 12, 0));

        JLabel title = new JLabel("Contacts & Network");
        title.setFont(VibeDarkTheme.FONT_TITLE);
        title.setForeground(VibeDarkTheme.TEXT_PRIMARY);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnBar.setBackground(VibeDarkTheme.BG_PRIMARY);
        JButton addFriendBtn = VibeDarkTheme.createPrimaryButton("+ Add Friend");
        JButton blockUserBtn = VibeDarkTheme.createSecondaryButton("🚫 Block User");
        btnBar.add(addFriendBtn);
        btnBar.add(blockUserBtn);

        topPanel.add(title, BorderLayout.WEST);
        topPanel.add(btnBar, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // List
        contactList = new JList<>(state.getContactsListModel());
        contactList.setBackground(VibeDarkTheme.BG_PRIMARY);
        contactList.setCellRenderer(new ContactCellRenderer());

        JScrollPane scrollPane = new JScrollPane(contactList);
        VibeDarkTheme.customizeScrollBar(scrollPane);
        add(scrollPane, BorderLayout.CENTER);

        // Actions
        addFriendBtn.addActionListener(e -> {
            String targetId = JOptionPane.showInputDialog(this, "Enter user ID to add as friend:");
            if (targetId != null && !targetId.trim().isEmpty()) {
                state.getClient().addFriend(targetId.trim())
                        .thenAccept(res -> SwingUtilities.invokeLater(state::refreshContacts));
            }
        });

        blockUserBtn.addActionListener(e -> {
            String targetId = JOptionPane.showInputDialog(this, "Enter user ID to block:");
            if (targetId != null && !targetId.trim().isEmpty()) {
                state.getClient().blockUser(targetId.trim())
                        .thenAccept(res -> SwingUtilities.invokeLater(state::refreshContacts));
            }
        });
    }

    private static final class ContactCellRenderer extends JPanel implements ListCellRenderer<StoredContact> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel statusLabel = new JLabel();

        ContactCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setOpaque(true);
            setBackground(VibeDarkTheme.BG_PRIMARY);

            nameLabel.setFont(VibeDarkTheme.FONT_BODY_BOLD);
            statusLabel.setFont(VibeDarkTheme.FONT_SMALL);

            add(nameLabel, BorderLayout.WEST);
            add(statusLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends StoredContact> list,
                StoredContact contact,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            nameLabel.setText(contact.displayName() + " (@" + contact.username() + ")");
            if (contact.isBlocked()) {
                statusLabel.setText("🚫 Blocked");
                statusLabel.setForeground(VibeDarkTheme.STATUS_DANGER);
            } else if (contact.isFriend()) {
                statusLabel.setText("⭐ Friend");
                statusLabel.setForeground(VibeDarkTheme.STATUS_SUCCESS);
            } else {
                statusLabel.setText("User");
                statusLabel.setForeground(VibeDarkTheme.TEXT_MUTED);
            }

            if (isSelected) {
                setBackground(VibeDarkTheme.BG_TERTIARY);
            } else {
                setBackground(VibeDarkTheme.BG_PRIMARY);
            }

            return this;
        }
    }
}
