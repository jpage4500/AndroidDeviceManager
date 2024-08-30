package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.utils.Colors;

import javax.swing.*;
import java.awt.*;

public class AlternatingBackgroundColorRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (isSelected) {
            setBackground(list.hasFocus() ? Color.BLUE : new Color(0, 81, 255, 108));
            setForeground(Color.WHITE);
        } else {
            setForeground(Color.BLACK);
            if (index % 2 == 0) setBackground(Color.WHITE);
            else setBackground(Colors.COLOR_LIGHT_GRAY);
        }
        setOpaque(true);
        return this;
    }
}
