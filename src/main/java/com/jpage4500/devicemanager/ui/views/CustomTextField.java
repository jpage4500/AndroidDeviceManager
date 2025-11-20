package com.jpage4500.devicemanager.ui.views;

import javax.swing.*;
import java.awt.*;

public class CustomTextField extends JTextField {
    //private static final Logger log = LoggerFactory.getLogger(CustomTextField.class);
    private boolean isTruncatedRight = false;

    public void setTruncatedRight(boolean truncatedRight) {
        this.isTruncatedRight = truncatedRight;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isTruncatedRight) {
            // if not truncated, just use the default rendering
            super.paintComponent(g);
            return;
        }
        // paint the background
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        // get the current text and font
        String text = getText();
        Font font = getFont();
        FontMetrics metrics = g.getFontMetrics(font);

        Insets insets = getInsets();
        int availableWidth = getWidth() - insets.left - insets.right;
        int textWidth = metrics.stringWidth(text);

        String visibleText = text;
        if (textWidth > availableWidth) {
            // start from the right side, chop off the left until it fits
            int start = 1;
            int textLength = text.length();
            while (start < textLength - 1 && metrics.stringWidth(text.substring(start)) > availableWidth) {
                start++;
            }
            visibleText = text.substring(start + 1);
        }

        // set color and font
        g.setColor(getForeground());
        g.setFont(font);

        // compute baseline
        int x = insets.left; // getWidth() - insets.right - metrics.stringWidth(visibleText);
        int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();

        // draw the text
        g.drawString(visibleText, x, y);

        // draw the border if it exists
        if (getBorder() != null) {
            paintBorder(g);
        }
    }
}
