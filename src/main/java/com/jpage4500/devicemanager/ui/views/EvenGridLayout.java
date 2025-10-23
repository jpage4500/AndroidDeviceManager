package com.jpage4500.devicemanager.ui.views;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A layout manager that arranges components in evenly spaced grid rows,
 * centering each row and distributing items like macOS Finder grid view.
 */
public class EvenGridLayout implements LayoutManager {
    public static final int EXTRA_SPACE = 20;
    private final int hgap;
    private final int vgap;
    private int tileWidth;
    private int tileHeight;

    public EvenGridLayout(int hgap, int vgap, int minTileWidth) {
        this.hgap = hgap;
        this.vgap = vgap;
        this.tileWidth = minTileWidth;
        this.tileHeight = minTileWidth * 3 / 4;
    }

    public void setTileWidth(int width) {
        this.tileWidth = width;
        this.tileHeight = width * 3 / 4;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int n = parent.getComponentCount();
            if (n == 0) return new Dimension(0, 0);
            int extraTop = EXTRA_SPACE; // space above first row
            int extraBottom = EXTRA_SPACE; // space below last row
            // Always use a fixed default column count for preferred size (e.g., 4 columns)
            int defaultCols = 1;
            int maxCols = defaultCols;
            int rows = (int) Math.ceil(n / (double) maxCols);
            int prefWidth = maxCols * tileWidth + (maxCols + 1) * hgap + insets.left + insets.right;
            int prefHeight = extraTop + rows * tileHeight + (rows - 1) * vgap + extraBottom + insets.top + insets.bottom;
            return new Dimension(prefWidth, prefHeight);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int width = parent.getWidth();
            int n = parent.getComponentCount();
            if (n == 0) return;
            int extraTop = EXTRA_SPACE; // space above first row
            int extraBottom = EXTRA_SPACE; // space below last row
            List<Component> visible = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Component c = parent.getComponent(i);
                if (c.isVisible()) visible.add(c);
            }
            int total = visible.size();
            int maxCols = Math.max(1, (width - insets.left - insets.right + hgap) / (tileWidth));
            int availableWidth = width - insets.left - insets.right;
            int gaps = maxCols + 1;
            int gap = (availableWidth - (maxCols * tileWidth)) / gaps;
            int[] colX = new int[maxCols];
            for (int col = 0; col < maxCols; col++) {
                colX[col] = insets.left + gap + col * (tileWidth + gap);
            }
            int rows = (int) Math.ceil(total / (double) maxCols);
            int idx = 0;
            for (int row = 0; row < rows; row++) {
                int y = insets.top + extraTop + row * (tileHeight + vgap);
                for (int col = 0; col < maxCols; col++) {
                    if (idx < total) {
                        Component c = visible.get(idx);
                        c.setBounds(colX[col], y, tileWidth, tileHeight);
                        idx++;
                    }
                }
            }
        }
    }

    // Utility to update preferred size for JScrollPane scrolling
    public static void updatePreferredSize(Container gridPanel) {
        LayoutManager layout = gridPanel.getLayout();
        if (layout instanceof EvenGridLayout) {
            Dimension pref = ((EvenGridLayout) layout).preferredLayoutSize(gridPanel);
            gridPanel.setPreferredSize(pref);
            gridPanel.revalidate();
        }
    }

}
