package com.jpage4500.devicemanager.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 *
 */
public class EmptyView extends JLabel {
    private static final Logger log = LoggerFactory.getLogger(EmptyView.class);

    public EmptyView() {
        init();
    }

    private void init() {
        setText("No Android Devices!");
        setHorizontalTextPosition(SwingConstants.CENTER);
        setVerticalTextPosition(SwingConstants.BOTTOM);

        try {
            Image image = ImageIO.read(getClass().getResource("/images/logo.png"));
            if (image != null) {
                setIcon(new ImageIcon(image));
            }
        } catch (IOException e) {
            log.debug("init: Exception:{}", e.getMessage());
        }
        setVisible(true);
        setOpaque(false);
    }

    public void setEmptyText(String emptyText) {
        setText(emptyText);
    }

    public void setEmpty(boolean empty) {
        setVisible(empty);
    }

}
