package com.jpage4500.devicemanager.ui.views;

import javax.swing.*;
import java.awt.*;

public class ComboIcon implements Icon {
    private final Icon iconLeft;
    private final Icon iconRight;

    public ComboIcon(Icon leftIcon, Icon rightIcon) {
        this.iconLeft = leftIcon;
        this.iconRight = rightIcon;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        drawIcon(iconLeft, c, g, x, y);
        x += iconWidth(iconLeft);
        drawIcon(iconRight, c, g, x, y);
    }

    @Override
    public int getIconWidth() {
        return iconWidth(iconRight) + iconWidth(iconLeft);
    }

    private int iconWidth(Icon icon) {
        return (icon != null) ? icon.getIconWidth() : 0;
    }

    @Override
    public int getIconHeight() {
        return iconRight.getIconHeight();
    }

    private void drawIcon(Icon icon, Component c, Graphics g, int x, int y) {
        if (icon != null) icon.paintIcon(c, g, x, y);
    }

}
