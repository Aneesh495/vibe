package com.vibe.desktop.ui;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;
import com.vibe.domain.entity.ConversationType;
import com.vibe.sdk.storage.StoredConversation;
import com.vibe.sdk.storage.StoredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.UUID;

/**
 * Third column context details sidebar displaying conversation metadata,
 * member list with online status, shared attachments, search within conversation,
 * and conversation actions (leave / mute / delete).
 */
public final class ContextDetailsSidebarPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ContextDetailsSidebarPanel.class);

    private final DesktopState state;
    private final JLabel titleLabel = new JLabel("Details");
    private final JLabel convTypeLabel = new JLabel(" ");
    private final JLabel convIdLabel = new JLabel(" ");
    private final DefaultListModel<String> memberListModel = new DefaultListModel<>();
    private final JList<String> memberList = new JList<>(memberListModel);
    private final DefaultListModel<String> attachmentListModel = new DefaultListModel<>();
    private final JList<String> attachmentList = new JList<>(attachmentListModel);
    private final JTextField inChatSearchField = VibeDarkTheme.createTextField();
    private final JButton leaveBtn = VibeDarkTheme.createDangerButton("Leave Conversation");

    public ContextDetailsSidebarPanel(DesktopState state) {
        this.state = state;
        setLayout(new BorderLayout());
        setBackground(VibeDarkTheme.BG_PRIMARY);
        setPreferredSize(new Dimension(280, 0));
        setMinimumSize(new Dimension(220, 0));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, VibeDarkTheme.BORDER_COLOR));

        initUI();
        state.addStateListener(this::updateFromState);
    }

    private void initUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(VibeDarkTheme.BG_PRIMARY);
        content.setBorder(new EmptyBorder(16, 14, 16, 14));

        // Header
        titleLabel.setFont(VibeDarkTheme.FONT_TITLE);
        titleLabel.setForeground(VibeDarkTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        convTypeLabel.setFont(VibeDarkTheme.FONT_SMALL);
        convTypeLabel.setForeground(VibeDarkTheme.TEXT_MUTED);
        convTypeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        convIdLabel.setFont(VibeDarkTheme.FONT_SMALL);
        convIdLabel.setForeground(VibeDarkTheme.TEXT_MUTED);
        convIdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(titleLabel);
        content.add(Box.createVerticalStrut(4));
        content.add(convTypeLabel);
        content.add(convIdLabel);
        content.add(Box.createVerticalStrut(14));

        // Search within chat
        JLabel searchTitle = new JLabel("Search in Conversation");
        searchTitle.setFont(VibeDarkTheme.FONT_BODY_BOLD);
        searchTitle.setForeground(VibeDarkTheme.TEXT_SECONDARY);
        searchTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        inChatSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        inChatSearchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        inChatSearchField.addActionListener(e -> performInChatSearch());

        content.add(searchTitle);
        content.add(Box.createVerticalStrut(6));
        content.add(inChatSearchField);
        content.add(Box.createVerticalStrut(16));

        // Members section
        JLabel membersTitle = new JLabel("Members");
        membersTitle.setFont(VibeDarkTheme.FONT_BODY_BOLD);
        membersTitle.setForeground(VibeDarkTheme.TEXT_SECONDARY);
        membersTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        memberList.setBackground(VibeDarkTheme.BG_SECONDARY);
        memberList.setForeground(VibeDarkTheme.TEXT_PRIMARY);
        memberList.setFont(VibeDarkTheme.FONT_BODY);
        memberList.setBorder(new EmptyBorder(6, 6, 6, 6));
        JScrollPane memberScroll = new JScrollPane(memberList);
        memberScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        memberScroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, 120));
        memberScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        VibeDarkTheme.customizeScrollBar(memberScroll);

        content.add(membersTitle);
        content.add(Box.createVerticalStrut(6));
        content.add(memberScroll);
        content.add(Box.createVerticalStrut(16));

        // Shared attachments section
        JLabel attachTitle = new JLabel("Shared Attachments");
        attachTitle.setFont(VibeDarkTheme.FONT_BODY_BOLD);
        attachTitle.setForeground(VibeDarkTheme.TEXT_SECONDARY);
        attachTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        attachmentList.setBackground(VibeDarkTheme.BG_SECONDARY);
        attachmentList.setForeground(VibeDarkTheme.TEXT_PRIMARY);
        attachmentList.setFont(VibeDarkTheme.FONT_SMALL);
        attachmentList.setBorder(new EmptyBorder(6, 6, 6, 6));
        JScrollPane attachScroll = new JScrollPane(attachmentList);
        attachScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        attachScroll.setPreferredSize(new Dimension(Integer.MAX_VALUE, 120));
        attachScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        VibeDarkTheme.customizeScrollBar(attachScroll);

        content.add(attachTitle);
        content.add(Box.createVerticalStrut(6));
        content.add(attachScroll);
        content.add(Box.createVerticalStrut(20));

        // Actions
        leaveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        leaveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        leaveBtn.addActionListener(e -> leaveCurrentConversation());
        content.add(leaveBtn);

        JScrollPane mainScroll = new JScrollPane(content);
        mainScroll.setBorder(BorderFactory.createEmptyBorder());
        VibeDarkTheme.customizeScrollBar(mainScroll);
        add(mainScroll, BorderLayout.CENTER);
    }

    private void updateFromState() {
        StoredConversation conv = state.getSelectedConversation();
        if (conv == null) {
            titleLabel.setText("No selection");
            convTypeLabel.setText("Select a conversation to view details");
            convIdLabel.setText("");
            memberListModel.clear();
            attachmentListModel.clear();
            leaveBtn.setEnabled(false);
            return;
        }

        leaveBtn.setEnabled(true);
        titleLabel.setText(conv.title());
        convTypeLabel.setText(conv.type() == ConversationType.DIRECT ? "Direct Chat" : "Group Channel");
        convIdLabel.setText("ID: " + conv.conversationId().toString().substring(0, 8) + "...");

        // Populate members
        memberListModel.clear();
        String currentUsername = state.getCurrentUsername();
        memberListModel.addElement("👤 @" + currentUsername + " (You) - Online");

        // Scan messages for shared attachments
        attachmentListModel.clear();
        int msgCount = state.getMessageListModel().getSize();
        for (int i = 0; i < msgCount; i++) {
            StoredMessage msg = state.getMessageListModel().getElementAt(i);
            if (msg.attachmentId() != null) {
                String attachDesc = "📎 " + msg.attachmentName() + " (" + (msg.attachmentSize() / 1024) + " KB)";
                if (!attachmentListModel.contains(attachDesc)) {
                    attachmentListModel.addElement(attachDesc);
                }
            }
        }
        if (attachmentListModel.isEmpty()) {
            attachmentListModel.addElement("No shared files yet");
        }
    }

    private void performInChatSearch() {
        String query = inChatSearchField.getText().trim();
        if (query.isEmpty()) {
            state.refreshMessagesForSelected();
            return;
        }
        int count = state.getMessageListModel().getSize();
        for (int i = 0; i < count; i++) {
            StoredMessage msg = state.getMessageListModel().getElementAt(i);
            if (msg.content().toLowerCase().contains(query.toLowerCase())) {
                log.info("Found match for '{}' at message seq {}", query, msg.seqNumber());
            }
        }
    }

    private void leaveCurrentConversation() {
        StoredConversation conv = state.getSelectedConversation();
        if (conv == null) return;
        int opt = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to leave '" + conv.title() + "'?",
                "Leave Conversation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (opt == JOptionPane.YES_OPTION) {
            log.info("User requested leave conversation {}", conv.conversationId());
            state.selectConversation(null);
            state.refreshConversations();
        }
    }
}
