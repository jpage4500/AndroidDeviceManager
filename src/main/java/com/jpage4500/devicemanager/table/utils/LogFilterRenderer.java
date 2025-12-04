package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.utils.UiUtils;

import javax.swing.*;
import java.awt.*;

public class LogFilterRenderer extends DefaultListCellRenderer {
    private final JSeparator separator;

    public LogFilterRenderer() {
        separator = new JSeparator(JSeparator.HORIZONTAL);
        //separator.setPreferredSize(new Dimension(10, 20));
        UiUtils.setEmptyBorder(this);
        setOpaque(true);
    }

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value instanceof LogFilter filter) {
            if (filter.name == null) return separator;
            setText(filter.name);
        }

        // TODO: alternate background colors?
//        if (isSelected) {
//            setBackground(list.hasFocus() ? Color.BLUE : Colors.COLOR_LIST_SELECTED_NO_FOCUS);
//            setForeground(Color.WHITE);
//        } else {
//            setForeground(Color.BLACK);
//            if (index % 2 == 0) setBackground(Color.WHITE);
//            else setBackground(Colors.COLOR_LIGHT_GRAY);
//        }

        return this;
    }
}
