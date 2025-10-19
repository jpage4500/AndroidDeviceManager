package com.jpage4500.devicemanager.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import javax.swing.JViewport;

/**
 * A layout manager that wraps components to new rows when they don't fit horizontally,
 * similar to FlowLayout but with proper wrapping behavior for grid tiles.
 */
public class WrapLayout extends FlowLayout {
    private static final Logger log = LoggerFactory.getLogger(WrapLayout.class);
    private int cachedWidth = -1;
    private Dimension cachedPreferredSize;

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        int currentWidth = target.getWidth();
        // Always recalculate if width changed or is zero
        if (cachedPreferredSize != null && currentWidth == cachedWidth && currentWidth > 0) {
            return new Dimension(cachedPreferredSize);
        }
        // Clear cache if width changed
        if (currentWidth != cachedWidth) {
            cachedWidth = -1;
            cachedPreferredSize = null;
        }
        Dimension result = layoutSize(target, true);
        if (currentWidth > 0) {
            cachedWidth = currentWidth;
            cachedPreferredSize = new Dimension(result);
        }
        return result;
    }

    @Override
    public void layoutContainer(Container target) {
        // Always clear cache if width changed
        if (target.getWidth() != cachedWidth) {
            cachedWidth = -1;
            cachedPreferredSize = null;
        }
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxWidth = target.getWidth() - (insets.left + insets.right + getHgap() * 2);
            int nmembers = target.getComponentCount();
            int x = insets.left + getHgap();
            int y = insets.top + getVgap();
            int rowHeight = 0;

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();
                    m.setSize(d.width, d.height);

                    if (x + d.width > maxWidth && x > insets.left + getHgap()) {
                        // Move to next row
                        x = insets.left + getHgap();
                        y += rowHeight + getVgap();
                        rowHeight = 0;
                    }

                    m.setLocation(x, y);
                    x += d.width + getHgap();
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }

            // Update preferred size after layout
            Dimension newPreferredSize = new Dimension(target.getWidth(), y + rowHeight + insets.bottom + getVgap());
            if (!newPreferredSize.equals(cachedPreferredSize)) {
                cachedPreferredSize = newPreferredSize;
                cachedWidth = target.getWidth();
                // Notify parent that our preferred size has changed
                Container parent = target.getParent();
                if (parent != null) {
                    parent.revalidate();
                    parent.repaint(); // Ensure repaint after revalidate
                }
            }
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            // Get the actual width of the target container
            int targetWidth = target.getWidth();

            // If target width is 0, try to get it from viewport or parent
            if (targetWidth == 0) {
                Container parent = target.getParent();
                if (parent instanceof JViewport) {
                    targetWidth = parent.getWidth();
                } else if (parent != null) {
                    targetWidth = parent.getWidth();
                } else {
                    // Fallback to a reasonable default
                    targetWidth = 800;
                }
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            // Ensure we have a minimum width to work with
            if (maxWidth <= 0) {
                maxWidth = 200; // minimum reasonable width
            }

            // Fit components into the allowed width
            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        // Start new row
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = d.width;
                        rowHeight = d.height;
                    } else {
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }

                    if (i < nmembers - 1) {
                        rowWidth += hgap;
                    }
                }
            }
            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + vgap * 2;

            return dim;
        }
    }

    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }
}
