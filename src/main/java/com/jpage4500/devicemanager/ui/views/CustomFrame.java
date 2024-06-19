package com.jpage4500.devicemanager.ui.views;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 *
 */
public class CustomFrame extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(CustomFrame.class);

    private static final String FRAME_X = "frame-x";
    private static final String FRAME_Y = "frame-y";
    private static final String FRAME_W = "frame-w";
    private static final String FRAME_H = "frame-h";

    protected String prefKey;

    public CustomFrame() throws HeadlessException {
        init();
    }

    public CustomFrame(String prefKey) throws HeadlessException {
        super("");
        this.prefKey = prefKey;
        init();
    }

    private void init() {
        restoreFrameSize();

        //addComponentListener(new ComponentAdapter() {
        //    @Override
        //    public void componentResized(ComponentEvent e) {
        //        //saveFrameSize();
        //        //log.trace("componentResized: w:{}, h:{}", getWidth(), getHeight());
        //    }
        //
        //    @Override
        //    public void componentMoved(ComponentEvent e) {
        //        //saveFrameSize();
        //        //log.trace("componentMoved: x:{}, y:{}", getX(), getY());
        //    }
        //});
    }

    /**
     * save current frame size
     */
    protected void saveFrameSize() {
        Preferences prefs = Preferences.userRoot();
        prefs.putInt(prefKey + "-" + FRAME_X, getX());
        prefs.putInt(prefKey + "-" + FRAME_Y, getY());
        prefs.putInt(prefKey + "-" + FRAME_W, getWidth());
        prefs.putInt(prefKey + "-" + FRAME_H, getHeight());
        //log.trace("saveFrameSize: {}: x:{}, y:{}, w:{}, h:{}", prefKey, getX(), getY(), getWidth(), getHeight());
    }

    /**
     * restore frame size
     */
    private void restoreFrameSize() {
        Preferences prefs = Preferences.userRoot();
        int x = prefs.getInt(prefKey + "-" + FRAME_X, -1);
        int y = prefs.getInt(prefKey + "-" + FRAME_Y, -1);
        int w = prefs.getInt(prefKey + "-" + FRAME_W, -1);
        int h = prefs.getInt(prefKey + "-" + FRAME_H, -1);

        if (w == -1 || h == -1) {
            w = 800;
            h = 300;
        }
        if (x == -1 || y == -1) {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            x = (screenSize.width - w) / 2;
            y = (screenSize.height - h) / 2;
        }

        //log.trace("restoreFrame: {}: x:{}, y:{}, w:{}, h:{}", prefKey, x, y, w, h);
        setLocation(x, y);
        setSize(w, h);
    }

}
