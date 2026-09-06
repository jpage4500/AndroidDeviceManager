package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.utils.UiUtils;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * draws a device as a single tray menu row: icon, name and a button per action
 */
public class TrayDeviceMenuItemUI extends BasicMenuItemUI {
    private static final int PAD = 8;
    private static final int ICON_GAP = 8;
    private static final int TEXT_GAP = 20;
    private static final int BUTTON_SIZE = 24;
    private static final int BUTTON_ICON_SIZE = 16;
    private static final int BUTTON_ARC = 6;
    private static final int HOVER_ALPHA = 60;

    private static final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();

    private final Device device;
    private final List<TrayUiFactory.TrayAction> actions;
    private final Consumer<Device> rowAction;

    // set while painting, read when hit-testing clicks
    private final Rectangle[] buttonRects;
    private int hoverIndex = -1;
    // button the mouse went down on; read when the menu item fires
    private int pressedIndex = -1;
    private RowHandler handler;

    public TrayDeviceMenuItemUI(Device device, List<TrayUiFactory.TrayAction> actions, Consumer<Device> rowAction) {
        this.device = device;
        this.actions = actions;
        this.rowAction = rowAction;
        this.buttonRects = new Rectangle[actions.size()];
        for (int i = 0; i < buttonRects.length; i++) buttonRects[i] = new Rectangle();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        // the menu shows a truncated name; the tooltip shows all of it
        ((JMenuItem) c).setToolTipText(device.getDisplayName());
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        handler = new RowHandler();
        menuItem.addMouseListener(handler);
        menuItem.addMouseMotionListener(handler);
        menuItem.addActionListener(handler);
    }

    @Override
    protected void uninstallListeners() {
        super.uninstallListeners();
        if (handler != null) {
            menuItem.removeMouseListener(handler);
            menuItem.removeMouseMotionListener(handler);
            menuItem.removeActionListener(handler);
            handler = null;
        }
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
        JMenuItem item = (JMenuItem) c;
        FontMetrics fm = c.getFontMetrics(item.getFont());
        int w = PAD;
        int h = fm.getHeight();
        Icon icon = item.getIcon();
        if (icon != null) {
            w += icon.getIconWidth() + ICON_GAP;
            h = Math.max(h, icon.getIconHeight());
        }
        w += fm.stringWidth(text(item)) + TEXT_GAP + actions.size() * BUTTON_SIZE + PAD;
        return new Dimension(w, Math.max(h + 6, BUTTON_SIZE + 4));
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        JMenuItem item = (JMenuItem) c;
        int w = c.getWidth();
        int h = c.getHeight();
        boolean isSelected = item.isArmed() || item.isSelected();
        boolean isEnabled = item.isEnabled();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setColor(isSelected ? selectionBackground : item.getBackground());
        g2.fillRect(0, 0, w, h);

        Color foreground = !isEnabled ? disabledForeground : (isSelected ? selectionForeground : item.getForeground());

        int x = PAD;
        Icon icon = item.getIcon();
        if (icon != null) {
            icon.paintIcon(c, g2, x, (h - icon.getIconHeight()) / 2);
            x += icon.getIconWidth() + ICON_GAP;
        }

        // buttons are right-aligned; an offline device row has none
        int buttonsX = w - PAD;
        for (int i = actions.size() - 1; i >= 0; i--) {
            buttonsX -= BUTTON_SIZE;
            Rectangle rect = buttonRects[i];
            if (!isEnabled) {
                rect.setBounds(0, 0, 0, 0);
                continue;
            }
            rect.setBounds(buttonsX, (h - BUTTON_SIZE) / 2, BUTTON_SIZE, BUTTON_SIZE);
            paintButton(g2, c, i, rect, foreground, isSelected);
        }

        g2.setColor(foreground);
        g2.setFont(item.getFont());
        FontMetrics fm = g2.getFontMetrics();
        String text = clipText(fm, text(item), buttonsX - TEXT_GAP - x);
        g2.drawString(text, x, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    private void paintButton(Graphics2D g2, JComponent c, int index, Rectangle rect, Color foreground, boolean isSelected) {
        if (index == hoverIndex) {
            Color hover = isSelected ? selectionForeground : selectionBackground;
            g2.setColor(new Color(hover.getRed(), hover.getGreen(), hover.getBlue(), HOVER_ALPHA));
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, BUTTON_ARC, BUTTON_ARC);
        }
        Icon icon = getActionIcon(actions.get(index).icon(), foreground);
        if (icon == null) return;
        icon.paintIcon(c, g2, rect.x + (rect.width - icon.getIconWidth()) / 2, rect.y + (rect.height - icon.getIconHeight()) / 2);
    }

    private static Icon getActionIcon(Icons icn, Color color) {
        return iconCache.computeIfAbsent(icn.name() + ":" + color.getRGB(),
            key -> UiUtils.getImageIcon(icn, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE, color));
    }

    private static String text(JMenuItem item) {
        String text = item.getText();
        return text != null ? text : "";
    }

    private static String clipText(FontMetrics fm, String text, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int available = maxWidth - fm.stringWidth(ellipsis);
        for (int i = text.length() - 1; i > 0; i--) {
            if (fm.stringWidth(text.substring(0, i)) <= available) return text.substring(0, i) + ellipsis;
        }
        return ellipsis;
    }

    private int buttonAt(int x, int y) {
        if (!menuItem.isEnabled()) return -1;
        for (int i = 0; i < buttonRects.length; i++) {
            if (buttonRects[i].contains(x, y)) return i;
        }
        return -1;
    }

    private void setHoverIndex(int index) {
        if (index == hoverIndex) return;
        hoverIndex = index;
        menuItem.setToolTipText(index >= 0 ? actions.get(index).tooltip() : device.getDisplayName());
        menuItem.repaint();
    }

    /**
     * tracks which button the mouse is over; the menu item's own listener closes the menu and fires
     */
    private class RowHandler extends MouseAdapter implements ActionListener {
        @Override
        public void mouseEntered(MouseEvent e) {
            setHoverIndex(buttonAt(e.getX(), e.getY()));
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            setHoverIndex(buttonAt(e.getX(), e.getY()));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            setHoverIndex(-1);
            pressedIndex = -1;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            pressedIndex = buttonAt(e.getX(), e.getY());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int index = pressedIndex;
            pressedIndex = -1;
            setHoverIndex(-1);
            if (index >= 0) actions.get(index).action().accept(device);
            else if (rowAction != null) rowAction.accept(device);
        }
    }
}
