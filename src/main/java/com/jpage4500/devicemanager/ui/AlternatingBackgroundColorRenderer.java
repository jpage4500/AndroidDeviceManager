package com.jpage4500.devicemanager.ui;

import javax.swing.*;
import java.awt.*;

public class AlternatingBackgroundColorRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (isSelected) setBackground(Color.BLUE);
        else if (index % 2 == 0) setBackground(Color.WHITE);
        else setBackground(new Color(232, 232, 232));
        setOpaque(true);
        return this;
    }
}
