package com.vibe.desktop.theme;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

/**
 * Modern high-contrast dark theme styling and component factories for the Vibe Desktop UI.
 */
public final class VibeDarkTheme {

    // Color Palette
    public static final Color BG_DARKEST = new Color(0x0f, 0x11, 0x15);
    public static final Color BG_PRIMARY = new Color(0x18, 0x1a, 0x20);
    public static final Color BG_SECONDARY = new Color(0x22, 0x26, 0x30);
    public static final Color BG_TERTIARY = new Color(0x2c, 0x32, 0x40);
    public static final Color BORDER_COLOR = new Color(0x36, 0x3d, 0x4d);

    public static final Color ACCENT_PRIMARY = new Color(0x63, 0x66, 0xf1); // Indigo
    public static final Color ACCENT_HOVER = new Color(0x4f, 0x46, 0xe5);
    public static final Color ACCENT_ACTIVE = new Color(0x43, 0x38, 0xca);

    public static final Color TEXT_PRIMARY = new Color(0xf8, 0xfa, 0xfc);
    public static final Color TEXT_SECONDARY = new Color(0x94, 0xa3, 0xb8);
    public static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8b);

    public static final Color STATUS_SUCCESS = new Color(0x10, 0xb9, 0x81); // Emerald
    public static final Color STATUS_WARNING = new Color(0xf5, 0x9e, 0x0b); // Amber
    public static final Color STATUS_DANGER = new Color(0xef, 0x44, 0x44);  // Rose
    public static final Color STATUS_INFO = new Color(0x38, 0xbd, 0xf8);    // Sky

    // Fonts
    public static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 18);
    public static final Font FONT_HEADER = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font FONT_BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    public static final Font FONT_MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private VibeDarkTheme() {}

    public static void applyGlobalTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        UIManager.put("Panel.background", BG_PRIMARY);
        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("Label.font", FONT_BODY);
        UIManager.put("TextField.background", BG_SECONDARY);
        UIManager.put("TextField.foreground", TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground", TEXT_PRIMARY);
        UIManager.put("PasswordField.background", BG_SECONDARY);
        UIManager.put("PasswordField.foreground", TEXT_PRIMARY);
        UIManager.put("PasswordField.caretForeground", TEXT_PRIMARY);
        UIManager.put("TextArea.background", BG_SECONDARY);
        UIManager.put("TextArea.foreground", TEXT_PRIMARY);
        UIManager.put("List.background", BG_PRIMARY);
        UIManager.put("List.foreground", TEXT_PRIMARY);
        UIManager.put("List.selectionBackground", BG_SECONDARY);
        UIManager.put("List.selectionForeground", TEXT_PRIMARY);
        UIManager.put("Table.background", BG_SECONDARY);
        UIManager.put("Table.foreground", TEXT_PRIMARY);
        UIManager.put("Table.gridColor", BORDER_COLOR);
        UIManager.put("TableHeader.background", BG_TERTIARY);
        UIManager.put("TableHeader.foreground", TEXT_PRIMARY);
        UIManager.put("ScrollPane.background", BG_PRIMARY);
        UIManager.put("TabbedPane.background", BG_DARKEST);
        UIManager.put("TabbedPane.foreground", TEXT_PRIMARY);
        UIManager.put("TabbedPane.selected", BG_PRIMARY);
    }

    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(ACCENT_PRIMARY);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 16, 8, 16));
        return btn;
    }

    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY);
        btn.setForeground(TEXT_PRIMARY);
        btn.setBackground(BG_TERTIARY);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 12, 6, 12));
        return btn;
    }

    public static JTextField createTextField() {
        JTextField tf = new JTextField();
        tf.setFont(FONT_BODY);
        tf.setBackground(BG_SECONDARY);
        tf.setForeground(TEXT_PRIMARY);
        tf.setCaretColor(TEXT_PRIMARY);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return tf;
    }

    public static JPasswordField createPasswordField() {
        JPasswordField pf = new JPasswordField();
        pf.setFont(FONT_BODY);
        pf.setBackground(BG_SECONDARY);
        pf.setForeground(TEXT_PRIMARY);
        pf.setCaretColor(TEXT_PRIMARY);
        pf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return pf;
    }

    public static JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setBackground(BG_SECONDARY);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return p;
    }

    public static Border createLineBorder() {
        return BorderFactory.createLineBorder(BORDER_COLOR, 1);
    }

    public static void customizeScrollBar(JScrollPane scrollPane) {
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = BG_TERTIARY;
                this.trackColor = BG_PRIMARY;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
    }
}
