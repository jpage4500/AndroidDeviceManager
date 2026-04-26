package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.utils.PreferenceUtils;

import javax.swing.*;
import java.awt.*;
import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

/**
 * Centralized "Window" menu shared across screens (Close, Show Devices/Logs/Browse, Settings, Hide Toolbar).
 * Each screen still adds its own screen-specific menus alongside this one.
 */
public final class CommonMenu {
    private CommonMenu() {
    }

    /** Build the standard "Window" menu. {@code contextDevice} may be null. */
    public static JMenu buildWindowMenu(BaseScreen screen, App app, Device contextDevice) {
        JMenu menu = new JMenu("Window");

        addCmd(menu, "Close Window", KeyEvent.VK_W, e -> screen.closeWindow());

        if (!app.isHeadlessMode()) {
            addCmd(menu, App.SHOW_DEVICE_LIST, KeyEvent.VK_1, e -> app.showDeviceList());
        }

        addCmd(menu, App.SHOW_BROWSE, KeyEvent.VK_2, e -> app.showFileBrowser(contextDevice));
        addCmd(menu, App.SHOW_LOG_VIEWER, KeyEvent.VK_3, e -> app.showLogs(contextDevice));

        addCmd(menu, "Settings", KeyEvent.VK_COMMA, e -> SettingsDialog.showSettings(app, screen));

        addCmd(menu, "Hide Toolbar", KeyEvent.VK_T, e -> screen.toggleToolbar());

        // always on top toggle
        JCheckBoxMenuItem onTopItem = new JCheckBoxMenuItem();
        boolean isAlwaysOnTop = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_ALWAYS_ON_TOP, false);
        screen.setAlwaysOnTop(isAlwaysOnTop);
        onTopItem.setState(isAlwaysOnTop);
        onTopItem.setAction(new AbstractAction("Always on top") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                boolean alwaysOnTop = !screen.isAlwaysOnTop();
                screen.setAlwaysOnTop(alwaysOnTop);
                PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_ALWAYS_ON_TOP, alwaysOnTop);
            }
        });
        menu.add(onTopItem);

        return menu;
    }

    private static void addCmd(JMenu menu, String label, int keyCode, ActionListener listener) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, mask);
        Action action = new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(e);
            }
        };
        JMenuItem item = new JMenuItem(action);
        action.putValue(Action.ACCELERATOR_KEY, keyStroke);
        item.setAccelerator(keyStroke);
        menu.add(item);
    }
}
