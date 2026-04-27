package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.GithubRelease;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
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

    // logs-only mode: single ViewLogsScreen with embedded device picker, not in logsViewMap
    private ViewLogsScreen headlessLogsScreen;

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
        deviceManager.initialize(this);
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
     * Logs-only launch mode: skip DeviceScreen and open the ViewLogsScreen immediately
     * with an embedded "Connected Devices" picker. Devices populate as they're discovered.
     */
    public void startLogsOnly() {
        headlessMode = true;
        headlessLogsScreen = new ViewLogsScreen(this);
        headlessLogsScreen.setConnectedDevices(DeviceManager.getInstance().getDevices());
    }

    /**
     * Handle an adm://logs[/serial] URL. Opens logs for the requested device (or first online).
     * Silent no-op if no devices are online.
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
            }

            if (online.isEmpty()) {
                log.warn("openLogsViaUrl: no online devices");
                return;
            }
            showLogs(online.get(0));
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
        if (headlessLogsScreen != null
                && (serial == null || serial.equals(headlessLogsScreen.getCurrentSerial()))) {
            headlessLogsScreen = null;
        } else {
            logsViewMap.remove(serial);
        }
        if (headlessMode && headlessLogsScreen == null && logsViewMap.isEmpty()) {
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
        DeviceManager.getInstance().refreshDevices(true);
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

        // save positions/sizes of any other open windows
        // (snapshot values first - closeWindow callbacks mutate the maps)
        for (ExploreScreen screen : new ArrayList<>(exploreViewMap.values())) {
            screen.onWindowStateChanged(BaseScreen.WindowState.CLOSING);
        }
        for (ViewLogsScreen screen : new ArrayList<>(logsViewMap.values())) {
            screen.onWindowStateChanged(BaseScreen.WindowState.CLOSING);
        }
        if (headlessLogsScreen != null)
            headlessLogsScreen.onWindowStateChanged(BaseScreen.WindowState.CLOSING);
        for (InputScreen screen : new ArrayList<>(inputViewMap.values())) {
            screen.onWindowStateChanged(BaseScreen.WindowState.CLOSING);
        }
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
            if (headlessLogsScreen != null) headlessLogsScreen.setConnectedDevices(deviceList);
        });
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleDeviceUpdated(device);
            updateChildWindows(device);
            if (headlessLogsScreen != null) {
                headlessLogsScreen.setConnectedDevices(DeviceManager.getInstance().getDevices());
            }
        });
    }

    @Override
    public void handleDeviceRemoved(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleDeviceRemoved(device);
            updateChildWindows(device);
            if (headlessLogsScreen != null) {
                headlessLogsScreen.setConnectedDevices(DeviceManager.getInstance().getDevices());
            }
        });
    }

    @Override
    public void handleException(Exception e) {
        SwingUtilities.invokeLater(() -> {
            Component parent = deviceScreen;
            String[] choices = {"Retry", "Cancel"};
            // showOptionDialog returns index of selected choice (0 = Retry, 1 = Cancel, -1 = closed)
            if (DialogHelper.showOptionDialog(parent, "ADB Server",
                "Unable to connect to ADB server. Please check that it's running and re-try", choices) != 0)
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

        if (headlessLogsScreen != null && headlessLogsScreen.isShowingDevice(device)) {
            headlessLogsScreen.updateDevice(device);
        }

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

    // TODO(merge): system-tray feature disabled — feature branch's TrayMenuItem
    //   class no longer exists. Re-add system tray once a replacement renderer
    //   (or revived TrayMenuItem) is available.
    public void setupSystemTray() {
        // no-op until system tray is re-implemented
    }

    public void hideTrayPopup() {
        if (trayPopupMenu != null) trayPopupMenu.setVisible(false);
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
        NetworkHelper.HttpResponse response = NetworkHelper.getRequest(UPDATE_SOURCE_GITHUB);
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
        if (DialogHelper.showCustomDialog(parent, panel, "Update Available", choices) != JOptionPane.YES_OPTION) return;

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
