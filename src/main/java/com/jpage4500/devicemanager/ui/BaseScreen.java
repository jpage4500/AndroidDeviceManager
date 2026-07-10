package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.utils.ClickListener;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public abstract class BaseScreen extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(BaseScreen.class);

    private String prefKey;
    private String titleBackup;
    private Timer resizeTitleTimer;
    protected final App app;
    /**
     * device this screen is bound to (null for non-device-specific screens like DeviceScreen, SaveLogsScreen)
     */
    protected Device device;

    public BaseScreen(App app, Device device, String prefKey, int defaultWidth, int defaultHeight) {
        this.app = app;
        this.device = device;
        this.prefKey = prefKey;
        restoreFrameSize(defaultWidth, defaultHeight);

        // by default do nothing on exit - each screen needs to handle onWindowStateChanged(CLOSING)
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                onWindowStateChanged(WindowState.ACTIVATED);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                onWindowStateChanged(WindowState.DEACTIVATED);
            }

            @Override
            public void windowOpened(WindowEvent e) {
                onWindowStateChanged(WindowState.OPENED);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                onWindowStateChanged(WindowState.CLOSING);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                onWindowStateChanged(WindowState.CLOSED);
            }
        });

        // handle window resizing by changing title to "WxH"
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                handleResize();
            }
        });

        // NOTE: this breaks dragging the scrollbar on Mac
        // getRootPane().putClientProperty("apple.awt.draggableWindowBackground", true);

        // TODO: not sure if this is used.. I don't see it on Mac or Linux
        //if (!Utils.isMac()) {
        //    BufferedImage image = UiUtils.getImage("system_tray.png", 100, 100);
        //    setIconImage(image);
        //}

    }

    public enum WindowState {
        ACTIVATED,      // visible
        DEACTIVATED,    // background
        OPENED,
        CLOSING,        // closing (user closed window)
        CLOSED          // closed (NOTE: not sent if HIDE_ON_CLOSE is used)
    }

    protected void onWindowStateChanged(WindowState state) {
        //log.trace("onWindowStateChanged: {}: {}", prefKey, state);
    }

    /**
     * Handle window resize - show dimensions in title temporarily
     */
    private void handleResize() {
        // backup original title on first resize
        if (titleBackup == null) {
            titleBackup = getTitle();
        }

        // show current dimensions in title
        int width = getWidth();
        int height = getHeight();
        super.setTitle(width + "x" + height);

        // reset or start timer to restore original title after 1 second
        if (resizeTitleTimer != null) {
            resizeTitleTimer.restart();
        } else {
            resizeTitleTimer = new Timer(1000, e -> restoreTitle());
            resizeTitleTimer.setRepeats(false);
            resizeTitleTimer.start();
        }
    }

    @Override
    public void setTitle(String title) {
        // while a resize is in flight we're showing "WxH" in the title bar; redirect external
        // title updates to titleBackup so restoreTitle() picks up the latest value instead of
        // restoring a stale one (e.g. headless logs window resolving its device asynchronously)
        if (resizeTitleTimer != null && resizeTitleTimer.isRunning()) {
            titleBackup = title;
            return;
        }
        super.setTitle(title);
    }

    /**
     * close this window (each screen handles save/cleanup before disposing)
     */
    public abstract void closeWindow();

    /**
     * show/hide this screen's main toolbar — default no-op for screens without one
     */
    public void toggleToolbar() {
    }

    /**
     * Build the standard "Window" menu (Close, Show Devices/Logs/Browse, Settings, Hide Toolbar,
     * Always-on-top). Each screen calls this from its menu setup and adds its own screen-specific
     * menus alongside.
     * <p>
     * Show Logs / Show File Browser look up the device fresh via {@link DeviceManager#getDevice}
     * by serial when clicked, so the menu always targets the current device state. For screens
     * without a single device context (DeviceScreen, SaveLogsScreen) the lookup yields null and
     * the App impl falls back to "first selected device" or no-op.
     */
    protected JMenu buildWindowMenu() {
        JMenu menu = new JMenu("Window");

        createCmdMenuItem(menu, "Close Window", KeyEvent.VK_W, e -> closeWindow());

        if (!app.isHeadlessMode()) {
            // not available in headless mode
            createCmdMenuItem(menu, "Show Device List", KeyEvent.VK_1, e -> app.showDeviceList());
        }

        createCmdMenuItem(menu, "Show File Browser", KeyEvent.VK_2, e -> app.showFileBrowser(device));
        createCmdMenuItem(menu, "Show Device Logs", KeyEvent.VK_3, e -> app.showLogs(device));
        createCmdMenuItem(menu, "Settings", KeyEvent.VK_COMMA, e -> SettingsDialog.showSettings(app, this));
        createCmdMenuItem(menu, "Hide Toolbar", KeyEvent.VK_T, e -> toggleToolbar());

        // always on top toggle
        JCheckBoxMenuItem onTopItem = new JCheckBoxMenuItem();
        boolean isAlwaysOnTop = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_ALWAYS_ON_TOP, false);
        setAlwaysOnTop(isAlwaysOnTop);
        onTopItem.setState(isAlwaysOnTop);
        onTopItem.setAction(new AbstractAction("Always on top") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                boolean alwaysOnTop = !isAlwaysOnTop();
                setAlwaysOnTop(alwaysOnTop);
                PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_ALWAYS_ON_TOP, alwaysOnTop);
            }
        });
        menu.add(onTopItem);

        return menu;
    }

    /**
     * Restore original title after resize completes
     */
    private void restoreTitle() {
        if (resizeTitleTimer != null) {
            resizeTitleTimer.stop();
            resizeTitleTimer = null;
        }
        if (titleBackup != null) {
            super.setTitle(titleBackup);
            titleBackup = null;
        }
    }

    // Icons enum overloads
    protected JButton createSmallToolbarButton(JToolBar toolbar, Icons icn, String label, String tooltip, ClickListener listener) {
        return createToolbarButton(toolbar, icn, label, tooltip, UiUtils.IMG_SIZE_TOOLBAR_SMALL, listener);
    }

    protected JButton createToolbarButton(JToolBar toolbar, Icons icn, String label, String tooltip, ClickListener listener) {
        return createToolbarButton(toolbar, icn, label, tooltip, UiUtils.IMG_SIZE_TOOLBAR, listener);
    }

    protected JButton createToolbarButton(JToolBar toolbar, Icons icn, String label, String tooltip, int size, ClickListener listener) {
        JButton button = new JButton(label);
        if (icn != null) {
            BufferedImage image = UiUtils.getImage(icn, size, size);
            if (image == null) {
                // fall back to default image
                image = UiUtils.getImage(Icons.ANDROID, size, size);
            }
            button.setIcon(new ImageIcon(image));

            BufferedImage hoverImage = UiUtils.replaceColor(image, Colors.COLOR_TOOLBAR_HOVER);
            button.setRolloverIcon(new ImageIcon(hoverImage));
        }

        button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        if (tooltip != null) button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        UiUtils.addLeftClickListener(button, listener);
        //button.addActionListener(listener);
        toolbar.add(button);
        return button;
    }

    public interface CustomActionListener {
        void actionPerformed(ActionEvent e);
    }

    /**
     * create shortcut key using CMD key
     */
    protected JMenuItem createCmdMenuItem(JMenu menu, String label, int key, CustomActionListener listener) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke keyStroke = KeyStroke.getKeyStroke(key, mask);
        return createMenuItem(menu, label, keyStroke, listener);
    }

    /**
     * create JMenuItem with label and can be actived using KeyStroke
     *
     * @param keyStroke - optional keystroke used to active menu item
     * @param listener  - listener when menu item is selected
     */
    protected JMenuItem createMenuItem(JMenu menu, String label, KeyStroke keyStroke, CustomActionListener listener) {
        Action action = new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(e);
            }
        };

        JMenuItem item = UiUtils.addMenuItem(menu, label, action);
        if (keyStroke != null) {
            action.putValue(Action.ACCELERATOR_KEY, keyStroke);
            item.setAccelerator(keyStroke);
        }
        return item;
    }

    /**
     * save current frame size
     */
    protected void saveFrameSize() {
        Preferences prefs = Preferences.userRoot();
        Rectangle rect = getBounds();
        //log.trace("saveFrameSize: {}, w:{}, h:{}", prefKey, rect.width, rect.height);
        prefs.put(prefKey, GsonHelper.toJson(rect));
    }

    /**
     * restore frame size
     */
    private void restoreFrameSize(int defaultWidth, int defaultHeight) {
        Preferences prefs = Preferences.userRoot();
        String savedFrameSize = prefs.get(prefKey, null);
        Rectangle r = GsonHelper.fromJson(savedFrameSize, Rectangle.class);
        if (r == null) {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (screenSize.width - defaultWidth) / 2;
            int y = (screenSize.height - defaultHeight) / 2;
            r = new Rectangle(x, y, defaultWidth, defaultHeight);
        }
        setLocation(r.x, r.y);
        setSize(r.width, r.height);
    }

}
