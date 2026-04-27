package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.GithubRelease;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.ui.views.TrayMenuItem;
import com.jpage4500.devicemanager.utils.Colors;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.NetworkHelper;
import com.jpage4500.devicemanager.utils.OsThemeDetector;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import com.jpage4500.devicemanager.utils.Utils;

import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * App-lifecycle owner. Implements {@link App} for the screens to depend on, and
 * {@link DeviceManager.DeviceListener} as the single sink for device events.
 *
 * Responsibilities (moved out of DeviceScreen):
 *  - ADB server connect / retry, remote-server / remote-connection lifecycle
 *  - Update checking against GitHub releases
 *  - System tray icon
 *  - Open child-window cache for logs / explore / input / save-logs
 *  - Exit-to-tray handling and process exit
 */
public class AppController implements App, DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    public static final String UPDATE_SOURCE_GITHUB = "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases";
    public static final String URL_GITHUB = "https://github.com/jpage4500/AndroidDeviceManager/releases";

    // owner of the device-list screen (null in headless mode)
    private DeviceScreen deviceScreen;
    private boolean headlessMode;

    // open windows (per device)
    private final Map<String, ExploreScreen> exploreViewMap = new HashMap<>();
    private final Map<String, ViewLogsScreen> logsViewMap = new HashMap<>();
    private final Map<String, InputScreen> inputViewMap = new HashMap<>();
    private SaveLogsScreen saveLogsScreen;

    // system tray
    private TrayIcon trayIcon;
    private JPopupMenu trayPopupMenu;
    private int trayIconDevices = -1;

    // update checking
    private ScheduledExecutorService updateExecutorService;
    private String updateVersion;
    private String updateDesc;

    // file-drop queue (apk drag) — filled before deviceScreen exists
    private List<File> pendingFiles;

    private static volatile boolean hasExited = false;

    // logs-only mode bootstrap
    private boolean awaitingFirstDeviceList;
    private JDialog searchingSplash;

    /** install Desktop.QUIT_HANDLER / shutdown hooks once. Safe to call multiple times. */
    public void installLifecycleHooks() {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((quitEvent, quitResponse) -> {
                    log.trace("installLifecycleHooks: desktop:QUIT");
                    exit(true);
                    quitResponse.performQuit();
                });
                return;
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.trace("installLifecycleHooks: SHUTDOWN_HOOK");
            if (!hasExited) {
                DeviceManager.getInstance().handleExit();
            }
        }, "ShutdownHook"));
    }

    /** wire up DeviceManager: set listener, start ADB, init remote managers. */
    public void connectAdbServer() {
        DeviceManager deviceManager = DeviceManager.getInstance();
        deviceManager.setDeviceListener(this);
        deviceManager.connectAdbServer(true);
        deviceManager.getRemoteConnectionManager().initialize();
        deviceManager.getRemoteServerManager().initialize();
    }

    public void setDeviceScreen(DeviceScreen ds) {
        this.deviceScreen = ds;
        // any files that arrived before DeviceScreen existed
        if (ds != null && pendingFiles != null && !pendingFiles.isEmpty()) {
            ds.handleFilesOpened(pendingFiles);
            pendingFiles = null;
        }
    }

    public DeviceScreen getDeviceScreen() {
        return deviceScreen;
    }

    public void setHeadlessMode(boolean headless) {
        this.headlessMode = headless;
    }

    /**
     * Logs-only launch mode: skip DeviceScreen, wait for first online device, open ViewLogsScreen.
     * If multiple devices, prompts the user to pick one.
     */
    public void startLogsOnly() {
        headlessMode = true;
        awaitingFirstDeviceList = true;
        showSearchingSplash();
        // device list arrives via DeviceManager.DeviceListener -> handleDevicesUpdated
    }

    private void showSearchingSplash() {
        if (searchingSplash != null) return;

        JPanel labelPanel = new JPanel(new GridBagLayout());
        labelPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        labelPanel.add(new JLabel("No connected devices. Waiting…"));

        JButton cancel = new JButton(new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                exit(true);
            }
        });
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(cancel);

        searchingSplash = new JDialog((Frame) null, "Android Device Manager", false);
        searchingSplash.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        searchingSplash.getContentPane().setLayout(new BorderLayout());
        searchingSplash.getContentPane().add(labelPanel, BorderLayout.CENTER);
        searchingSplash.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        searchingSplash.pack();
        searchingSplash.setLocationRelativeTo(null);
        searchingSplash.setVisible(true);
    }

    private void dismissSearchingSplash() {
        if (searchingSplash != null) {
            searchingSplash.setVisible(false);
            searchingSplash.dispose();
            searchingSplash = null;
        }
    }

    /** Called from handleDevicesUpdated when waiting for a device in logs-only mode. */
    private void onFirstDeviceListAvailable(List<Device> devices) {
        if (devices == null) return;
        List<Device> online = new ArrayList<>();
        for (Device d : devices) if (d.isOnline) online.add(d);
        if (online.isEmpty()) return; // keep waiting

        awaitingFirstDeviceList = false;
        dismissSearchingSplash();

        if (online.size() == 1) {
            showLogs(online.get(0));
        } else {
            pickDevice(online, this::showLogs);
        }
    }

    /** Show a combobox picker for one of the given devices; invokes onPick (or exits in logs-only on cancel). */
    public void pickDevice(List<Device> devices, Consumer<Device> onPick) {
        JComboBox<Device> picker = new JComboBox<>(devices.toArray(new Device[0]));
        picker.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(list, value, i, s, f);
                if (value instanceof Device d) setText(d.getDisplayName());
                return this;
            }
        });
        boolean ok = DialogHelper.showCustomDialog(null, picker, "Select Device", null);
        Device chosen = ok ? (Device) picker.getSelectedItem() : null;
        if (chosen == null) {
            if (headlessMode) exit(true);
            return;
        }
        onPick.accept(chosen);
    }

    /**
     * Handle an adm://logs[/serial] URL. Picks a device (auto if 1, dialog if more) and opens logs.
     * Used by URL scheme handlers from any state.
     */
    public void openLogsViaUrl(String optionalSerial) {
        SwingUtilities.invokeLater(() -> {
            List<Device> all = DeviceManager.getInstance().getDevices();
            List<Device> online = new ArrayList<>();
            for (Device d : all) if (d.isOnline) online.add(d);

            if (TextUtils.notEmpty(optionalSerial)) {
                for (Device d : online) {
                    if (optionalSerial.equals(d.serial)) {
                        showLogs(d);
                        return;
                    }
                }
                // requested serial not online — fall through to picker
            }

            if (online.isEmpty()) {
                // mark waiting; the next handleDevicesUpdated will pick up
                awaitingFirstDeviceList = true;
                showSearchingSplash();
                return;
            }
            if (online.size() == 1) {
                showLogs(online.get(0));
            } else {
                pickDevice(online, this::showLogs);
            }
        });
    }

    @Override
    public boolean isHeadlessMode() {
        return headlessMode;
    }

    // ========================================================================
    // App: navigation
    // ========================================================================

    @Override
    public void showDeviceList() {
        if (deviceScreen == null) return;
        SwingUtilities.invokeLater(() -> {
            deviceScreen.setVisible(true);
            deviceScreen.toFront();
        });
    }

    @Override
    public void showLogs(Device device) {
        if (device == null && deviceScreen != null) device = deviceScreen.getFirstSelectedDevice();
        if (device == null) return;

        ViewLogsScreen logsScreen = logsViewMap.get(device.serial);
        if (logsScreen == null) {
            if (!device.isOnline) return;
            logsScreen = new ViewLogsScreen(this, device);
            logsViewMap.put(device.serial, logsScreen);
        }
        logsScreen.show();
    }

    @Override
    public void showFileBrowser(Device device) {
        log.trace("showFileBrowser: BEFORE: device {}", device);
        if (device == null && deviceScreen != null) device = deviceScreen.getFirstSelectedDevice();
        if (device == null) return;

        log.trace("showFileBrowser: device {}, isOnline:{}", device.getDisplayName(), device.isOnline);
        ExploreScreen exploreScreen = exploreViewMap.get(device.serial);
        if (exploreScreen == null) {
            if (!device.isOnline) return;
            exploreScreen = new ExploreScreen(this, device);
            exploreViewMap.put(device.serial, exploreScreen);
        }
        log.trace("showFileBrowser: show..");
        exploreScreen.show();
    }

    @Override
    public void showSaveLogs(List<Device> devices) {
        if (devices == null || devices.isEmpty()) return;
        if (saveLogsScreen == null) {
            saveLogsScreen = new SaveLogsScreen(this);
        }
        saveLogsScreen.setDeviceList(devices);
        saveLogsScreen.show();
    }

    @Override
    public void showInput(Device device) {
        if (device == null) return;

        InputScreen inputScreen = inputViewMap.get(device.serial);
        if (inputScreen == null) {
            if (!device.isOnline) return;
            inputScreen = new InputScreen(this, device);
            inputViewMap.put(device.serial, inputScreen);
        }
        inputScreen.show();
    }

    // ========================================================================
    // App: cleanup callbacks
    // ========================================================================

    @Override
    public void onLogsClosed(String serial) {
        logsViewMap.remove(serial);
        if (headlessMode && logsViewMap.isEmpty()) {
            // logs-only mode: closing the last logs window exits the app
            exit(true);
        }
    }

    @Override
    public void onBrowseClosed(String serial) {
        exploreViewMap.remove(serial);
    }

    @Override
    public void onInputClosed(String serial) {
        inputViewMap.remove(serial);
    }

    @Override
    public void onSaveLogsClosed() {
        saveLogsScreen = null;
    }

    // ========================================================================
    // App: device state
    // ========================================================================

    @Override
    public void setDeviceBusy(Device device, boolean isBusy) {
        device.setBusy(isBusy);
        if (deviceScreen == null) return;
        if (SwingUtilities.isEventDispatchThread()) {
            deviceScreen.model.updateDevice(device);
        } else {
            SwingUtilities.invokeLater(() -> deviceScreen.model.updateDevice(device));
        }
    }

    // ========================================================================
    // App: settings hooks
    // ========================================================================

    @Override
    public void scheduleUpdateChecks() {
        if (updateExecutorService != null) {
            updateExecutorService.shutdownNow();
            updateExecutorService = null;
        }
        boolean checkUpdates = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_CHECK_UPDATES, true);
        if (checkUpdates) {
            updateExecutorService = Executors.newSingleThreadScheduledExecutor();
            updateExecutorService.scheduleAtFixedRate(() -> checkForUpdates(null), 5, TimeUnit.HOURS.toSeconds(12), TimeUnit.SECONDS);
        }
    }

    @Override
    public void refreshDeviceListView() {
        if (deviceScreen != null) deviceScreen.model.fireTableDataChanged();
    }

    @Override
    public void rebuildDeviceTable() {
        if (deviceScreen == null) return;
        deviceScreen.setupTable();
        deviceScreen.handleDevicesUpdated(DeviceManager.getInstance().getDevices());
    }

    @Override
    public void restoreDeviceTable() {
        if (deviceScreen != null) deviceScreen.restoreTable();
    }

    @Override
    public void rebuildDeviceToolbar() {
        if (deviceScreen != null) deviceScreen.setupToolbar();
    }

    @Override
    public void notifyCustomColumnsChanged() {
        if (deviceScreen != null) deviceScreen.setCustomColumns();
        DeviceManager.getInstance().refreshDevices();
    }

    // ========================================================================
    // App: file-drop forwarding
    // ========================================================================

    @Override
    public void handleFilesOpened(List<File> files) {
        if (files == null || files.isEmpty()) return;
        if (deviceScreen != null) {
            deviceScreen.handleFilesOpened(files);
        } else {
            // queue until DeviceScreen exists; in headless mode files just sit here (no harm)
            if (pendingFiles == null) pendingFiles = new ArrayList<>();
            pendingFiles.addAll(files);
        }
    }

    // ========================================================================
    // App: exit
    // ========================================================================

    @Override
    public void exit(boolean forceQuit) {
        if (hasExited) {
            log.debug("exit: already executed (force:{})", forceQuit);
            return;
        }
        boolean exitToTray = !headlessMode && PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_EXIT_TO_TRAY);
        log.debug("exit: force:{} exitToTray:{}", forceQuit, exitToTray);
        if (deviceScreen != null) deviceScreen.setVisible(false);
        if (!forceQuit && exitToTray) {
            log.trace("exit: exit-to-tray preference active -> keeping process running");
            return;
        }

        hasExited = true;
        if (deviceScreen != null) {
            deviceScreen.saveFrameSize();
            deviceScreen.table.saveTable();
        }

        // save positions/sizes of any other open windows (only first of each map)
        if (!exploreViewMap.isEmpty())
            (exploreViewMap.values().iterator().next()).onWindowStateChanged(BaseScreen.WindowState.CLOSING);
        if (!logsViewMap.isEmpty())
            (logsViewMap.values().iterator().next()).onWindowStateChanged(BaseScreen.WindowState.CLOSING);
        if (!inputViewMap.isEmpty())
            (inputViewMap.values().iterator().next()).onWindowStateChanged(BaseScreen.WindowState.CLOSING);
        if (saveLogsScreen != null) saveLogsScreen.onWindowStateChanged(BaseScreen.WindowState.CLOSING);

        DeviceManager.getInstance().handleExit();

        if (updateExecutorService != null) {
            updateExecutorService.shutdown();
            updateExecutorService = null;
        }

        if (SystemTray.isSupported() && trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }

        try {
            AppLoggerFactory loggerFactory = (AppLoggerFactory) org.slf4j.LoggerFactory.getILoggerFactory();
            loggerFactory.shutdown();
        } catch (Exception ignored) {
        }

        if (deviceScreen != null) deviceScreen.dispose();

        System.exit(0);
    }

    // ========================================================================
    // DeviceManager.DeviceListener — single sink, fans out to deviceScreen + child windows
    // ========================================================================

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        SwingUtilities.invokeLater(() -> {
            if (deviceList == null) return;
            if (deviceScreen != null) deviceScreen.handleDevicesUpdated(deviceList);
            for (Device device : deviceList) updateChildWindows(device);
            setupSystemTray();
            updateTaskbarBadge();

            if (awaitingFirstDeviceList) onFirstDeviceListAvailable(deviceList);
        });
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleDeviceUpdated(device);
            updateChildWindows(device);

            // a device transitioning to online (or arriving fresh) may only fire here, not via
            // handleDevicesUpdated — re-check the splash gate so logs-only mode dismisses promptly
            if (awaitingFirstDeviceList && device.isOnline) {
                onFirstDeviceListAvailable(DeviceManager.getInstance().getDevices());
            }
        });
    }

    @Override
    public void handleDeviceRemoved(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleDeviceRemoved(device);
            updateChildWindows(device);
        });
    }

    @Override
    public void handleException(Exception e) {
        SwingUtilities.invokeLater(() -> {
            Component parent = deviceScreen;
            String[] choices = {"Retry", "Cancel"};
            if (!DialogHelper.showOptionDialog(parent, "ADB Server",
                "Unable to connect to ADB server. Please check that it's running and re-try", choices))
                return;
            connectAdbServer();
        });
    }

    private void updateChildWindows(Device device) {
        ExploreScreen exploreScreen = exploreViewMap.get(device.serial);
        if (exploreScreen != null) exploreScreen.updateDevice(device);

        ViewLogsScreen logsScreen = logsViewMap.get(device.serial);
        if (logsScreen != null) logsScreen.updateDevice(device);

        InputScreen inputScreen = inputViewMap.get(device.serial);
        if (inputScreen != null) inputScreen.updateDevice(device);

        if (saveLogsScreen != null) saveLogsScreen.updateDevice();
    }

    private void updateTaskbarBadge() {
        if (!Taskbar.isTaskbarSupported()) return;
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER)) return;
            int numOnline = 0;
            for (Device device : DeviceManager.getInstance().getDevices()) {
                if (device.isOnline) numOnline++;
            }
            String badge = numOnline > 0 ? String.valueOf(numOnline) : null;
            taskbar.setIconBadge(badge);
        } catch (Exception e) {
            log.error("updateTaskbarBadge: Exception: {}", e.getMessage());
        }
    }

    // ========================================================================
    // System tray
    // ========================================================================

    public void setupSystemTray() {
        if (headlessMode) return;
        if (!SystemTray.isSupported()) return;

        List<Device> devices = DeviceManager.getInstance().getDevices();
        if (devices.size() == trayIconDevices && trayIcon != null) return;

        trayIconDevices = devices.size();
        BufferedImage trayIconImage = getTrayIconWithCount(trayIconDevices);

        if (trayIcon == null) {
            trayIcon = new TrayIcon(trayIconImage, "Android Device Manager");
            trayIcon.setImageAutoSize(false);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (trayPopupMenu != null) {
                        trayPopupMenu.setVisible(false);
                        trayPopupMenu = null;
                    } else {
                        showSystemTray(e);
                    }
                }
            });
            try {
                SystemTray tray = SystemTray.getSystemTray();
                tray.add(trayIcon);
            } catch (Exception e) {
                log.error("setupSystemTray: Exception: {}", e.getMessage());
            }
            OsThemeDetector.getInstance().registerListener(isDark ->
                    SwingUtilities.invokeLater(() -> {
                        if (trayIcon != null) trayIcon.setImage(getTrayIconWithCount(trayIconDevices));
                    }));
        } else {
            trayIcon.setImage(trayIconImage);
        }
    }

    public void hideTrayPopup() {
        if (trayPopupMenu != null) trayPopupMenu.setVisible(false);
    }

    private BufferedImage getTrayIconWithCount(int count) {
        Color iconColor = OsThemeDetector.getInstance().isDark() ? Color.WHITE : Color.BLACK;
        BufferedImage baseImage = UiUtils.getImage("system_tray.png", 22, 22, iconColor);
        int w = baseImage.getWidth();
        int h = baseImage.getHeight();
        if (count == 0) return baseImage;

        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = tempImg.createGraphics();
        Font font = new Font("Arial", Font.PLAIN, 16);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        String text = String.valueOf(count);
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        g2.dispose();

        int combinedWidth = w + textWidth + 6;
        BufferedImage combined = new BufferedImage(combinedWidth, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();
        g.drawImage(baseImage, 0, 0, null);
        g.setFont(font);
        g.setColor(iconColor);
        int x = w + 6;
        int y = h / 2 + textHeight / 3;
        g.drawString(text, x, y);
        g.dispose();
        return combined;
    }

    private void showSystemTray(MouseEvent e) {
        trayPopupMenu = new JPopupMenu();
        trayPopupMenu.setLocation(e.getX(), e.getY());
        trayPopupMenu.setInvoker(trayPopupMenu);
        List<Device> devices = DeviceManager.getInstance().getDevices();
        if (devices.isEmpty()) {
            UiUtils.addPopupMenuItem(trayPopupMenu, "Open", "icon_open.png", actionEvent -> {
                trayPopupMenu.setVisible(false);
                bringMainWindowToFront();
            });
        } else {
            for (Device device : devices) {
                addTrayMenuItem(device);
            }
        }
        trayPopupMenu.addSeparator();
        UiUtils.addPopupMenuItem(trayPopupMenu, "Quit", "icon_close.png", actionEvent -> exit(true));

        trayPopupMenu.setVisible(true);
    }

    private void addTrayMenuItem(Device device) {
        BufferedImage image = UiUtils.getImage("device_status.png", 20, 20);
        if (device.isOnline) {
            image = UiUtils.replaceColor(image, new Color(24, 134, 0));
        }

        TrayMenuItem item = new TrayMenuItem(device.getDisplayName(), new ImageIcon(image));
        item.addButton("Browse", actionEvent -> {
            trayPopupMenu.setVisible(false);
            showFileBrowser(device);
        });
        item.addButton("Logs", actionEvent -> {
            trayPopupMenu.setVisible(false);
            showLogs(device);
        });
        item.addActionListener(e2 -> {
            trayPopupMenu.setVisible(false);
            bringMainWindowToFront();
        });
        trayPopupMenu.add(item);
    }

    private void bringMainWindowToFront() {
        if (deviceScreen == null) return;
        if (deviceScreen.isActive()) return;
        SwingUtilities.invokeLater(() -> {
            if (!deviceScreen.isVisible()) {
                deviceScreen.setVisible(true);
                deviceScreen.setState(JFrame.NORMAL);
                return;
            }
            deviceScreen.setState(JFrame.ICONIFIED);
            Utils.runDelayed(300, true, () -> {
                deviceScreen.setState(JFrame.NORMAL);
                Utils.runDelayed(300, true, () -> deviceScreen.setState(JFrame.NORMAL));
            });
        });
    }

    // ========================================================================
    // Update checking
    // ========================================================================

    public interface UpdateListener {
        void onUpdateCheckComplete(String version, String desc);
    }

    public void checkForUpdates(UpdateListener updateListener) {
        if (SwingUtilities.isEventDispatchThread()) {
            Utils.runBackground(() -> checkForUpdates(updateListener));
            return;
        }
        String version = null;
        String desc = null;
        NetworkHelper networkHelper = new NetworkHelper();
        NetworkHelper.HttpResponse response = networkHelper.getRequest(UPDATE_SOURCE_GITHUB);
        List<GithubRelease> releases = GsonHelper.stringToList(response.body, GithubRelease.class);
        if (!releases.isEmpty()) {
            GithubRelease latestRelease = releases.get(0);
            Utils.CompareResult compareResult = Utils.compareVersion(MainApplication.version, latestRelease.tagName);
            if (compareResult == Utils.CompareResult.VERSION_NEWER) {
                version = latestRelease.tagName;
                desc = latestRelease.body;
            }
        }

        String finalVersion = version;
        String finalDesc = desc;
        if (version != null) {
            log.debug("checkForUpdates: LATEST:{}, CURRENT:{}", version, MainApplication.version);
            SwingUtilities.invokeLater(() -> {
                updateVersion = finalVersion;
                updateDesc = finalDesc;
                if (deviceScreen != null) deviceScreen.notifyUpdateAvailable(updateVersion, updateDesc);
                if (updateListener != null) updateListener.onUpdateCheckComplete(finalVersion, finalDesc);
            });
        } else if (updateListener != null) {
            SwingUtilities.invokeLater(() -> updateListener.onUpdateCheckComplete(null, null));
        }
    }

    /** triggered from the device-list status-bar update label */
    public void handleUpdateClicked(Component parent) {
        if (updateVersion == null) {
            if (!DialogHelper.showConfirmDialog(parent, "Update Check", "Check for update?")) return;
            checkForUpdates((version, desc) -> {
                if (updateVersion != null) {
                    handleUpdateClicked(parent);
                } else {
                    DialogHelper.showDialog(parent, null, "No Updates");
                }
            });
            return;
        }

        // Jdeploy will auto-update app on start
        String jdeployPath = System.getProperty("jdeploy.launcher.path");
        boolean isJdeploy = jdeployPath != null;
        int index = TextUtils.indexOf(jdeployPath, "/Contents/MacOS/Client4JLauncher");
        if (index > 0) {
            jdeployPath = jdeployPath.substring(0, index);
        }

        JPanel panel = new JPanel(new MigLayout());
        panel.add(new JLabel(String.format("Update %s Available", updateVersion)), "wrap");
        JTextArea textArea = new JTextArea(updateDesc);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, "newline 20px, wrap");
        String yesOption;
        if (isJdeploy) {
            panel.add(new JLabel("Restart App?"), "newline 20px, wrap");
            yesOption = "Restart";
        } else {
            panel.add(new JLabel("View release in browser?"), "newline 20px, wrap");
            yesOption = "View";
        }
        String[] choices = {yesOption, "Cancel"};
        if (!DialogHelper.showCustomDialog(parent, panel, "Update Available", choices)) return;

        if (isJdeploy) {
            final ArrayList<String> command = new ArrayList<>();
            command.add("open");
            command.add(jdeployPath);
            try {
                new ProcessBuilder(command).start();
                System.exit(0);
            } catch (IOException ex) {
                log.error("handleUpdateClicked: IOException: {}", ex.getMessage());
            }
        } else {
            Utils.openBrowser(URL_GITHUB);
        }
    }
}
