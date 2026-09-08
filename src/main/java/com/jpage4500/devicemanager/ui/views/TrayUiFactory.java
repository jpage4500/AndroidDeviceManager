package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.ui.swing.SwingUIFactory;
import dorkbox.systemTray.util.HeavyCheckMark;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.UIManager;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.PopupMenuUI;
import javax.swing.plaf.SeparatorUI;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * draws tray menu entries registered with {@link #setDevice} as device rows; everything else keeps the
 * look and feel's own menu item
 * <p>
 * only used where the tray menu is drawn by Swing (macOS/Windows); GTK/AppIndicator menus ignore it
 */
public class TrayUiFactory implements SwingUIFactory {
    /**
     * one action button on a device row
     */
    public record TrayAction(String tooltip, Icons icon, Consumer<Device> action) {
    }

    // dorkbox creates menu item peers on its own thread; weak keys drop entries once a rebuild replaces them
    private final Map<Entry, Device> deviceMap = Collections.synchronizedMap(new WeakHashMap<>());
    private final Consumer<Device> rowAction;
    private volatile List<TrayAction> actions = List.of();

    /**
     * @param rowAction run when a device row is clicked outside of its buttons
     */
    public TrayUiFactory(Consumer<Device> rowAction) {
        this.rowAction = rowAction;
    }

    /**
     * set the buttons every device row shows; must be set before entries are added to the menu
     */
    public void setActions(List<TrayAction> actions) {
        this.actions = List.copyOf(actions);
    }

    /**
     * draw this entry as a device row; must be called before the entry is added to the menu
     */
    public void setDevice(MenuItem entry, Device device) {
        deviceMap.put(entry, device);
    }

    @Override
    public MenuItemUI getItemUI(JMenuItem jMenuItem, Entry entry) {
        Device device = entry != null ? deviceMap.get(entry) : null;
        if (device != null) return new TrayDeviceMenuItemUI(device, actions, rowAction);
        return (MenuItemUI) UIManager.getUI(jMenuItem);
    }

    @Override
    public PopupMenuUI getMenuUI(JPopupMenu jPopupMenu, Menu entry) {
        return (PopupMenuUI) UIManager.getUI(jPopupMenu);
    }

    @Override
    public SeparatorUI getSeparatorUI(JSeparator jSeparator) {
        return (SeparatorUI) UIManager.getUI(jSeparator);
    }

    @Override
    public String getCheckMarkIcon(Color color, int checkMarkSize, int targetImageSize) {
        return HeavyCheckMark.get(color, checkMarkSize, targetImageSize);
    }
}
