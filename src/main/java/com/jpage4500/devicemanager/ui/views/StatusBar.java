package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

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
            leftLabel.setBackground(Color.BLUE);
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

    public String getCenterLabelText() {
        return centerLabel != null ? centerLabel.getText() : null;
    }

    public void setRightLabel(String text) {
        if (rightLabel == null) {
            rightLabel = createLabel("center");
            add(rightLabel, BorderLayout.EAST);
        }
        rightLabel.setText(text);
    }

    public void setLeftComponent(JComponent component) {
        add(component, BorderLayout.WEST);
    }

    public void setRightComponent(JComponent component) {
        add(component, BorderLayout.EAST);
    }

    public void setCenterComponent(JComponent component) {
        add(component, BorderLayout.CENTER);
    }

    private JLabel createLabel(String desc) {
        JLabel label = new JLabel();
        label.setBorder(new EmptyBorder(0, 10, 0, 10));
        switch (desc) {
            case "center":
                label.setHorizontalAlignment(SwingConstants.CENTER);
                break;
        }
        UiUtils.addLeftClickListener(label, e -> {
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
