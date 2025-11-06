package com.jpage4500.devicemanager.ui.views;

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
    private static final Color TOOLTIP_BACKGROUND = new Color(255, 255, 200); // Light yellow
    private static final Color TOOLTIP_BORDER = new Color(180, 180, 150); // Darker border
    private static final int PADDING = 8;
    public static final int MAX_HEIGHT = 200; // Maximum tooltip height

    private final JTextArea textArea;

    public MessageTooltipPanel(Window owner) {
        super(owner);
        setAlwaysOnTop(true);
        setFocusableWindowState(false);

        // Create text area for multi-line wrapping
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setBackground(TOOLTIP_BACKGROUND);
        textArea.setForeground(Color.BLACK);
        textArea.setBorder(new CompoundBorder(
            new LineBorder(TOOLTIP_BORDER, 1),
            new EmptyBorder(PADDING, PADDING, PADDING, PADDING)
        ));

        getContentPane().add(textArea);
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

        textArea.setText(text);

        // Calculate required height based on text and width
        int preferredHeight = calculateHeight(text, width);
        int actualHeight = Math.min(preferredHeight, MAX_HEIGHT);

        boolean showAtTop = mouseY >= (bottomY - actualHeight);
        int y;
        if (showAtTop) {
            y = topY;
        } else {
            // Adjust y position so bottom of tooltip aligns with provided y coordinate
            y = bottomY - actualHeight;
        }

        // Set size and position
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
     * Calculate the height needed to display the text with wrapping
     */
    private int calculateHeight(String text, int width) {
        // Set width to calculate wrapped height accurately
        textArea.setSize(width, 0);

        // Get preferred size which includes wrapped lines
        Dimension preferredSize = textArea.getPreferredSize();

        // Return just the preferred height (padding is already included in border)
        return preferredSize.height;
    }

    /**
     * Check if tooltip is currently visible
     */
    public boolean isTooltipVisible() {
        return isVisible();
    }
}
