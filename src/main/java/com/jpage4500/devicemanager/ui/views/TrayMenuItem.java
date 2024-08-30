package com.jpage4500.devicemanager.ui.views;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class TrayMenuItem extends JMenuItem {
    public TrayMenuItem(String text) {
        super(text);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    }

    public TrayMenuItem(String text, Icon icon) {
        super(text, icon);
        setLayout(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    }

    public void addButton(String text, ActionListener listener) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(0, 2, 0, 2) );
        button.addActionListener(listener);
        add(button);

        setPreferredSize(new Dimension(getPreferredSize().width + button.getPreferredSize().width, getPreferredSize().height));
    }

}
