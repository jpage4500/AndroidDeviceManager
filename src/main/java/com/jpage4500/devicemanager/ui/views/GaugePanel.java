package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;

import net.miginfocom.swing.MigLayout;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

/**
 * a card for one "how full is it" value: icon, title, a bar, the percent, and lines of supporting
 * values underneath ("Free: 108.0 GB, Total: 221.8 GB")
 * <p>
 * the bar is the reason this exists instead of a plain name/value row - half a disk is a fraction,
 * and a fraction is read faster as a length than as a number.
 */
public class GaugePanel extends CardPanel {
    private static final int ICON_SIZE = 36;
    private static final int BAR_HEIGHT = 6;
    // room for "100%", so gauges stacked above each other end their bars at the same place
    private static final int PERCENT_WIDTH = 42;

    // a card is rebuilt on every device refresh, and loading+scaling a png each time isn't free
    private static final Map<String, ImageIcon> iconCache = new HashMap<>();

    /**
     * @param icon      shown at the left of the card
     * @param iconColor color to tint the icon, or null to use the image as-is (the battery icons are
     *                  already colored by level)
     * @param percent   how full, 0-100, or null to leave out the bar and percent - a device that
     *                  doesn't report a total still shows what it did report
     * @param detail    supporting values, one line each; empty lines are skipped
     */
    public GaugePanel(Icons icon, Color iconColor, String title, Integer percent, String... detail) {
        super(new MigLayout("fillx, insets 12 14 12 14", "[]14[grow]", "[]"));

        ImageIcon iconImage = getIcon(icon, iconColor);
        if (iconImage != null) add(new JLabel(iconImage), "aligny center");

        // everything but the icon goes in here: the icon sits beside all of it, vertically centered,
        // without each row having to know how many other rows there are
        JPanel content = new JPanel(new MigLayout("wrap 2, fillx, insets 0, gapy 5", "[grow]10[]"));
        content.setOpaque(false);

        JLabel titleView = new JLabel(title);
        titleView.setFont(deriveFont(titleView.getFont(), Font.BOLD, 1));
        titleView.setForeground(Colors.COLOR_CARD_LABEL);
        content.add(titleView, "growx, span 2");

        if (percent != null) {
            content.add(new GaugeBar(percent), "growx, height " + BAR_HEIGHT + "!");
            // in the same row as the bar it labels, and wide enough that 2 and 3 digit values line up
            JLabel percentView = new JLabel(percent + "%");
            percentView.setFont(deriveFont(percentView.getFont(), Font.BOLD, 3));
            percentView.setForeground(Colors.COLOR_CARD_LABEL);
            percentView.setHorizontalAlignment(SwingConstants.RIGHT);
            content.add(percentView, "aligny center, wmin " + PERCENT_WIDTH);
        }

        for (String line : detail) {
            if (TextUtils.isEmpty(line)) continue;
            JLabel detailView = new JLabel(line);
            detailView.setFont(deriveFont(detailView.getFont(), Font.PLAIN, -1));
            detailView.setForeground(Colors.COLOR_CARD_DETAIL);
            content.add(detailView, "growx, span 2");
        }
        add(content, "growx");
    }

    /**
     * size relative to the LAF font instead of a fixed point size, so these still scale with the
     * platform's font size
     */
    private static Font deriveFont(Font font, int style, int sizeDelta) {
        return font.deriveFont(style, font.getSize() + (float) sizeDelta);
    }

    private static ImageIcon getIcon(Icons icon, Color color) {
        if (icon == null) return null;
        return iconCache.computeIfAbsent(icon + "-" + color, key -> UiUtils.getImageIcon(icon, ICON_SIZE, ICON_SIZE, color));
    }

    /**
     * the bar itself: a rounded track with the used portion filled in, plus a dot marking the end of
     * the track (without it a nearly-full bar looks like it runs off the edge of the card)
     */
    private static class GaugeBar extends JComponent {
        // between the end of the track and the dot after it
        private static final int GAP = 2;

        private final int percent;

        GaugeBar(int percent) {
            // a bad reading shouldn't be able to draw outside the component
            this.percent = Math.max(0, Math.min(100, percent));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int height = getHeight();
                // the dot is as wide as the bar is tall, so it reads as a round cap
                int dotSize = height;
                int trackWidth = Math.max(0, getWidth() - dotSize - GAP);
                int fillWidth = Math.round(trackWidth * percent / 100f);

                g2.setColor(Colors.COLOR_GAUGE_TRACK);
                g2.fillRoundRect(0, 0, trackWidth, height, height, height);
                g2.setColor(Colors.COLOR_CARD_ACCENT);
                if (fillWidth > 0) g2.fillRoundRect(0, 0, fillWidth, height, height, height);
                g2.fillOval(getWidth() - dotSize, 0, dotSize, height);
            } finally {
                g2.dispose();
            }
        }
    }
}
