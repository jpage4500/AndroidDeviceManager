package com.jpage4500.devicemanager.ui.views;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * A layout manager that arranges components in evenly spaced grid rows,
 * centering each row and distributing items like macOS Finder grid view.
 */
public class EvenGridLayout implements LayoutManager {
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
            int width = parent.getWidth();
            int n = parent.getComponentCount();
            int maxRowItems = Math.max(1, (width - insets.left - insets.right + hgap) / (tileWidth + hgap));
            int rows = (int) Math.ceil(n / (double) maxRowItems);
            int height = rows * tileHeight + (rows - 1) * vgap + insets.top + insets.bottom;
            return new Dimension(width, height);
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
            int maxRowItems = Math.max(1, (width - insets.left - insets.right + hgap) / (tileWidth + hgap));
            List<Component> visible = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Component c = parent.getComponent(i);
                if (c.isVisible()) visible.add(c);
            }
            int total = visible.size();
            int rows = (int) Math.ceil(total / (double) maxRowItems);
            int idx = 0;
            for (int row = 0; row < rows; row++) {
                int y = insets.top + row * (tileHeight + vgap);
                for (int col = 0; col < maxRowItems; col++) {
                    int x = insets.left + col * (tileWidth + hgap);
                    if (idx < total) {
                        Component c = visible.get(idx);
                        c.setBounds(x, y, tileWidth, tileHeight);
                        idx++;
                    }
                    // else: leave empty space for alignment
                }
            }
        }
    }

}

