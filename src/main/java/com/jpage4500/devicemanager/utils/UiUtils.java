package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.data.Icons;
import net.coobird.thumbnailator.Thumbnails;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.net.URL;

public class UiUtils {
    private static final Logger log = LoggerFactory.getLogger(UiUtils.class);

    public static final int IMG_SIZE_SMALL = 15;
    public static final int IMG_SIZE_ICON = 20;
    public static final int IMG_SIZE_TOOLBAR = 40;
    public static final int IMG_SIZE_TOOLBAR_SMALL = 20;

    // ------------------------------------------------------------------------
    // getImage:
    // - BufferedImage scaled using Thumbnails library
    // ------------------------------------------------------------------------

    public static BufferedImage getImage(Icons icn, int size) {
        return getImage(icn, size, size);
    }

    public static BufferedImage getImage(Icons icn, int w, int h) {
        return getImage(icn, w, h, null);
    }

    public static BufferedImage getImage(Icons icn, int w, int h, Color color) {
        if (icn == null) return null;
        try {
            // library offers MUCH better image scaling than ImageIO
            Thumbnails.Builder<URL> imageBuilder = Thumbnails.of(UiUtils.class.getResource("/images/" + icn.getName()));
            if (w > 0 && h > 0) {
                imageBuilder = imageBuilder.size(w, h);
            } else {
                // load full size image
                imageBuilder = imageBuilder.scale(1.0);
            }
            BufferedImage image = imageBuilder.asBufferedImage();
            if (image != null) {
                if (color != null) return replaceColor(image, color);
                else return image;
            }
            log.error("getImage: image not found! {}", icn);
        } catch (Exception e) {
            log.error("getImage: Exception: url:{}, {}", icn, e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // getImageIcon:
    // - ImageIcon scaled using Thumbnails library
    // ------------------------------------------------------------------------

    public static ImageIcon getImageIcon(Icons icn, int size) {
        return new ImageIcon(getImage(icn, size, size, null));
    }

    public static ImageIcon getImageIcon(Icons icn, int w, int h, Color color) {
        Image image = getImage(icn, w, h, color);
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

    /**
     * add LEFT click listener (will not respond to right-click)
     * - more responsive than a typical mouseClicked() listener
     */
    public static void addLeftClickListener(JComponent component, ClickListener listener) {
        component.addMouseListener(new MyMouseAdapter(listener, true));
    }

    /**
     * add RIGHT click listener (will not respond to left-click)
     * - more responsive than a typical mouseClicked() listener
     */
    public static void addRightClickListener(JComponent component, ClickListener listener) {
        component.addMouseListener(new MyMouseAdapter(listener, false));
    }

    /**
     * add click listener (right or left click)
     * - more responsive than a typical mouseClicked() listener
     */
    public static void addClickListener(JComponent component, ClickListener listener) {
        component.addMouseListener(new MyMouseAdapter(listener, null));
    }

    public static JMenuItem addPopupMenuItem(JPopupMenu popupMenu, String label, ActionListener listener) {
        return addPopupMenuItem(popupMenu, label, null, listener);
    }

    public static JMenuItem addPopupMenuItem(JPopupMenu popupMenu, String label, Icons iconEnum, ActionListener listener) {
        Icon icon = null;
        if (iconEnum != null) {
            icon = getImageIcon(iconEnum, UiUtils.IMG_SIZE_SMALL);
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

    public static JPanel createPanel(String label) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        Font font = panel.getFont().deriveFont(Font.BOLD, 14f);
        TitledBorder titledBorder = new TitledBorder(null, label, TitledBorder.LEADING, TitledBorder.DEFAULT_POSITION, font, null);
        panel.setBorder(titledBorder);
        return panel;
    }

    public static BufferedImage getTrayIconWithCount(int count) {
        int size = 16;
        BufferedImage baseImage = getImage(Icons.ANDROID, size, size, Color.WHITE);
        if (count == 0) return baseImage;

        // Create square image
        BufferedImage combined = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        // Enable anti-aliasing for smoother rendering
//        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
//        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw base icon with semi-transparency (40% opacity) so number is easier to read
//        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        g.drawImage(baseImage, 0, 0, null);

        // Reset to full opacity for text
//        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // Draw count with large bold font centered in the image
        String text = String.valueOf(count);
        Font font = new Font("Arial", Font.PLAIN, 8);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getAscent();

        // Center text in the image
        int textX = (size - textWidth) / 2;
        int textY = (size - textHeight) / 2 + textHeight;

        // Draw outline around text for better visibility
//        g.setColor(Color.BLACK);
//        g.setStroke(new BasicStroke(3.0f));
//        g.drawString(text, textX - 1, textY - 1);
//        g.drawString(text, textX + 1, textY - 1);
//        g.drawString(text, textX - 1, textY + 1);
//        g.drawString(text, textX + 1, textY + 1);

        // Draw text on top
        g.setColor(Color.BLACK);
        g.drawString(text, textX, textY);
        g.dispose();
        return combined;
    }

    public interface ButtonListener {
        void onClicked();
    }

    public static JButton addSettingButton(Container panel, String label, String action, ButtonListener listener) {
        JLabel jLabel = new JLabel(label);
        panel.add(jLabel, "growx");
        JButton button = new JButton(action);
        if (listener != null) {
            UiUtils.addLeftClickListener(jLabel, e -> {
                listener.onClicked();
            });
            UiUtils.addLeftClickListener(button, e -> {
                listener.onClicked();
            });
        }
        panel.add(button, "align right, wrap");
        return button;
    }

    public interface CheckBoxListener {
        void onChecked(boolean isChecked);
    }

    public static JCheckBox addSettingCheckbox(Container panel, String label, PreferenceUtils.PrefBoolean pref, boolean defaultValue, CheckBoxListener listener) {
        JLabel textLabel = new JLabel(label);
        panel.add(textLabel);

        JCheckBox checkbox = new JCheckBox();
        boolean currentChecked = PreferenceUtils.getPreference(pref, defaultValue);
        checkbox.setSelected(currentChecked);
        checkbox.setHorizontalTextPosition(SwingConstants.LEFT);
        panel.add(checkbox, "align center, wrap");

        checkbox.addActionListener(actionEvent -> {
            boolean selected = checkbox.isSelected();
            PreferenceUtils.setPreference(pref, selected);
            if (listener != null) listener.onChecked(selected);
        });

        UiUtils.addLeftClickListener(textLabel, e -> {
            // TODO: fire checkbox action listener directly
            boolean selected = !checkbox.isSelected();
            checkbox.setSelected(selected);
            PreferenceUtils.setPreference(pref, selected);
            if (listener != null) listener.onChecked(selected);
        });
        return checkbox;
    }

    public static boolean closeWindow(Component component) {
        Window window = SwingUtilities.getWindowAncestor(component);
        if (window != null) window.dispose();
        return window != null;
    }
}
