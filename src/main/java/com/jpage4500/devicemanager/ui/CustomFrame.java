package com.jpage4500.devicemanager.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.prefs.Preferences;

import javax.swing.*;

/**
 *
 */
public class CustomFrame extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(CustomFrame.class);

    private static final String FRAME_X = "frame-x";
    private static final String FRAME_Y = "frame-y";
    private static final String FRAME_W = "frame-w";
    private static final String FRAME_H = "frame-h";

    public CustomFrame() throws HeadlessException {
        init();
    }

    public CustomFrame(String title) throws HeadlessException {
        super(title);
        init();
    }

    private void init() {
        restoreFrame();

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                saveFrameSize();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                saveFrameSize();
            }
        });
    }

    private void saveFrameSize() {
        Preferences prefs = Preferences.userRoot();
        prefs.putInt(FRAME_X, getX());
        prefs.putInt(FRAME_Y, getY());
        prefs.putInt(FRAME_W, getWidth());
        prefs.putInt(FRAME_H, getHeight());
    }

    private void restoreFrame() {
        Preferences prefs = Preferences.userRoot();
        int x = prefs.getInt(FRAME_X, 200);
        int y = prefs.getInt(FRAME_Y, 200);
        int w = prefs.getInt(FRAME_W, 500);
        int h = prefs.getInt(FRAME_H, 300);

        log.debug("restoreFrame: x:{}, y:{}, w:{}, h:{}", x, y, w, h);
        setLocation(x, y);
        setSize(w, h);
    }


}
