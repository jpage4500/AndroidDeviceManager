package com.jpage4500.devicemanager.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;

public class IconTableCellRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(IconTableCellRenderer.class);

    public IconTableCellRenderer() {
        setOpaque(true);

        Border margin = new EmptyBorder(0, 10, 0, 0);
        setBorder(margin);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        Icon icon = (Icon) object;
        setIcon(icon);

        if (isSelected) {
            setBackground(table.getSelectionBackground());
        } else {
            setBackground(Color.WHITE);
        }
        return this;
    }
}