package com.jpage4500.devicemanager.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;

public class IconRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(IconRenderer.class);

    public IconRenderer() {
        setOpaque(true); //MUST do this for background to show up.
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        Icon icon = (Icon) object;
        setIcon(icon);
        if (isSelected) {
            setBackground(Color.BLACK);
        }
        return this;
    }
}