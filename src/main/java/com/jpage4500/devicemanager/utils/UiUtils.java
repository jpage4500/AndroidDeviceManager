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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
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

    // how much of the app's background image shows through: enough to see it, not enough to fight
    // with the content drawn on top of it
    private static final float BACKGROUND_ALPHA = 0.10f;

    /**
     * tile the app's background image, faded, over the given area
     * <p>
     * tiled at its own size rather than stretched to fit: the image is a repeating pattern, and
     * scaling it to the window would make it a different size in every window. does nothing if image
     * is null, so callers can pass a missing/disabled background straight through
     *
     * @param y top of the area to fill (eg: below a table header)
     */
    public static void drawBackgroundImage(Graphics graphics, BufferedImage image, int y, int width, int height) {
        if (image == null || width <= 0 || height <= 0) return;

        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            g2d.setComposite(AlphaComposite.SrcOver.derive(BACKGROUND_ALPHA));
            // anchored at the top of the area, so the first tile starts there instead of wherever the
            // window's own origin happens to fall
            g2d.setPaint(new TexturePaint(image, new Rectangle2D.Float(0, y, image.getWidth(), image.getHeight())));
            g2d.fillRect(0, y, width, height);
        } finally {
            g2d.dispose();
        }
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
        int size = 64;
        boolean isDark = OsThemeDetector.getInstance().isDark();
        Color iconColor = isDark ? Color.WHITE : Color.BLACK;
        Color textColor = isDark ? Color.BLACK : Color.WHITE;
        if (count == 0) return getImage(Icons.ANDROID, size, size, iconColor);

        BufferedImage combined = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int circleX = 6;
        int circleY = 10;
        int circleD = 52;
        int circleCenterX = circleX + circleD / 2;
        int circleCenterY = circleY + circleD / 2;

        // antennae first; circle then covers their inner ends so they appear to sprout from the head
        g.setColor(iconColor);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(19, 14, 15, 6);
        g.drawLine(45, 14, 49, 6);
        g.fillOval(circleX, circleY, circleD, circleD);

        String text = String.valueOf(count);
        int innerW = 36;
        int innerH = 36;
        int fontSize = 40;
        Font font;
        FontMetrics fm;
        while (true) {
            font = new Font("JetBrains Mono", Font.BOLD, fontSize);
            g.setFont(font);
            fm = g.getFontMetrics();
            if (fm.stringWidth(text) <= innerW && fm.getAscent() <= innerH) break;
            if (fontSize <= 10) break;
            fontSize -= 2;
        }

        int textX = circleCenterX - fm.stringWidth(text) / 2;
        int baselineY = circleCenterY + (fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(textColor);
        g.drawString(text, textX, baselineY);
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

    /**
     * section divider inside a settings panel
     */
    public static void addSettingHeader(Container panel, String label) {
        JLabel textLabel = new JLabel(label);
        textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD, 11f));
        textLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(textLabel, "span 2, gaptop 10, wrap");
        panel.add(new JSeparator(), "span 2, growx, gapbottom 4, wrap");
    }

    /**
     * folder picker row: label + browse button with the current folder shown underneath
     *
     * @param defaultFolder shown when nothing has been saved yet
     */
    public static void addSettingFolder(Container panel, String label, PreferenceUtils.Pref pref, String defaultFolder) {
        panel.add(new JLabel(label), "growx");

        JButton button = new JButton(getImageIcon(Icons.OPEN_FOLDER, IMG_SIZE_ICON));
        button.setToolTipText("Browse");
        panel.add(button, "align right, wrap");

        JLabel pathLabel = new JLabel();
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 11f));
        pathLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(pathLabel, "span 2, gapleft 4, gapbottom 4, wrap");

        Runnable refresh = () -> {
            String folder = PreferenceUtils.getPreference(pref);
            pathLabel.setText(TextUtils.isEmpty(folder) ? defaultFolder : folder);
        };
        refresh.run();

        button.addActionListener(e -> {
            String folder = chooseFolder(panel, pathLabel.getText(), label);
            if (folder == null) return;
            PreferenceUtils.setPreference(pref, folder);
            refresh.run();
        });
    }

    /**
     * @return selected folder, or null if cancelled
     */
    private static String chooseFolder(Component parent, String currentFolder, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(currentFolder));
        chooser.setDialogTitle(title);
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setApproveButtonText("OK");
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File selectedFile = chooser.getSelectedFile();
        if (selectedFile == null || !selectedFile.isDirectory()) return null;
        return selectedFile.getAbsolutePath();
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
