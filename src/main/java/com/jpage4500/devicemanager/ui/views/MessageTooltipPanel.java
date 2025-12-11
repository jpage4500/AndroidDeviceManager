package com.jpage4500.devicemanager.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Custom tooltip panel for displaying multi-line truncated text
 * with yellow background at the bottom of a table
 */
public class MessageTooltipPanel extends JWindow {
    private static final Logger log = LoggerFactory.getLogger(MessageTooltipPanel.class);

    private static final Color TOOLTIP_BACKGROUND = new Color(255, 255, 200); // Light yellow
    private static final Color TOOLTIP_BORDER = new Color(180, 180, 150); // Darker border
    private static final int PADDING = 8;
    public static final int MAX_HEIGHT = 100; // Maximum tooltip height

    private final TooltipTextView textView;

    public MessageTooltipPanel(Window owner) {
        super(owner);
        setAlwaysOnTop(true);
        setFocusableWindowState(false);

        textView = new TooltipTextView();
        textView.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textView.setBackground(TOOLTIP_BACKGROUND);
        textView.setForeground(Color.BLACK);
        textView.setBorder(new CompoundBorder(
            new LineBorder(TOOLTIP_BORDER, 1),
            new EmptyBorder(PADDING, PADDING, PADDING, PADDING)
        ));
        getContentPane().add(textView);
    }

    /**
     * Show tooltip with given text at specified position and width
     *
     * @param text  the message to display
     * @param x     x position (screen coordinates)
     * @param width desired width of tooltip
     */
    public void showTooltip(String text, int x, int topY, int bottomY, int mouseY, int width) {
        if (text == null || text.isEmpty()) {
            hideTooltip();
            return;
        }
        textView.setText(text);
        int preferredHeight = textView.getPreferredHeight(width);
        int actualHeight = Math.min(preferredHeight, MAX_HEIGHT);
        // NOTE: always show at top unless the dialog would cover up the mouse cursor
        // boolean showAtTop = mouseY >= (bottomY - actualHeight);
        boolean showAtTop = mouseY >= topY + actualHeight;
        int y = showAtTop ? topY : bottomY - actualHeight;
        setSize(width, actualHeight);
        setLocation(x, y);
        setVisible(true);
    }

    /**
     * Hide the tooltip
     */
    public void hideTooltip() {
        setVisible(false);
    }

    /**
     * Check if tooltip is currently visible
     */
    public boolean isTooltipVisible() {
        return isVisible();
    }

    /**
     * Custom text view for tooltip with adjustable line spacing
     */
    private static class TooltipTextView extends JComponent {
        private String text = "";
        private float lineSpacing = 1.2f; // 20% extra spacing

        public void setText(String text) {
            this.text = text != null ? text : "";
            repaint();
        }

        public void setLineSpacing(float spacing) {
            this.lineSpacing = spacing;
            repaint();
        }

        public int getPreferredHeight(int width) {
            FontMetrics fm = getFontMetrics(getFont());
            int lineHeight = Math.round(fm.getHeight() * lineSpacing);
            java.util.List<String> lines = wrapText(text, fm, width - getInsets().left - getInsets().right);
            return lines.size() * lineHeight + getInsets().top + getInsets().bottom;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(getBackground());
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(getForeground());
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int lineHeight = Math.round(fm.getHeight() * lineSpacing);
            int x = getInsets().left;
            int y = getInsets().top + fm.getAscent();
            int maxWidth = getWidth() - getInsets().left - getInsets().right;
            for (String line : wrapText(text, fm, maxWidth)) {
                g2.drawString(line, x, y);
                y += lineHeight;
            }
        }

        private java.util.List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (text == null || text.isEmpty()) {
                lines.add("");
                return lines;
            }
            String[] paragraphs = text.split("\n");
            for (String paragraph : paragraphs) {
                StringBuilder line = new StringBuilder();
                for (String word : paragraph.split(" ")) {
                    String testLine = line.isEmpty() ? word : line + " " + word;
                    int testWidth = fm.stringWidth(testLine);
                    if (testWidth > maxWidth && !line.isEmpty()) {
                        lines.add(line.toString());
                        line = new StringBuilder(word);
                    } else {
                        if (!line.isEmpty()) line.append(" ");
                        line.append(word);
                    }
                }
                lines.add(line.toString());
            }
            return lines;
        }
    }
}
