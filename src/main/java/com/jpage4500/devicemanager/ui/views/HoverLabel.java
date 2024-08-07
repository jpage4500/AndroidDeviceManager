package com.jpage4500.devicemanager.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class HoverLabel extends JButton {
    private static final Logger log = LoggerFactory.getLogger(HoverLabel.class);

    private final Color backgroundColor = new Color(224,224,224);

    public HoverLabel() {
        init();
    }

    public HoverLabel(Icon icon) {
        super(icon);
        init();
    }

    public HoverLabel(String s) {
        super(s);
        init();
    }

    public HoverLabel(String s, Icon icon) {
        super(s, icon);
        init();
    }

    private void init() {
        setBorder(new EmptyBorder(0, 10, 0, 10));
        //setContentAreaFilled(false);
        //setBorderPainted(false);
        //setFocusPainted(false);
        setBackground(null);
        setOpaque(true);
        getModel().addChangeListener(e -> {
            ButtonModel model = (ButtonModel) e.getSource();
            if (model.isRollover()) {
                setBackground(backgroundColor);
            } else {
                setBackground(null);
            }
        });
    }
}
