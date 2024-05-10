package com.jpage4500.devicemanager.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class UiUtils {
    private static final Logger log = LoggerFactory.getLogger(UiUtils.class);

    public static Image getImage(String path, int size) {
        return getImage(path, size, size);
    }

    public static Image getImage(String path, int w, int h) {
        try {
            // library offers MUCH better image scaling than ImageIO
            Image icon = Thumbnails.of(UiUtils.class.getResource("/images/" + path)).size(w, h).asBufferedImage();
            if (icon != null) {
                log.debug("getImage: loaded:{}, w:{}", path, w);
                return icon;
            }
            log.error("loadImage: image not found! {}", path);
        } catch (Exception e) {
            log.error("loadImage: Exception:{}", e.getMessage());
        }
        return null;
    }

    public static ImageIcon getIcon(String imageName, int size) {
        return getIcon(imageName, size, size);
    }

    public static ImageIcon getIcon(String imageName, int w, int h) {
        Image image = getImage(imageName, w, h);
        if (image != null) return new ImageIcon(image);
        else return null;
    }

    /**
     * create a 'standard' toolbar button with 40x40 image and label below
     */
    public static JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        ImageIcon icon = UiUtils.getIcon(imageName, 40, 40);
        JButton button = new JButton(label, icon);

        button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        if (tooltip != null) button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.addActionListener(listener);
        toolbar.add(button);
        return button;
    }

}
