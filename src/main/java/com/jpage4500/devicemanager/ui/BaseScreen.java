package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.*;

/**
 * create and manage device view
 */
public class BaseScreen extends JFrame implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(BaseScreen.class);

    public static final String SHOW_DEVICE_LIST = "Show Device List";
    public static final String SHOW_BROWSE = "Show File Browser";
    public static final String SHOW_LOG_VIEWER = "Show Device Logs";

    protected MainApplication mainApplication;
    private String prefKey;

    public BaseScreen(MainApplication mainApplication, String prefKey, int defaultWidth, int defaultHeight) {
        this.mainApplication = mainApplication;
        this.prefKey = prefKey;
        restoreFrameSize(defaultWidth, defaultHeight);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

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

        // TODO: handle window resizing
        //addComponentListener(new ComponentAdapter() {
        //    @Override
        //    public void componentResized(ComponentEvent componentEvent) {
        //        log.trace("componentResized: {}: W:{}, H:{}", prefKey, getWidth(), getHeight());
        //    }
        //});

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

    protected JButton createSmallToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        return createToolbarButton(toolbar, imageName, label, tooltip, UiUtils.IMG_SIZE_TOOLBAR_SMALL, listener);
    }

    /**
     * create a 'standard' toolbar button with 40x40 image and label below
     */
    protected JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        return createToolbarButton(toolbar, imageName, label, tooltip, UiUtils.IMG_SIZE_TOOLBAR, listener);
    }

    protected JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, int size, ActionListener listener) {
        JButton button = new JButton(label);
        if (imageName != null) {
            ImageIcon icon = UiUtils.getImageIcon(imageName, size, size);
            //image = replaceColor(image, new Color(0, 38, 255, 184));
            button.setIcon(icon);
        }

        button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        if (tooltip != null) button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.addActionListener(listener);
        toolbar.add(button);
        return button;
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdMenuItem(windowMenu, "Close Window", KeyEvent.VK_W, e -> exitApp(false));

        // [CMD + 2] = show explorer
        createCmdMenuItem(windowMenu, SHOW_BROWSE, KeyEvent.VK_2, e -> handleBrowseCommand(null));

        // [CMD + 3] = show logs
        createCmdMenuItem(windowMenu, SHOW_LOG_VIEWER, KeyEvent.VK_3, e -> handleViewLogsCommand(null));

        // [CMD + ,] = settings
        createCmdMenuItem(windowMenu, "Settings", KeyEvent.VK_COMMA, e -> SettingsDialog.showSettings(this));

        // [CMD + T] = hide toolbar
        createCmdMenuItem(windowMenu, "Hide Toolbar", KeyEvent.VK_T, e -> hideToolbar());

        // always on top
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
        windowMenu.add(onTopItem);

        JMenu deviceMenu = new JMenu("Devices");

        // [CMD + F] = focus search box
        createCmdMenuItem(deviceMenu, "Filter", KeyEvent.VK_F, e -> filterTextField.requestFocus());

        // [CMD + N] = connect device
        createCmdMenuItem(deviceMenu, "Connect Device", KeyEvent.VK_N, e -> handleConnectDevice());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(deviceMenu);
        setJMenuBar(menubar);
    }

    /**
     * exit app or just hide screen if user has 'exit to tray' setting enabled
     *
     * @param forceQuit true to exit regardless of setting
     */
    private void exitApp(boolean forceQuit) {
        setVisible(false);
        if (!forceQuit && PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_EXIT_TO_TRAY)) {
            return;
        }

        saveFrameSize();
        table.saveTable();

        // save positions/sizes of any other open windows
        // NOTE: only saving FIRST open window position
        if (!exploreViewMap.isEmpty())
            (exploreViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSED);
        if (!logsViewMap.isEmpty())
            (logsViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSED);
        if (!inputViewMap.isEmpty())
            (inputViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSED);
        if (saveLogsScreen != null) saveLogsScreen.onWindowStateChanged(WindowState.CLOSED);

        DeviceManager.getInstance().handleExit();

        if (SystemTray.isSupported() && trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }

        dispose();
        System.exit(0);
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
        log.trace("saveFrameSize: {}, w:{}, h:{}", prefKey, rect.width, rect.height);
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

    public void handleFilesOpened(List<File> openFileList) {
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
    }

    @Override
    public void handleDeviceRemoved(Device device) {
    }

    @Override
    public void handleException(Exception e) {
    }

}
