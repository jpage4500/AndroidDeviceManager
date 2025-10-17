package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 *
 */
public class EmptyView {
    private static final Logger log = LoggerFactory.getLogger(EmptyView.class);

    private boolean showBackground;
    private String emptyText;
    private Image emptyImage;
    private Font emptyTextFont;

    public EmptyView() {
    }

    public void setShowBackground(boolean showBackground) {
        this.showBackground = showBackground;
    }

    public void setEmptyText(String emptyText) {
        this.emptyText = emptyText;
    }

    public void setEmptyImage(Image emptyImage) {
        this.emptyImage = emptyImage;
    }

    public void setEmptyTextFont(Font emptyTextFont) {
        this.emptyTextFont = emptyTextFont;
    }

    public void paint(Graphics graphics, int width, int height, int yOffset) {
        if (showBackground) {
            if (emptyImage == null) {
                emptyImage = UiUtils.getImage("empty_image.png", 500);
            }
            if (emptyImage != null) {
                int imgW = emptyImage.getWidth(null);
                int imgH = emptyImage.getHeight(null);
                double aspectRatio = width / (double) imgW;
                double drawImageH = imgH * aspectRatio;
                // make image semi-transparent
                Graphics2D g2d = (Graphics2D) graphics.create();
                g2d.setComposite(AlphaComposite.SrcOver.derive(0.2f));
                g2d.drawImage(emptyImage, 0, yOffset, width, (int) drawImageH, null);
                g2d.dispose();
            }
        }

        if (emptyText != null) {
            // draw empty text in center
            if (emptyTextFont == null) {
                emptyTextFont = graphics.getFont().deriveFont(Font.BOLD, 22);
            }
            graphics.setFont(emptyTextFont);
            FontMetrics fontMetrics = graphics.getFontMetrics(emptyTextFont);
            int textH = emptyTextFont.getSize() * (fontMetrics.getAscent() + fontMetrics.getDescent()) / fontMetrics.getAscent();
            int textW = fontMetrics.stringWidth(emptyText);
            int x = width / 2 - (textW / 2);
            int y = (height / 2);
            // prevent drawing on top of header
            if (y < (yOffset * 2)) y = yOffset * 2;
            // don't draw if no available space
            if (x >= 0 && y >= 0 && (height - yOffset > textH)) {
                graphics.drawString(emptyText, x, y);
            }
        }
    }

}
