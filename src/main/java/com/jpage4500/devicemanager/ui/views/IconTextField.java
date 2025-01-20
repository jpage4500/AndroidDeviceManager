package com.jpage4500.devicemanager.ui.views;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class IconTextField extends JTextField {
    private static final int ICON_SPACING = 4;

    private Border border;
    private Border origBorder;
    private Icon icon;

    public IconTextField() {
        super();
        init();
    }

    public IconTextField(int cols) {
        super(cols);
        init();
    }

    private void init() {
        origBorder = getBorder();
        border = origBorder;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (icon != null) {
            Insets iconInsets = origBorder.getBorderInsets(this);
            int vPos = (getHeight() - icon.getIconHeight()) / 2;
            if (vPos < 0) vPos = 0;
            icon.paintIcon(this, graphics, iconInsets.left, vPos);
        }
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
        setBorder(origBorder);
    }

    public void setIconSpacing(int spacing) {
    }

    @Override
    public void setBorder(Border border) {
        origBorder = border;
        if (icon == null) {
            this.border = border;
        } else {
            Border margin = BorderFactory.createEmptyBorder(0, icon.getIconWidth() + ICON_SPACING, 0, 0);
            this.border = BorderFactory.createCompoundBorder(border, margin);
        }
        super.setBorder(this.border);
    }
}