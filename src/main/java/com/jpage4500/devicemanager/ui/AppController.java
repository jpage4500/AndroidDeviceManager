package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.GithubRelease;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.data.StatusEvent;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.client.RemoteConnection;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.NetworkHelper;
import com.jpage4500.devicemanager.utils.OsThemeDetector;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import com.jpage4500.devicemanager.utils.Utils;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * App-lifecycle owner. Implements {@link App} for the screens to depend on, and
 * {@link DeviceManager.DeviceListener} as the single sink for device events.
 *
 * Responsibilities (moved out of DeviceScreen):
 *  - ADB server connect / retry, remote-server / remote-connection lifecycle
 *  - Update checking against GitHub releases
 *  - System tray icon
 *  - Open child-window registry (one window per screen type per device)
 *  - Switching between logs-only mode and normal mode
 *  - Exit-to-tray handling and process exit
 */
public class AppController implements App, DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(AppController.class);

    public static final String UPDATE_SOURCE_GITHUB = "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases";
    public static final String URL_GITHUB = "https://github.com/jpage4500/AndroidDeviceManager/releases";

    // owner of the device-list screen (null in headless mode)
    private DeviceScreen deviceScreen;
    private boolean headlessMode;

    // every open child window, keyed by windowKey() - one entry per (screen type, device).
    // LinkedHashMap so exit() saves them in the order they were opened.
    private final Map<String, BaseScreen> windowMap = new LinkedHashMap<>();

    // system tray (dorkbox)
    private SystemTray systemTray;
    private String systemTrayHashCode;

    // update checking
    private ScheduledExecutorService updateExecutorService;
    private String updateVersion;
    private String updateDesc;

    // file-drop queue (apk drag) — filled before deviceScreen exists
    private List<File> pendingFiles;

    private static volatile boolean hasExited = false;

    // logs-only mode: single ViewLogsScreen with embedded device picker, not in logsViewMap
    private ViewLogsScreen headlessLogsScreen;

    // adm://logs URL request that arrived before any online device was discovered;
    // fired from the device-listener callbacks once a matching device appears
    private volatile boolean hasPendingLogsRequest;
    private volatile String pendingLogsSerial;

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
        // initialize() registers listener, calls connectAdbServer(true), and constructs
        // both remote managers (whose constructors call their own initialize()).
        DeviceManager.getInstance().initialize(this);
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
        openLogsModeWindow();
    }

    /**
     * switch between logs-only mode and normal mode - toolbar buttons in both screens toggle it
     * <p>
     * the device list is hidden and re-shown; the logs window is recreated (its layout differs by mode)
     */
    @Override
    public void setLogsMode(boolean logsMode) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setLogsMode(logsMode));
            return;
        }
        if (logsMode == headlessMode) return;
        log.debug("setLogsMode: {}", logsMode);
        PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_LOGS_MODE, logsMode);

        if (logsMode) enterLogsMode();
        else exitLogsMode();

        // tray menu differs between modes (no Mirror in logs mode)
        systemTrayHashCode = null;
        setupSystemTray();
    }

    private void enterLogsMode() {
        if (deviceScreen != null) deviceScreen.setVisible(false);
        // a per-device logs window would block the logs-mode window's close-to-exit check
        for (BaseScreen screen : new ArrayList<>(windowMap.values())) {
            if (screen instanceof ViewLogsScreen) screen.closeWindow();
        }
        // NOTE: set before creating the window - ViewLogsScreen reads it to build its layout
        headlessMode = true;
        openLogsModeWindow();
    }

    private void exitLogsMode() {
        // NOTE: clear before closing the window - onWindowClosed exits the app in logs mode
        headlessMode = false;
        ViewLogsScreen logsScreen = headlessLogsScreen;
        headlessLogsScreen = null;
        // closeWindow saves filters/divider/size and stops logging
        if (logsScreen != null) logsScreen.closeWindow();

        if (deviceScreen == null) {
            // NOTE: setDeviceScreen also installs any apk drops that arrived while in logs mode
            setDeviceScreen(new DeviceScreen(this));
            scheduleUpdateChecks();
        }
        // no device event may fire for minutes; populate from the list we already have
        deviceScreen.handleDevicesUpdated(DeviceManager.getInstance().getDevices());
        showDeviceList();
    }

    /** create the single logs-mode window; {@link #headlessMode} must already be set */
    private void openLogsModeWindow() {
        headlessLogsScreen = new ViewLogsScreen(this, null);
        headlessLogsScreen.setConnectedDevices(DeviceManager.getInstance().getDevices());
    }

    /**
     * Handle an adm://logs[/serial] URL. Opens logs for the requested device (or first online).
     * If no matching device is online yet (URL events typically arrive before ADB discovery
     * completes), the request is queued and fired from the device-listener callbacks.
     */
    public void openLogsViaUrl(String optionalSerial) {
        SwingUtilities.invokeLater(() -> {
            if (tryOpenLogsForSerial(optionalSerial)) return;
            hasPendingLogsRequest = true;
            pendingLogsSerial = optionalSerial;
            log.debug("openLogsViaUrl: queued, no matching online device yet (serial={})", optionalSerial);
        });
    }

    /** @return true if an online device matched and the logs window was opened */
    private boolean tryOpenLogsForSerial(String optionalSerial) {
        List<Device> online = new ArrayList<>();
        for (Device d : DeviceManager.getInstance().getDevices()) if (d.isOnline) online.add(d);
        if (online.isEmpty()) return false;
        if (TextUtils.notEmpty(optionalSerial)) {
            for (Device d : online) {
                if (optionalSerial.equals(d.serial)) {
                    showLogs(d);
                    return true;
                }
            }
            return false;
        }
        showLogs(online.get(0));
        return true;
    }

    private void drainPendingLogsRequest() {
        if (!hasPendingLogsRequest) return;
        if (tryOpenLogsForSerial(pendingLogsSerial)) {
            hasPendingLogsRequest = false;
            pendingLogsSerial = null;
        }
    }

    @Override
    public boolean isHeadlessMode() {
        return headlessMode;
    }

    // ========================================================================
    // App: navigation
    //
    // every child window lives in windowMap, so one instance per (screen type, device) is reused
    // and updateChildWindows()/exit() can treat them all the same.
    // ========================================================================

    /**
     * registry key: one window per screen type per device (blank serial for the app-wide screens
     * that aren't bound to a single device)
     */
    private static String windowKey(Class<? extends BaseScreen> type, Device device) {
        return type.getSimpleName() + ":" + (device != null ? device.serial : "");
    }

    /**
     * the open window of the given type for this device, creating it on first use
     *
     * @param onlineOnly the window can't be created for an offline device (it needs to talk to it).
     *                   NOTE: an already-open window is still returned - it shows the OFFLINE state
     *                   rather than vanishing when the device goes away
     * @return null only when the window doesn't exist and can't be created
     */
    private <T extends BaseScreen> T openWindow(Class<T> type, Device device, boolean onlineOnly, Function<Device, T> factory) {
        String key = windowKey(type, device);
        T screen = type.cast(windowMap.get(key));
        if (screen == null) {
            if (onlineOnly && device != null && !device.isOnline) return null;
            screen = factory.apply(device);
            windowMap.put(key, screen);
        } else if (device != null) {
            // re-showing an existing window: pick up whatever changed since it was last displayed
            screen.updateDevice(device);
        }
        return screen;
    }

    /**
     * show the per-device window of the given type
     * <p>
     * NOTE: a null device means "whatever is selected in the device list" - toolbar buttons and the
     * Window menu items in every screen pass null
     */
    private <T extends BaseScreen> void showDeviceWindow(Class<T> type, Device device, boolean onlineOnly, Function<Device, T> factory) {
        if (device == null && deviceScreen != null) device = deviceScreen.getFirstSelectedDevice();
        if (device == null) return;
        T screen = openWindow(type, device, onlineOnly, factory);
        if (screen == null) {
            log.debug("showDeviceWindow: {} is offline, not opening {}", device.getDisplayName(), type.getSimpleName());
            return;
        }
        screen.show();
    }

    @Override
    public void showDeviceList() {
        // the device list doesn't exist in logs mode; switching modes creates it
        if (headlessMode) {
            setLogsMode(false);
            return;
        }
        if (deviceScreen == null) return;
        SwingUtilities.invokeLater(() -> {
            deviceScreen.setVisible(true);
            deviceScreen.toFront();
        });
    }

    @Override
    public void showLogs(Device device) {
        // headless/logs-only mode uses a single ViewLogsScreen with an embedded device picker;
        // route to it instead of creating a per-serial instance
        if (headlessLogsScreen != null) {
            if (device == null || !device.isOnline) return;
            headlessLogsScreen.selectDevice(device);
            headlessLogsScreen.show();
            return;
        }
        showDeviceWindow(ViewLogsScreen.class, device, true, d -> new ViewLogsScreen(this, d));
    }

    @Override
    public void showFileBrowser(Device device) {
        showDeviceWindow(ExploreScreen.class, device, true, d -> new ExploreScreen(this, d));
    }

    @Override
    public void showInput(Device device) {
        showDeviceWindow(InputScreen.class, device, true, d -> new InputScreen(this, d));
    }

    @Override
    public void showDeviceInfo(Device device) {
        // NOTE: no online check - the last known details are still worth reading for a device that
        // just went away (and it's how you find out which device that was)
        showDeviceWindow(DeviceInfoScreen.class, device, false, d -> new DeviceInfoScreen(this, d));
    }

    @Override
    public void showBattery(Device device) {
        showDeviceWindow(BatteryScreen.class, device, true, d -> new BatteryScreen(this, d));
    }

    @Override
    public void showStats() {
        // NOTE: not showDeviceWindow - this one isn't bound to a device and has to open with nothing
        // selected in the device list
        StatsScreen screen = openWindow(StatsScreen.class, null, false, d -> new StatsScreen(this));
        screen.show();
    }

    @Override
    public void showSaveLogs(List<Device> devices) {
        if (devices == null || devices.isEmpty()) return;
        SaveLogsScreen screen = openWindow(SaveLogsScreen.class, null, false, d -> new SaveLogsScreen(this));
        screen.setDeviceList(devices);
        screen.show();
    }

    @Override
    public void showCommand(List<Device> devices) {
        if (devices == null || devices.isEmpty()) return;
        CommandScreen screen = openWindow(CommandScreen.class, null, false, d -> new CommandScreen(this));
        screen.setDeviceList(devices);
        screen.show();
    }

    @Override
    public void showMessage(String title, String text) {
        // NOTE: callers can be on a background thread (eg: adb command results)
        SwingUtilities.invokeLater(() -> {
            MessageViewScreen screen = openWindow(MessageViewScreen.class, null, false, d -> new MessageViewScreen(this));
            screen.setText(title, text);
            screen.show();
        });
    }

    // ========================================================================
    // App: cleanup callback
    // ========================================================================

    @Override
    public void onWindowClosed(BaseScreen screen) {
        windowMap.values().remove(screen);
        if (screen == headlessLogsScreen) headlessLogsScreen = null;
        if (headlessMode && screen instanceof ViewLogsScreen
                && headlessLogsScreen == null && !hasWindowOfType(ViewLogsScreen.class)) {
            // logs-only mode: closing the last logs window exits the app
            exit(true);
        }
    }

    private boolean hasWindowOfType(Class<? extends BaseScreen> type) {
        for (BaseScreen screen : windowMap.values()) {
            if (type.isInstance(screen)) return true;
        }
        return false;
    }

    /**
     * the open app-wide window of the given type, or null if it isn't open
     * <p>
     * NOTE: only for the screens that aren't bound to a single device (save-logs, command, message)
     */
    private <T extends BaseScreen> T findWindow(Class<T> type) {
        return type.cast(windowMap.get(windowKey(type, null)));
    }

    // ========================================================================
    // App: device state
    // ========================================================================

    @Override
    public void setDeviceBusy(Device device, boolean isBusy) {
        device.setBusy(isBusy);
        if (deviceScreen == null) return;
        Utils.runOnUi(() -> deviceScreen.model.updateDevice(device));
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
        // (snapshot values first - closeWindow removes itself from windowMap)
        for (BaseScreen screen : new ArrayList<>(windowMap.values())) {
            screen.closeWindow();
        }
        if (headlessLogsScreen != null) headlessLogsScreen.closeWindow();

        DeviceManager.getInstance().handleExit();

        if (updateExecutorService != null) {
            updateExecutorService.shutdown();
            updateExecutorService = null;
        }

        if (systemTray != null) {
            try {
                systemTray.shutdown();
            } catch (Exception e) {
                log.warn("exit: systemTray shutdown: {}", e.getMessage());
            }
            systemTray = null;
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
        if (deviceList == null) return;
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleDevicesUpdated(deviceList);
            for (Device device : deviceList) updateChildWindows(device);
            handleDeviceListChanged();
        });
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleDeviceUpdated(device);
            updateChildWindows(device);
            handleDeviceListChanged();
        });
    }

    @Override
    public void handleDeviceRemoved(Device device) {
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleDeviceRemoved(device);
            updateChildWindows(device);
            handleDeviceListChanged();
        });
    }

    /**
     * shared tail of all 3 device-listener callbacks: everything here reflects the device list as a
     * whole rather than one device
     * <p>
     * NOTE: this has to run for single-device updates and removals too, not just a full list change.
     * DeviceManager only fires handleDevicesUpdated when a device is *added*, so unplugging one used
     * to leave a stale count in the tray icon/menu and the taskbar badge.
     */
    private void handleDeviceListChanged() {
        setupSystemTray();
        updateTaskbarBadge();
        // rows show live per-device status while recording
        SaveLogsScreen saveLogsScreen = findWindow(SaveLogsScreen.class);
        if (saveLogsScreen != null) saveLogsScreen.refreshDeviceRows();
        if (headlessLogsScreen != null) {
            headlessLogsScreen.setConnectedDevices(DeviceManager.getInstance().getDevices());
        }
        drainPendingLogsRequest();
    }

    @Override
    public void handleException(Exception e) {
        SwingUtilities.invokeLater(() -> {
            Component parent = deviceScreen;
            String[] choices = {"Retry"};
            // only option is Retry; if reconnect fails, handleException re-fires and the dialog
            // reappears. Closing the dialog via X also triggers a retry — there is no way out
            // of the loop until adb is reachable.
            DialogHelper.showOptionDialog(parent, "ADB Server",
                "Unable to connect to ADB server. Please check that it's running and re-try", choices);
            connectAdbServer();
        });
    }

    @Override
    public void handleStatusEvent(StatusEvent event) {
        SwingUtilities.invokeLater(() -> {
            if (deviceScreen != null) deviceScreen.handleStatusEvent(event);
        });
    }

    /**
     * push the refreshed device into every window bound to it, so an open window never shows state
     * from the last refresh
     * <p>
     * NOTE: snapshot the values - updateDevice can start/stop logging, which can end up closing a
     * window and mutating windowMap
     */
    private void updateChildWindows(Device device) {
        for (BaseScreen screen : new ArrayList<>(windowMap.values())) {
            if (screen.isShowingDevice(device)) screen.updateDevice(device);
        }
        if (headlessLogsScreen != null && headlessLogsScreen.isShowingDevice(device)) {
            headlessLogsScreen.updateDevice(device);
        }
    }

    /** macOS 26 plates the 1-slice icon.icns jDeploy writes, so set the dock icon from the bundled image */
    public void setupTaskbarIcon() {
        if (!Taskbar.isTaskbarSupported()) return;
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) return;
            BufferedImage image = UiUtils.getImage(Icons.APP_ICON, 0, 0);
            if (image == null) return;
            taskbar.setIconImage(image);
        } catch (Exception e) {
            log.error("setupTaskbarIcon: Exception: {}", e.getMessage());
        }
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
        DeviceManager deviceManager = DeviceManager.getInstance();
        List<Device> deviceList = deviceManager.getDevices();
        // sort by display name
        deviceList.sort((d1, d2) -> d1.getDisplayName().compareToIgnoreCase(d2.getDisplayName()));
        // compare list to previous list so we don't have to update tray anytime a device property is updated
        StringBuilder sb = new StringBuilder();
        for (Device device : deviceList) {
            sb.append(device.getDisplayName()).append("|").append(device.isOnline ? "1" : "0").append(";");
        }
        String hashCode = sb.toString();
        if (TextUtils.equals(systemTrayHashCode, hashCode)) return;
        systemTrayHashCode = hashCode;

        Menu menu;
        try {
            if (systemTray == null) {
                // Configure Dorkbox SystemTray before initialization
                // These settings prevent LinkageError on Java 17+ by disabling runtime class modifications
                SystemTray.ENABLE_ROOT_CHECK = false;
                SystemTray.AUTO_FIX_INCONSISTENCIES = false;
                SystemTray.AUTO_SIZE = true;
                if (Utils.isMac()) {
                    // Osx works the best but doesn't show icons; Swing shows icons but isn't native
                    SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing;
                }
                systemTray = SystemTray.get();
                if (systemTray == null) {
                    log.warn("setupSystemTray: SystemTray not supported on this platform");
                    return;
                }
                log.trace("setupSystemTray: {}", systemTray.getTrayImageSize());
                OsThemeDetector.getInstance().registerListener(isDark ->
                        SwingUtilities.invokeLater(() -> {
                            systemTrayHashCode = null;
                            setupSystemTray();
                        }));
            }
            BufferedImage trayImage = UiUtils.getTrayIconWithCount(deviceList.size());
            systemTray.setImage(trayImage);
            if (!Utils.isLinux()) {
                systemTray.setTooltip(deviceList.size() + " Devices");
            }
            menu = systemTray.getMenu();
        } catch (LinkageError e) {
            log.error("setupSystemTray: LinkageError: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("setupSystemTray: Exception: {}", e.getMessage());
            return;
        }
        if (menu == null) return;

        // clear menu
        for (Entry entry : menu.getEntries()) menu.remove(entry);

        MenuItem openItem = new MenuItem("Open", UiUtils.getImage(Icons.OPEN, 16, 16, Color.BLACK));
        openItem.setCallback(e2 -> bringMainWindowToFront());
        menu.add(openItem);

        menu.add(new Separator());

        // show local devices first
        deviceList.removeIf(device -> device.remoteConnection != null);
        addSystemTrayDevices(menu, deviceList);

        // show servers and their devices
        List<RemoteConnection> remoteConnections = deviceManager.getRemoteConnectionManager().getActiveConnections();
        remoteConnections.sort(Comparator.comparing(RemoteConnection::getName, String.CASE_INSENSITIVE_ORDER));
        for (RemoteConnection server : remoteConnections) {
            if (!server.isConnected()) continue;
            String name = TextUtils.truncate("Server: " + server.getName(), 30);
            Color color = new Color(server.getServerConfig().color);
            Menu serverItem = new Menu(name, UiUtils.getImage(Icons.SERVER, 16, 16, color));
            List<Device> serverDeviceList = server.getDeviceList();
            serverDeviceList.sort((d1, d2) -> d1.getDisplayName().compareToIgnoreCase(d2.getDisplayName()));
            addSystemTrayDevices(serverItem, serverDeviceList);
            menu.add(serverItem);
        }

        menu.add(new Separator());

        MenuItem quitItem = new MenuItem("Quit", UiUtils.getImage(Icons.POWER, 16, 16, Color.BLACK));
        quitItem.setCallback(e2 -> exit(true));
        menu.add(quitItem);
    }

    private void addSystemTrayDevices(Menu menu, List<Device> deviceList) {
        for (Device device : deviceList) {
            Icons icn = device.getDeviceIcon();
            Color color = device.getDeviceColor();
            Image imageIcon = UiUtils.getImage(icn, 16, 16, color);
            String displayName = device.getDisplayName();
            Menu submenu = new Menu(TextUtils.truncate(displayName, 30), imageIcon);
            submenu.setEnabled(device.isOnline);

            if (device.isOnline) {
                if (!headlessMode) {
                    MenuItem mirrorItem = new MenuItem("Mirror", UiUtils.getImage(Icons.MIRROR, 16, 16, Color.BLACK));
                    mirrorItem.setCallback(e2 -> mirrorDeviceFromTray(device));
                    submenu.add(mirrorItem);
                }

                MenuItem browseItem = new MenuItem("Browse", UiUtils.getImage(Icons.BROWSE, 16, 16, Color.BLACK));
                browseItem.setCallback(e2 -> showFileBrowser(device));
                submenu.add(browseItem);

                MenuItem logsItem = new MenuItem("Logs", UiUtils.getImage(Icons.LOGS, 16, 16, Color.BLACK));
                logsItem.setCallback(e2 -> showLogs(device));
                submenu.add(logsItem);
            }

            menu.add(submenu);
        }
    }

    private void mirrorDeviceFromTray(Device device) {
        setDeviceBusy(device, true);
        DeviceManager.getInstance().mirrorDevice(device, (isSuccess, error) -> setDeviceBusy(device, false));
    }

    private void bringMainWindowToFront() {
        JFrame frame = headlessMode ? headlessLogsScreen : deviceScreen;
        if (frame == null) return;
        if (frame.isActive()) return;
        JFrame target = frame;
        SwingUtilities.invokeLater(() -> {
            if (!target.isVisible()) {
                target.setVisible(true);
                target.setState(JFrame.NORMAL);
                return;
            }
            target.setState(JFrame.ICONIFIED);
            Utils.runDelayed(300, true, () -> {
                target.setState(JFrame.NORMAL);
                Utils.runDelayed(300, true, () -> target.setState(JFrame.NORMAL));
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
        for (GithubRelease release : releases) {
            // skip the "jdeploy" prerelease - it holds package-info.json for jdeploy's auto-updater
            if (release.prerelease) continue;
            Utils.CompareResult compareResult = Utils.compareVersion(MainApplication.version, release.tagName);
            if (compareResult == Utils.CompareResult.VERSION_NEWER) {
                version = release.tagName;
                desc = release.body;
            }
            // releases are returned newest first
            break;
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
