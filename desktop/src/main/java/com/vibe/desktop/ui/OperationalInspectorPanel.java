package com.vibe.desktop.ui;

import com.vibe.desktop.model.DesktopState;
import com.vibe.desktop.theme.VibeDarkTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Live Operational Inspector visualizer providing real-time visibility into
 * socket reactors, binary frames, delivery lanes, queue depths, and consensus state.
 */
public final class OperationalInspectorPanel extends JPanel {

    private final DesktopState state;

    // Stat metric labels
    private final JLabel connectionStateVal = new JLabel("CLOSED");
    private final JLabel serverTargetVal = new JLabel("127.0.0.1:8443");
    private final JLabel tlsStatusVal = new JLabel("TLS 1.3 / TCP_NODELAY");
    private final JLabel framesDecodedVal = new JLabel("0");
    private final JLabel pendingOutboxVal = new JLabel("0 items");
    private final JLabel consensusModeVal = new JLabel("Standalone WAL / Ratis Quorum");

    private final JTable logTable;
    private final Timer refreshTimer;

    public OperationalInspectorPanel(DesktopState state) {
        this.state = state;
        setLayout(new BorderLayout(0, 12));
        setBackground(VibeDarkTheme.BG_DARKEST);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("🛠️ Operational Network & Protocol Inspector");
        title.setFont(VibeDarkTheme.FONT_TITLE);
        title.setForeground(VibeDarkTheme.TEXT_PRIMARY);

        JLabel sub = new JLabel("Live telemetry across Java NIO Transport, Bounded Frames, and Consensus State");
        sub.setFont(VibeDarkTheme.FONT_SMALL);
        sub.setForeground(VibeDarkTheme.TEXT_MUTED);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setOpaque(false);
        titleBlock.add(title);
        titleBlock.add(sub);

        headerPanel.add(titleBlock, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Center: Metric Cards + Log Table
        JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
        centerPanel.setOpaque(false);

        // 4 Stat Cards in a Grid
        JPanel cardsGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        cardsGrid.setOpaque(false);
        cardsGrid.setPreferredSize(new Dimension(Integer.MAX_VALUE, 110));

        cardsGrid.add(createMetricCard("Transport State", connectionStateVal, "Multiplexed Non-blocking NIO"));
        cardsGrid.add(createMetricCard("Target & Protocol", serverTargetVal, "24-Byte Length Prefixed"));
        cardsGrid.add(createMetricCard("Offline Outbox", pendingOutboxVal, "SQLite WAL + Resumable"));
        cardsGrid.add(createMetricCard("Durability & Quorum", consensusModeVal, "Monotonic Conv Lanes"));

        centerPanel.add(cardsGrid, BorderLayout.NORTH);

        // Log Table section
        JPanel tableContainer = new JPanel(new BorderLayout(0, 6));
        tableContainer.setOpaque(false);

        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        JLabel tableTitle = new JLabel("Live Wire Frame Trace (CRC32C Verified)");
        tableTitle.setFont(VibeDarkTheme.FONT_HEADER);
        tableTitle.setForeground(VibeDarkTheme.TEXT_PRIMARY);
        tableHeader.add(tableTitle, BorderLayout.WEST);

        logTable = new JTable(state.getFrameLogTableModel());
        logTable.setBackground(VibeDarkTheme.BG_PRIMARY);
        logTable.setForeground(VibeDarkTheme.TEXT_PRIMARY);
        logTable.setRowHeight(24);
        logTable.setFont(VibeDarkTheme.FONT_MONO);
        logTable.setShowGrid(true);
        logTable.setGridColor(VibeDarkTheme.BORDER_COLOR);

        // Column widths and styling
        logTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(60);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(70);
        logTable.getColumnModel().getColumn(5).setPreferredWidth(450);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        logTable.getColumnModel().getColumn(1).setCellRenderer(new DirectionCellRenderer());
        logTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        logTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

        JScrollPane tableScroll = new JScrollPane(logTable);
        VibeDarkTheme.customizeScrollBar(tableScroll);

        tableContainer.add(tableHeader, BorderLayout.NORTH);
        tableContainer.add(tableScroll, BorderLayout.CENTER);

        centerPanel.add(tableContainer, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // 1-second UI refresh timer
        refreshTimer = new Timer(1000, e -> updateMetrics());
        refreshTimer.start();
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, String subtitle) {
        JPanel card = VibeDarkTheme.createCardPanel();
        card.setLayout(new GridLayout(3, 1, 0, 4));

        JLabel t = new JLabel(title);
        t.setFont(VibeDarkTheme.FONT_SMALL);
        t.setForeground(VibeDarkTheme.TEXT_MUTED);

        valueLabel.setFont(VibeDarkTheme.FONT_HEADER);
        valueLabel.setForeground(VibeDarkTheme.ACCENT_PRIMARY);

        JLabel sub = new JLabel(subtitle);
        sub.setFont(VibeDarkTheme.FONT_SMALL);
        sub.setForeground(VibeDarkTheme.TEXT_SECONDARY);

        card.add(t);
        card.add(valueLabel);
        card.add(sub);
        return card;
    }

    private void updateMetrics() {
        if (state.getClient() != null) {
            connectionStateVal.setText(state.getConnectionState().name());
            connectionStateVal.setForeground(state.getConnectionState().isActive()
                    ? VibeDarkTheme.STATUS_SUCCESS
                    : VibeDarkTheme.STATUS_DANGER);

            serverTargetVal.setText(state.getClient().config().host() + ":" + state.getClient().config().port());

            try {
                int outboxSize = state.getClient().storage().getPendingOutbox().size();
                pendingOutboxVal.setText(outboxSize + " pending");
                pendingOutboxVal.setForeground(outboxSize > 0 ? VibeDarkTheme.STATUS_WARNING : VibeDarkTheme.STATUS_SUCCESS);
            } catch (Exception ignored) {}
        }
    }

    private static final class DirectionCellRenderer extends DefaultTableCellRenderer {
        DirectionCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column
        ) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String str = String.valueOf(value);
            if (str.contains("TX")) {
                setForeground(VibeDarkTheme.STATUS_INFO);
            } else if (str.contains("RX")) {
                setForeground(VibeDarkTheme.STATUS_SUCCESS);
            } else {
                setForeground(VibeDarkTheme.TEXT_MUTED);
            }
            return this;
        }
    }
}
