package com.jpage4500.devicemanager.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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

    private ClickListener leftListener;
    private ClickListener centerListener;
    private ClickListener rightListener;

    public StatusBar() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout());
    }

    public void setLeftLabel(String text) {
        if (leftLabel == null) {
            leftLabel = createLabel("left");
            add(leftLabel, BorderLayout.WEST);
        }
        leftLabel.setText(text);
    }

    public void setCenterLabel(String text) {
        if (centerLabel == null) {
            centerLabel = createLabel("center");
            add(centerLabel, BorderLayout.CENTER);
        }
        centerLabel.setText(text);
    }

    public void setRightLabel(String text) {
        if (rightLabel == null) {
            rightLabel = createLabel("center");
            add(rightLabel, BorderLayout.EAST);
        }
        rightLabel.setText(text);
    }

    public void setRightComponent(JComponent component) {
        add(component, BorderLayout.EAST);
//        component.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                if (rightListener != null) rightListener.onClicked();
//            }
//        });
    }

    private JLabel createLabel(String desc) {
        JLabel label = new JLabel();
        label.setBorder(new EmptyBorder(0, 10, 0, 10));
        switch (desc) {
            case "center":
                label.setHorizontalAlignment(SwingConstants.CENTER);
                break;
        }
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                switch (desc) {
                    case "left":
                        if (leftListener != null) leftListener.onClicked();
                        break;
                    case "center":
                        if (centerListener != null) centerListener.onClicked();
                        break;
                    case "right":
                        if (rightListener != null) rightListener.onClicked();
                        break;
                }
            }
        });
        return label;
    }

    public interface ClickListener {
        void onClicked();
    }

    public void setLeftLabelListener(ClickListener listener) {
        this.leftListener = listener;
    }

    public void setCenterLabelListener(ClickListener listener) {
        this.centerListener = listener;
    }

    public void setRightLabelListener(ClickListener listener) {
        this.rightListener = listener;
    }

}
