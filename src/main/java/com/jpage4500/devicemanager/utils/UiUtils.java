package com.jpage4500.devicemanager.utils;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;

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
            Thumbnails.Builder<URL> imageBuilder = Thumbnails.of(UiUtils.class.getResource("/images/" + path));
            imageBuilder = imageBuilder.size(w, h);
            BufferedImage image = imageBuilder.asBufferedImage();
            if (image != null) {
                if (color != null) return replaceColor(image, color);
                else return image;
            }
            log.error("getImage: image not found! {}", path);
        } catch (Exception e) {
            log.error("getImage: Exception: url:{}, {}", path, e.getMessage());
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

    public static void setEmptyBorder(JComponent component) {
        setEmptyBorder(component, 10, 10);
    }

    public static void setEmptyBorder(JComponent component, int left, int right) {
        component.setBorder(new EmptyBorder(0, left, 0, right));
    }

    /**
     * set text
     * - if longer than maxLen, truncate and show tooltip
     */
    public static void setText(JComponent component, String text, int maxLen) {
        // display text
        int textLen = TextUtils.length(text);
        boolean isTruncated = textLen > maxLen;
        // TODO: add flag to truncate from beginning or end
        String displayText = isTruncated ? TextUtils.truncateStart(text, maxLen) : text;
        String hintText = isTruncated ? text : null;

        if (component instanceof JLabel label) {
            label.setText(displayText);
            label.setToolTipText(hintText);
        } else if (component instanceof AbstractButton button) {
            button.setText(displayText);
            button.setToolTipText(hintText);
        }
    }

    public interface ClickListener {
        void onClick(MouseEvent e);
    }

    /**
     * add click listener (left-click)
     */
    public static void addClickListener(JComponent component, ClickListener listener) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                super.mouseClicked(mouseEvent);
                if (SwingUtilities.isLeftMouseButton(mouseEvent)) {
                    listener.onClick(mouseEvent);
                }
            }
        });
    }

    public static JMenuItem addPopupMenuItem(JPopupMenu popupMenu, String label, ActionListener listener) {
        return addPopupMenuItem(popupMenu, label, null, listener);
    }

    public static JMenuItem addPopupMenuItem(JPopupMenu popupMenu, String label, String iconName, ActionListener listener) {
        Icon icon = null;
        if (iconName != null) {
            icon = getImageIcon(iconName, 15);
        }
        JMenuItem menuItem = new JMenuItem(label, icon);
        menuItem.addActionListener(listener);
        popupMenu.add(menuItem);
        return menuItem;
    }

    public static JMenuItem addMenuItem(JMenu menu, String label, ActionListener listener) {
        JMenuItem menuItem = new JMenuItem(label);
        menuItem.addActionListener(listener);
        menu.add(menuItem);
        return menuItem;
    }

}
