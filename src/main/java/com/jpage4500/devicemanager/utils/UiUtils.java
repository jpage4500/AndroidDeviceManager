package com.jpage4500.devicemanager.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class UiUtils {
    private static final Logger log = LoggerFactory.getLogger(UiUtils.class);

    public static BufferedImage getImage(String path, int size) {
        return getImage(path, size, size);
    }

    public static BufferedImage getImage(String path, int w, int h) {
        return getImage(path, w, h, null);
    }

    public static BufferedImage getImage(String path, int w, int h, Color color) {
        try {
            // library offers MUCH better image scaling than ImageIO
            BufferedImage image = Thumbnails.of(UiUtils.class.getResource("/images/" + path)).size(w, h).asBufferedImage();
            if (image != null) {
                if (color != null) return replaceColor(image, color);
                else return image;
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

    public static BufferedImage replaceColor(BufferedImage image, Color color) {
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
}
