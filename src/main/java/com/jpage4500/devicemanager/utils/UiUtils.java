package com.jpage4500.devicemanager.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

public class UiUtils {
    private static final Logger log = LoggerFactory.getLogger(UiUtils.class);

    public static BufferedImage getImage(String path, int size) {
        return getImage(path, size, size);
    }

    public static BufferedImage getImage(String path, int w, int h) {
        try {
            // library offers MUCH better image scaling than ImageIO
            BufferedImage image = Thumbnails.of(UiUtils.class.getResource("/images/" + path)).size(w, h).asBufferedImage();
            if (image != null) {
                log.debug("getImage: loaded:{}, w:{}", path, w);
                return image;
            }
            log.error("getImage: image not found! {}", path);
        } catch (Exception e) {
            log.error("getImage: Exception:{}", e.getMessage());
        }
        return null;
    }

    public static ImageIcon getImageIcon(String imageName, int size) {
        return getImageIcon(imageName, size, size);
    }

    public static ImageIcon getImageIcon(String imageName, int w, int h) {
        Image image = getImage(imageName, w, h);
        if (image != null) return new ImageIcon(image);
        else return null;
    }

    private static BufferedImage replaceColor(BufferedImage image, Color color) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage dyed = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dyed.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.setComposite(AlphaComposite.SrcAtop);
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return dyed;
    }

    /**
     * create a 'standard' toolbar button with 40x40 image and label below
     */
    public static JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        BufferedImage image = UiUtils.getImage(imageName, 40, 40);
        //image = replaceColor(image, new Color(0, 38, 255, 184));
        JButton button = new JButton(label, new ImageIcon(image));

        button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        if (tooltip != null) button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.addActionListener(listener);
        toolbar.add(button);
        return button;
    }

}
