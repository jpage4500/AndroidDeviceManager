package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class HoverLabel extends JButton {
    private static final Logger log = LoggerFactory.getLogger(HoverLabel.class);

    private final Color backgroundColor = new Color(224, 224, 224);

    private int progress = -1;
    private boolean isError;

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

    public void setBorder(int left, int right) {
        setBorder(new EmptyBorder(0, left, 0, right));
    }

    /**
     * Show a progress fill behind the text. Pass progress=-1 to clear.
     */
    public void setProgress(int progress, boolean isError) {
        this.progress = progress;
        this.isError = isError;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // paint AFTER super so the LAF's background fill doesn't wipe out the progress bar.
        // alpha is low enough that the icon/text underneath remain readable.
        if (progress >= 0) {
            int width = getWidth();
            int height = getHeight();
            int fillWidth = (int) Math.round(Math.min(100, progress) / 100.0 * width);
            Color base = isError ? Colors.COLOR_ERROR : Colors.COLOR_SUCCESS;
            Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), 80);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setColor(fill);
                g2.fillRect(0, 0, fillWidth, height);
            } finally {
                g2.dispose();
            }
        }
    }

    private void init() {
        setBorder(0, 0);
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
