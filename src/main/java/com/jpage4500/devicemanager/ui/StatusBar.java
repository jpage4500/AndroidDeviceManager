package com.jpage4500.devicemanager.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 *
 */
public class StatusBar extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(StatusBar.class);

    private JLabel leftLabel;
    private JLabel centerLabel;
    private JLabel rightLabel;

    public StatusBar() {
        init();
    }

    private void init() {
        leftLabel = new JLabel();
        leftLabel.setBorder(new EmptyBorder(0, 10, 0, 0));

        centerLabel = new JLabel();
        centerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        rightLabel = new JLabel();
        rightLabel.setBorder(new EmptyBorder(0, 0, 0, 10));

        setLayout(new BorderLayout());
        add(leftLabel, BorderLayout.WEST);
        add(centerLabel, BorderLayout.CENTER);
        add(rightLabel, BorderLayout.EAST);
    }

    public void setLeftLabel(String text) {
        leftLabel.setText(text);
    }

    public void setCenterLabel(String text) {
        centerLabel.setText(text);
    }

    public void setRightLabel(String text) {
        rightLabel.setText(text);
    }

}
