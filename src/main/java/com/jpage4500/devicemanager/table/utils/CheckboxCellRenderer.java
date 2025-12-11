package com.jpage4500.devicemanager.table.utils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Renderer for checkbox column
 */
public class CheckboxCellRenderer extends DefaultTableCellRenderer {
    private final JCheckBox checkbox = new JCheckBox();

    public CheckboxCellRenderer() {
        checkbox.setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        checkbox.setSelected(value != null && (Boolean) value);
        checkbox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return checkbox;
    }
}
