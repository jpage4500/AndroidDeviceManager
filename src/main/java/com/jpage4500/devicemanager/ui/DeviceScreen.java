package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.*;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.client.RemoteConnection;
import com.jpage4500.devicemanager.manager.server.RemoteServerManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.utils.DeviceCellRenderer;
import com.jpage4500.devicemanager.table.utils.DeviceRowSorter;
import com.jpage4500.devicemanager.table.utils.TableColumnAdjuster;
import com.jpage4500.devicemanager.ui.dialog.*;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.utils.*;
import dorkbox.systemTray.*;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * create and manage device view
 */
public class DeviceScreen extends BaseScreen implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceScreen.class);

    private static final String HINT_FILTER_DEVICES = "Search";
    public static final String SHOW_DEVICE_LIST = "Show Device List";
    public static final String SHOW_BROWSE = "Show File Browser";
    public static final String SHOW_LOG_VIEWER = "Show Device Logs";
    public static final String SHOW_ACTIVITIES = "Show Activities";
    public static final String PREF_KEY_DEVICES = "devices";

    // update check for github releases
    public static final String UPDATE_SOURCE_GITHUB = "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases";
    public static final String URL_GITHUB = "https://github.com/jpage4500/AndroidDeviceManager/releases";
    public static final String PACKAGE_PREFIX = "package:";

    public CustomTable table;
    public DeviceTableModel model;
    private DeviceRowSorter sorter;
    public JToolBar toolbar;
    private HintTextField filterTextField;

    // system tray
    private SystemTray systemTray;
    private String systemTrayHashCode;

    // status bar items
    private HoverLabel updateLabel;         // update
    private HoverLabel versionLabel;        // version
    private HoverLabel memoryLabel;
    private JLabel countLabel;             // total devices

    private boolean hasSelectedDevice;
    // update checking
    private String updateVersion;
    private String updateDesc;

    private ScheduledExecutorService updateExecutorService;

    // open windows (per device)
    private final Map<String, ExploreScreen> exploreViewMap = new HashMap<>();
    private final Map<String, ViewLogsScreen> logsViewMap = new HashMap<>();
    private final Map<String, InputScreen> inputViewMap = new HashMap<>();
    private SaveLogsScreen saveLogsScreen;
    private ActivityDialog activityDialog;

    private static volatile boolean hasExited = false; // idempotent exit flag

    public DeviceScreen() {
        super("main", 900, 300);

        // create and initialize Device Manager
        DeviceManager.getInstance().initialize(this);

        initalizeUi();

        scheduleUpdateChecks();
    }

    public void scheduleUpdateChecks() {
        // cancel any current scheduled update checks
        if (updateExecutorService != null) {
            updateExecutorService.shutdownNow();
            updateExecutorService = null;
        }

        // check for updates (default: true)
        boolean checkUpdates = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_CHECK_UPDATES, true);
        if (checkUpdates) {
            updateExecutorService = Executors.newSingleThreadScheduledExecutor();
            // check after 5 seconds, then again every 12 hours
            updateExecutorService.scheduleAtFixedRate(() -> checkForUpdates(null), 5, TimeUnit.HOURS.toSeconds(12), TimeUnit.SECONDS);
        }
    }

    protected void initalizeUi() {
        setTitle("Device Manager");
        JPanel panel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        filterTextField = new HintTextField(HINT_FILTER_DEVICES, this::filterDevices);
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // -- table --
        table = new CustomTable(PREF_KEY_DEVICES);
        setupTable();
        panel.add(table.getScrollPane(), BorderLayout.CENTER);

        // -- statusbar --
        setupStatusBar(panel);

        setupMenuBar();
        setupSystemTray();
        setContentPane(panel);

        setVisible(true);

        refreshUi();
        table.requestFocus();
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSING -> exitApp(false);
        }
    }

    /**
     * exit app or just hide screen if user has 'exit to tray' setting enabled
     *
     * @param forceQuit true to exit regardless of setting
     */
    private void exitApp(boolean forceQuit) {
        if (hasExited) {
            log.debug("exitApp: already executed (force:{})", forceQuit);
            return;
        }
        boolean exitToTray = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_EXIT_TO_TRAY);
        log.debug("exitApp: force:{} exitToTray:{}", forceQuit, exitToTray);

        // Check if server is running and prompt user before exiting
        if (!forceQuit && !exitToTray) {
            RemoteServerManager serverManager = DeviceManager.getInstance().getRemoteServerManager();
            if (serverManager != null && serverManager.isRunning()) {
                boolean shouldExit = DialogHelper.showConfirmDialog(this, "Server Running",
                    "The remote server is currently running. Exiting will stop the server.\n\nDo you want to exit?");
                if (!shouldExit) return;
            }
        }

        setVisible(false);
        if (!forceQuit && exitToTray) {
            log.trace("exitApp: exit-to-tray preference active -> keeping process running");
            return;
        }

        hasExited = true;
        saveFrameSize();
        table.saveTable();

        // save positions/sizes of any other open windows
        // NOTE: only saving FIRST open window position
        if (!exploreViewMap.isEmpty())
            (exploreViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSING);
        if (!logsViewMap.isEmpty())
            (logsViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSING);
        if (!inputViewMap.isEmpty())
            (inputViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSING);
        if (saveLogsScreen != null) saveLogsScreen.onWindowStateChanged(WindowState.CLOSING);

        DeviceManager.getInstance().handleExit();

        // shutdown update executor service
        if (updateExecutorService != null) {
            updateExecutorService.shutdown();
            updateExecutorService = null;
        }

        // Shutdown SystemTray
        if (systemTray != null) {
            systemTray.shutdown();
        }

        // shutdown file logging executor
        try {
            AppLoggerFactory loggerFactory = (AppLoggerFactory) org.slf4j.LoggerFactory.getILoggerFactory();
            loggerFactory.shutdown();
        } catch (Exception ignored) {
        }

        dispose();

        System.exit(0);
    }

    private void setupStatusBar(JPanel panel) {
        JPanel statusBar = new JPanel(new BorderLayout());
        UiUtils.setEmptyBorder(statusBar, 0, 0);

        // left
        JPanel leftPanel = new JPanel();
        UiUtils.setEmptyBorder(leftPanel, 0, 0);

        // update
        ImageIcon icon = UiUtils.getImageIcon(Icons.UPDATE, UiUtils.IMG_SIZE_SMALL);
        updateLabel = new HoverLabel(icon);
        updateLabel.setToolTipText("Check for updates");
        leftPanel.add(updateLabel);
        UiUtils.addLeftClickListener(updateLabel, this::handleUpdateClicked);

        // version
        versionLabel = new HoverLabel();
        leftPanel.add(versionLabel);
        UiUtils.addLeftClickListener(versionLabel, this::handleVersionClicked);
        versionLabel.setText("v" + MainApplication.version);

        // memory
        icon = UiUtils.getImageIcon(Icons.MEMORY, UiUtils.IMG_SIZE_SMALL);
        memoryLabel = new HoverLabel(icon);
        memoryLabel.setBorder(0, 0);
        leftPanel.add(memoryLabel);
        UiUtils.addLeftClickListener(memoryLabel, this::showSystemEnvironmentDialog);

        statusBar.add(leftPanel, BorderLayout.WEST);

        // count
        countLabel = new JLabel();
        UiUtils.setEmptyBorder(countLabel);
        statusBar.add(countLabel, BorderLayout.EAST);

        panel.add(statusBar, BorderLayout.SOUTH);
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdMenuItem(windowMenu, "Close Window", KeyEvent.VK_W, e -> exitApp(false));

        // [CMD + 2] = show explorer
        createCmdMenuItem(windowMenu, SHOW_BROWSE, KeyEvent.VK_2, e -> handleBrowseCommand(null));

        // [CMD + 3] = show logs
        createCmdMenuItem(windowMenu, SHOW_LOG_VIEWER, KeyEvent.VK_3, e -> handleViewLogsCommand(null));

        // [CMD + 4] = show activities
        createCmdMenuItem(windowMenu, SHOW_ACTIVITIES, KeyEvent.VK_4, e -> showActivityDialog());

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
        createCmdMenuItem(deviceMenu, "Connect to Device", KeyEvent.VK_N, e -> showConnectAdbWirelessDialog());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(deviceMenu);
        setJMenuBar(menubar);
    }

    public ActivityDialog showActivityDialog() {
        if (activityDialog == null) {
            activityDialog = new ActivityDialog(this);
        }
        activityDialog.showDialog();
        return activityDialog;
    }

    private void hideToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    public void setupTable() {
        model = new DeviceTableModel();

        // restore previous settings
        setCustomColumns();

        List<String> hiddenColList = SettingsDialog.getHiddenColumnList();
        model.setHiddenColumns(hiddenColList);

        table.setModel(model);
        table.setDefaultRenderer(Device.class, new DeviceCellRenderer());
        table.setEmptyText("No Connected Devices!");

        boolean autoResize = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_DEVICE_AUTO_RESIZE, true);
        int flag = autoResize ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF;
        table.setAutoResizeMode(flag);

        // restore user-defined column sizes
        if (!table.restoreTable()) {
            // use some default column sizes
            table.setPreferredColWidth(DeviceTableModel.Columns.NAME.name(), 185);
            table.setPreferredColWidth(DeviceTableModel.Columns.SERIAL.name(), 152);
            table.setPreferredColWidth(DeviceTableModel.Columns.PHONE.name(), 116);
            table.setPreferredColWidth(DeviceTableModel.Columns.IMEI.name(), 147);
            table.setPreferredColWidth(DeviceTableModel.Columns.OS.name(), 31);
            table.setPreferredColWidth(DeviceTableModel.Columns.BATTERY.name(), 31);
            table.setPreferredColWidth(DeviceTableModel.Columns.FREE.name(), 66);
            // set max sizes
            table.setMaxColWidth(DeviceTableModel.Columns.BATTERY.name(), 31);
            table.setMaxColWidth(DeviceTableModel.Columns.OS.name(), 31);
            table.setMaxColWidth(DeviceTableModel.Columns.FREE.name(), 80);
        }

        sorter = new DeviceRowSorter(model);
        table.setRowSorter(sorter);

        table.setDoubleClickListener((row, column, e) -> {
            log.trace("table.setDoubleClickListener: row: {}, column: {}", row, column);
            if (column == DeviceTableModel.Columns.CUSTOM1.ordinal()) {
                // edit custom 1 field
                handleSetProperty(Device.CUSTOM_PROP_X + 1, DeviceTableModel.Columns.CUSTOM1.toString());
                return;
            } else if (column == DeviceTableModel.Columns.CUSTOM2.ordinal()) {
                // edit custom 1 field
                handleSetProperty(Device.CUSTOM_PROP_X + 2, DeviceTableModel.Columns.CUSTOM2.toString());
                return;
            } else if (column == DeviceTableModel.Columns.PHONE.ordinal()) {
                Device device = getFirstSelectedDevice();
                if (device != null && TextUtils.isEmpty(device.phone)) {
                    // edit phone number field
                    handleSetProperty(Device.CUST_PROP_PHONE, "Device Phone Number");
                    return;
                }
            }
            // default double-click action
            handleMirrorCommand(null);
        });

        // support drag and drop of files IN TO deviceView
        new DropTarget(table, new FileDragAndDropListener(table, this::handleFilesDropped));

        table.setPopupMenuListener((row, column) -> getPopupMenu(row, column));

        table.setTooltipListener((row, col) -> {
            int modelCol = table.convertColumnIndexToModel(col);
            DeviceTableModel.Columns columnType = model.getColumnType(modelCol);
            if (row >= 0) {
                int modelRow = table.convertRowIndexToModel(row);
                Device device = (Device) model.getValueAt(modelRow, modelCol);

                if (columnType == DeviceTableModel.Columns.BATTERY) {
                    // always show battery level and power status in tooltip
                    String tooltip = device.batteryLevel + "%";
                    if (device.powerStatus != Device.PowerStatus.POWER_NONE)
                        tooltip += " (" + device.powerStatus + ")";
                    return tooltip;
                }
            }
            return table.getTextIfTruncated(row, col);
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            refreshUi();
        });

        filterTextField.setupSearch(table);
    }

    public void setCustomColumns() {
        List<String> entryList = SettingsDialog.getCustomColumns();
        List<String> nameList = new ArrayList<>();
        for (String entry : entryList) {
            if (TextUtils.isEmpty(entry) || TextUtils.startsWithAny(entry, false, "#", "//"))
                continue;
            String[] entryArr = entry.split(":");
            String label = entryArr.length >= 1 ? entryArr[0].trim() : entry;
            nameList.add(label);
        }
        model.setCustomColumnList(nameList);
    }

    /**
     * @return PopupMenu to display or null
     */
    private JPopupMenu getPopupMenu(int row, int column) {
        if (row == -1) {
            // header
            JPopupMenu popupMenu = new JPopupMenu();
            DeviceTableModel.Columns columnType = model.getColumnType(column);
            if (columnType != null) {
                // standard columns (all others are custom)
                UiUtils.addPopupMenuItem(popupMenu, "Hide " + columnType.name(), Icons.EYE_CLOSED, actionEvent -> handleHideColumn(column));
            }
            UiUtils.addPopupMenuItem(popupMenu, "Size to Fit", Icons.SIZE, actionEvent -> {
                TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 0);
                adjuster.adjustColumn(column);
            });

            popupMenu.addSeparator();

            UiUtils.addPopupMenuItem(popupMenu, "Manage Columns", Icons.SETTINGS, actionEvent -> SettingsDialog.showManageDeviceColumnsDialog(this, this));

            boolean autoResize = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_DEVICE_AUTO_RESIZE, true);
            String resizeDesc = autoResize ? "ON" : "OFF";
            UiUtils.addPopupMenuItem(popupMenu, "Auto Resize: " + resizeDesc, Icons.SIZE, actionEvent -> {
                boolean update = !autoResize;
                PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_DEVICE_AUTO_RESIZE, update);
                int flag = update ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF;
                table.setAutoResizeMode(flag);
            });
            if (!autoResize) {
                UiUtils.addPopupMenuItem(popupMenu, "Size ALL to Fit", Icons.SIZE, actionEvent -> {
                    TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 0);
                    adjuster.adjustColumns();
                });
            }
            return popupMenu;
        }

        // device popup
        Device device = model.getDeviceAtRow(row);
        if (device == null) return null;
        JPopupMenu popupMenu = new JPopupMenu();

        if (device.isOnline) {
            DeviceTableModel.Columns columnType = model.getColumnType(column);
            if (columnType == DeviceTableModel.Columns.CUSTOM1) {
                UiUtils.addPopupMenuItem(popupMenu, "Edit Custom Field 1...", actionEvent -> handleSetProperty(Device.CUSTOM_PROP_X + 1, DeviceTableModel.Columns.CUSTOM1.toString()));
                popupMenu.addSeparator();
            } else if (columnType == DeviceTableModel.Columns.CUSTOM2) {
                UiUtils.addPopupMenuItem(popupMenu, "Edit Custom Field 2...", actionEvent -> handleSetProperty(Device.CUSTOM_PROP_X + 2, DeviceTableModel.Columns.CUSTOM2.toString()));
                popupMenu.addSeparator();
            } else if (columnType == DeviceTableModel.Columns.PHONE) {
                UiUtils.addPopupMenuItem(popupMenu, "Edit Phone Number...", actionEvent -> handleSetProperty(Device.CUST_PROP_PHONE, "Device Phone Number"));
                popupMenu.addSeparator();
            }

            UiUtils.addPopupMenuItem(popupMenu, "Copy Field to Clipboard", Icons.COPY, actionEvent -> handleCopyClipboardFieldCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Copy Line to Clipboard", Icons.COPY, actionEvent -> handleCopyClipboardCommand());
            popupMenu.addSeparator();

            // device details
            UiUtils.addPopupMenuItem(popupMenu, "Device Details", Icons.LOGS, actionEvent -> handleDeviceDetails(device));

            List<ToolbarButton> toolbarButtons = new ArrayList<>(List.of(ToolbarButton.values()));

            // remove any non-device specific actions
            toolbarButtons.removeAll(List.of(ToolbarButton.CONNECT, ToolbarButton.SCRIPTS, ToolbarButton.FILTER,
                ToolbarButton.ADB, ToolbarButton.REFRESH, ToolbarButton.SERVER, ToolbarButton.SETTINGS));

            for (ToolbarButton toolbarButton : toolbarButtons) {
                UiUtils.addPopupMenuItem(popupMenu, toolbarButton.label, toolbarButton.icn, e -> {
                    handleButtonClicked(toolbarButton, null);
                });
            }

            if (device.isWireless()) {
                popupMenu.addSeparator();
                UiUtils.addPopupMenuItem(popupMenu, "Disconnect " + device.getDisplayName(), actionEvent -> handleDisconnect(device));
            }
        } else {
            // offline device
            if (device.isWireless()) {
                UiUtils.addPopupMenuItem(popupMenu, "Reconnect", actionEvent -> handleReconnectDevice(device));
            }
            UiUtils.addPopupMenuItem(popupMenu, "Remove", actionEvent -> handleRemoveDevice(device));
        }
        return popupMenu;
    }

    private void setupTaskBar() {
        if (!Taskbar.isTaskbarSupported()) return;
        // badge number
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER)) {
                int numOnline = 0;
                for (Device device : DeviceManager.getInstance().getDevices()) {
                    if (device.isOnline) numOnline++;
                }
                String badge = numOnline > 0 ? String.valueOf(numOnline) : null;
                taskbar.setIconBadge(badge);
            }
        } catch (final Exception e) {
            log.error("setupTaskBar: Exception: {}", e.getMessage());
        }
    }

    private void setupSystemTray() {
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
                //SystemTray.DEBUG = true;
                SystemTray.ENABLE_ROOT_CHECK = false;
                SystemTray.AUTO_FIX_INCONSISTENCIES = false;
                SystemTray.AUTO_SIZE = true;
                // Osx works the best but doesn't show icons
                // Swing shows icons but doesn't look native and has focus issues
                if (Utils.isMac()) {
                    //SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Osx;
                    SystemTray.FORCE_TRAY_TYPE = SystemTray.TrayType.Swing;
                }
                //
                // Disable javafx/swt/gtk detection to avoid class loading issues
                // System.setProperty("SystemTray.PREFER_GTK3", "false");
                systemTray = SystemTray.get();
                if (systemTray == null) {
                    log.warn("setupSystemTray: SystemTray not supported on this platform");
                    return;
                }
                log.trace("setupSystemTray: {}", systemTray.getTrayImageSize());
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
        openItem.setCallback(e2 -> bringWindowToFront());
        menu.add(openItem);

        menu.add(new Separator());

        // show local devices first
        deviceList.removeIf(device -> device.remoteConnection != null);
        addSystemTrayDevices(menu, deviceList);

        // show servers and their devices
        List<RemoteConnection> remoteConnections = deviceManager.getRemoteConnectionManager().getActiveConnections();
        // sort by name
        remoteConnections.sort(Comparator.comparing(RemoteConnection::getName, String.CASE_INSENSITIVE_ORDER));
        for (RemoteConnection server : remoteConnections) {
            String name = TextUtils.truncate("Server: " + server.getName(), 30);
            Color color = new Color(server.getServerConfig().color);
            Menu serverItem = new Menu(name, UiUtils.getImage(Icons.SERVER, 16, 16, color));
            List<Device> serverDeviceList = server.getDeviceList();
            // sort by display name
            serverDeviceList.sort((d1, d2) -> d1.getDisplayName().compareToIgnoreCase(d2.getDisplayName()));
            addSystemTrayDevices(serverItem, serverDeviceList);

            menu.add(serverItem);
        }

        menu.add(new Separator());

        MenuItem quitItem = new MenuItem("Quit", UiUtils.getImage(Icons.POWER, 16, 16, Color.BLACK));
        quitItem.setCallback(e2 -> exitApp(true));
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
            // note: tooltips don't display on mac
            //submenu.setTooltip(TextUtils.truncate(displayName, 64));

            if (device.isOnline) {
                MenuItem mirrorItem = new MenuItem("Mirror", UiUtils.getImage(Icons.MIRROR, 16, 16, Color.BLACK));
                mirrorItem.setCallback(e2 -> handleMirrorCommand(device));
                submenu.add(mirrorItem);

                MenuItem browseItem = new MenuItem("Browse", UiUtils.getImage(Icons.BROWSE, 16, 16, Color.BLACK));
                browseItem.setCallback(e2 -> handleBrowseCommand(device));
                submenu.add(browseItem);

                MenuItem logsItem = new MenuItem("Logs", UiUtils.getImage(Icons.LOGS, 16, 16, Color.BLACK));
                logsItem.setCallback(e2 -> handleViewLogsCommand(device));
                submenu.add(logsItem);
            }

            menu.add(submenu);
        }
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        SwingUtilities.invokeLater(() -> {
            model.setDeviceList(deviceList);

            // auto-select first device
            if (!hasSelectedDevice && !deviceList.isEmpty() && table.getSelectedRow() == -1) {
                table.changeSelection(0, 0, false, false);
                hasSelectedDevice = true;
            }

            refreshUi();

            for (Device device : deviceList) {
                updateDeviceState(device);
            }

            setupSystemTray();
            setupTaskBar();
        });
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        SwingUtilities.invokeLater(() -> {
            model.updateDevice(device);
            updateDeviceState(device);
            sorter.sort();

            setupSystemTray();
            setupTaskBar();
        });
    }

    @Override
    public void handleDeviceRemoved(Device device) {
        SwingUtilities.invokeLater(() -> {
            model.removeDevice(device);
            updateDeviceState(device);
            sorter.sort();

            setupSystemTray();
            setupTaskBar();
        });
    }

    @Override
    public void handleException(Exception e) {
        SwingUtilities.invokeLater(() -> {
            String[] choices = {"Retry", "Cancel"};
            int rc = DialogHelper.showOptionDialog(this, "ADB Server", "Unable to connect to ADB server. Please check that it's running and re-try", choices);
            if (rc == 0) DeviceManager.getInstance().connectAdbServer(true);
        });
    }

    private void bringWindowToFront() {
        if (isActive()) return;
        // requires multiple steps otherwise this won't work..
        SwingUtilities.invokeLater(() -> {
            if (!isVisible()) {
                setVisible(true);
                setState(JFrame.NORMAL);
                return;
            }
            setState(JFrame.ICONIFIED);
            Utils.runDelayed(300, true, () -> {
                setState(JFrame.NORMAL);
                Utils.runDelayed(300, true, () -> setState(JFrame.NORMAL));
            });
        });
    }

    private void updateDeviceState(Device device) {
        ExploreScreen exploreScreen = exploreViewMap.get(device.serial);
        if (exploreScreen != null) exploreScreen.updateDevice(device);

        ViewLogsScreen logsScreen = logsViewMap.get(device.serial);
        if (logsScreen != null) logsScreen.updateDevice(device);

        InputScreen inputScreen = inputViewMap.get(device.serial);
        if (inputScreen != null) inputScreen.updateDevice(device);

        if (saveLogsScreen != null) saveLogsScreen.updateDevice();
    }

    private void refreshUi() {
        int selectedRowCount = table.getSelectedRowCount();
        int rowCount = table.getRowCount();
        String filterText = (sorter != null) ? sorter.getFilterText() : null;
        if (TextUtils.notEmpty(filterText)) {
            int totalDevices = model.getRowCount();
            countLabel.setText("found: " + rowCount + " / " + totalDevices);
        } else if (selectedRowCount > 1) {
            countLabel.setText("selected: " + selectedRowCount + " / " + rowCount);
        } else {
            countLabel.setText("total: " + rowCount);
        }

        // memory
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        memoryLabel.setText(FileUtils.bytesToDisplayString(usedMemory));
    }

    private void handleHideColumn(int column) {
        DeviceTableModel.Columns columnType = model.getColumnType(column);
        if (columnType == null) return;
        List<String> hiddenColList = SettingsDialog.getHiddenColumnList();
        hiddenColList.add(columnType.name());
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS, GsonHelper.toJson(hiddenColList));
        restoreTable();
    }

    public void restoreTable() {
        table.saveTable();
        List<String> hiddenColList = SettingsDialog.getHiddenColumnList();
        model.setHiddenColumns(hiddenColList);
        table.restoreTable();
        // set max sizes
        table.setMaxColWidth(DeviceTableModel.Columns.BATTERY.name(), 31);
        table.setMaxColWidth(DeviceTableModel.Columns.FREE.name(), 80);
    }

    private void handleCopyClipboardFieldCommand() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;
        int selectedColumn = table.getSelectedColumn();
        int modelCol = table.convertColumnIndexToModel(selectedColumn);
        if (modelCol < 0) return;
        log.trace("handleCopyClipboardFieldCommand: col:{}, devices:{}", modelCol, selectedDeviceList.size());

        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (!sb.isEmpty()) sb.append("\n");
            String value = model.deviceValue(device, modelCol);
            sb.append(value != null ? value : "");
        }
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private void handleCopyClipboardCommand() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (!sb.isEmpty()) sb.append("\n");
            for (int i = 0; i < model.getColumnCount(); i++) {
                if (i > 0) sb.append(", ");
                String value = model.deviceValue(device, i);
                sb.append(value != null ? value : "");
            }
        }
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private void handleTermCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;
        else if (checkRemoteDevices(selectedDeviceList, true)) return;

        if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            if (!DialogHelper.showConfirmDialog(this, "Open Terminal", "Open Terminal for " + selectedDeviceList.size() + " devices?"))
                return;
        }
        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().openTerminal(device, (isSuccess, error) -> {

            });
        }
    }

    /**
     * check if any devices in the list is a remote device
     *
     * @param showWarning - true to show a warning if 1 or more devices are remote
     * @return true if any are remote; however if showWarning is true and the user chooses to continue, false is returned
     */
    private boolean checkRemoteDevices(List<Device> list, boolean showWarning) {
        int numRemote = 0;
        for (Device device : list) {
            if (device.remoteConnection != null) {
                numRemote++;
            }
        }
        if (numRemote > 0 && showWarning) {
            boolean isYes = DialogHelper.showConfirmDialog(this, "Remote Devices", "Remote Devices aren't supported with this feature. Continue anyway?");
            if (isYes) {
                // remove remote devices
                list.removeIf(device -> device.remoteConnection != null);
                // return false to continue
                return false;
            } else {
                return true;
            }
        } else if (numRemote > 0) {
            return true;
        }
        return false;
    }

    /**
     * called when user double-clicks on .apk file in OS file manager
     * - similar to handleFilesDropped() but waits a bit until a device is connected
     */
    public void handleFilesOpened(List<File> fileList, int attempt) {
        log.debug("handleFilesOpened: {} files, attempt:{}", fileList.size(), attempt);
        List<Device> deviceList = DeviceManager.getInstance().getDevices();
        if (deviceList.isEmpty()) {
            // try again later (until 5 attempts)
            if (attempt <= 5) {
                Utils.runDelayed(1000, true, () -> handleFilesOpened(fileList, attempt + 1));
            } else {
                DialogHelper.showDialog(this, "Device Manager", "No devices connected. Please connect a device and try again.");
            }
        } else if (deviceList.size() == 1) {
            // install/copy to this device
            installOrCopyFiles(deviceList, fileList);
        } else {
            // Show device selection dialog
            DialogHelper.showDeviceSelectionDialog(this, "Install/Copy Files", null, selectedDevices -> {
                installOrCopyFiles(selectedDevices, fileList);
            });
        }
    }

    /**
     * called from drag & drop of files to device manager window
     */
    private void handleFilesDropped(List<File> fileList) {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) {
            log.error("handleFilesDropped: no devices! {}", fileList);
            return;
        }
        log.debug("handleFilesDropped: {}, #devices:{}", fileList, selectedDeviceList.size());
        installOrCopyFiles(selectedDeviceList, fileList);
    }

    public void installOrCopyFiles(List<Device> selectedDeviceList, List<File> fileList) {
        if (selectedDeviceList == null || selectedDeviceList.isEmpty()) return;
        FileUtils.FileStats stats = FileUtils.getFileStats(fileList);
        // if all files are .apk, do install instead of copy
        boolean isInstall = stats.numApk == stats.numTotal;
        String title = isInstall ? "Install App" : "Copy File(s)";
        String msg = isInstall ? "Install " : "Copy ";
        msg += TextUtils.join(stats.nameList, ", ");
        msg += " to " + selectedDeviceList.size() + " device(s)?";

        // prompt to install/copy
        // NOTE: using JDialog.setAlwaysOnTap to bring app to foreground on drag and drop operations
        final JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);
        if (!DialogHelper.showConfirmDialog(this, title, msg)) return;
        if (isInstall) {
            installFiles(selectedDeviceList, fileList);
        } else {
            copyFiles(selectedDeviceList, fileList);
        }
    }

    private void copyFiles(List<Device> selectedDeviceList, List<File> fileList) {
        ActivityDialog activityDialog = showActivityDialog();

        StringBuilder fileNames = new StringBuilder();
        for (File file : fileList) {
            if (!fileNames.isEmpty()) fileNames.append(", ");
            else fileNames.append("[");
            fileNames.append(file.getName());
        }
        fileNames.append("]");

        // TODO: where to put files on device?
        String destFolder = "/sdcard/Download/";
        for (Device device : selectedDeviceList) {
            setDeviceBusy(device, true);
            // copy [abc.jpg, hello.text] to "device name"
            String operationDesc = String.format("Copy %s -> %s", fileNames, device.getDisplayName());
            int activityId = activityDialog.addOperation(operationDesc, Icons.COPY);
            DeviceManager.getInstance().copyFiles(device, fileList, destFolder, (numCompleted, numTotal, msg) -> {
                int progress = (numCompleted * 100) / numTotal;
                activityDialog.updateOperation(activityId, progress, msg);
            }, (isSuccess, error) -> {
                setDeviceBusy(device, false);
                String msg = isSuccess ? "✅ Success" : "❌ Failed";
                if (!isSuccess && error != null) msg += ": " + error;
                activityDialog.updateOperation(activityId, 100, msg);
            });
        }
    }

    private void installFiles(List<Device> selectedDeviceList, List<File> fileList) {
        if (fileList.isEmpty() || selectedDeviceList.isEmpty()) return;
        // separate out all remote devices so we run 1 install action per remote server/connection
        Map<RemoteConnection, List<Device>> remoteDeviceMap = new HashMap<>();
        List<Device> localDeviceList = new ArrayList<>();
        for (Device device : selectedDeviceList) {
            if (device.remoteConnection != null) {
                List<Device> remoteDeviceList = remoteDeviceMap.computeIfAbsent(device.remoteConnection, k -> new ArrayList<>());
                remoteDeviceList.add(device);
            } else {
                localDeviceList.add(device);
            }
        }

        ActivityDialog activityDialog = showActivityDialog();

        for (File file : fileList) {
            // install on local devices first
            for (Device device : localDeviceList) {
                setDeviceBusy(device, true);
                String label = String.format("Install %s -> %s", file.getName(), device.getDisplayName());
                final int activityId = activityDialog.addOperation(label, Icons.FILE_APK);
                DeviceManager.getInstance().installApp(device, file,
                    (currentStep, totalSteps, message) -> {
                        int percent = Math.max(0, Math.min(100, (int) Math.round((totalSteps > 0 ? (currentStep * 100.0 / totalSteps) : 0))));
                        activityDialog.updateOperation(activityId, percent, message);
                    },
                    (isSuccess, error) -> {
                        setDeviceBusy(device, false);
                        String msg = isSuccess ? "✅ Success" : "❌ Failed";
                        if (!isSuccess && error != null) msg += ": " + error;
                        activityDialog.updateOperation(activityId, 100, msg);
                        if (isSuccess) DeviceManager.getInstance().fetchDeviceDetails(device, true);
                    }
                );
            }
            // install on remote devices (grouped)
            remoteDeviceMap.forEach((remoteConnection, deviceList) -> {
                deviceList.forEach(device -> setDeviceBusy(device, true));
                String label = String.format("Install %s -> %s (%d devices)", file.getName(), remoteConnection.getName(), deviceList.size());
                final int activityId = activityDialog.addOperation(label, Icons.FILE_APK);
                DeviceManager.getInstance().installApp(remoteConnection, deviceList, file,
                    (currentStep, totalSteps, message) -> {
                        int percent = Math.max(0, Math.min(100, (int) Math.round((totalSteps > 0 ? (currentStep * 100.0 / totalSteps) : 0))));
                        activityDialog.updateOperation(activityId, percent, message);
                    },
                    (isSuccess, error) -> {
                        deviceList.forEach(device -> setDeviceBusy(device, false));
                        String msg = isSuccess ? "✅ Success" : "❌ Failed";
                        if (!isSuccess && error != null) msg += ": " + error;
                        activityDialog.updateOperation(activityId, 100, msg);
                        // TODO: refresh remote connection's devices
                        // remoteConnection.scheduleRefresh();
                    });
            });
        }
    }

    /**
     * set device property
     * uses "persist.dm.custom[number]" for key and prompts user for value
     */
    private void handleSetProperty(String property, String description) {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;
        String customValue = "";
        String message;
        if (selectedDeviceList.size() == 1) {
            Device device = selectedDeviceList.get(0);
            customValue = device.getCustomProperty(property);
            message = "Enter " + description;
        } else {
            message = "Enter " + description + " for " + selectedDeviceList.size() + " devices";
        }

        String result = DialogHelper.showInputDialog(this, description, message, customValue);
        // allow empty input to go through (clear current value)
        if (result == null) return;

        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().setProperty(device, property, result, (isSuccess, error) -> {
                handleDeviceUpdated(device);
            });
        }
    }

    private void handleInputCommand() {
        Device selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        InputScreen inputScreen = inputViewMap.get(selectedDevice.serial);
        if (inputScreen == null) {
            if (!selectedDevice.isOnline) return;
            inputScreen = new InputScreen(this, selectedDevice);
            inputViewMap.put(selectedDevice.serial, inputScreen);
        }
        inputScreen.show();
    }

    private void handleScreenshotCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;
        if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            if (!DialogHelper.showConfirmDialog(this, "Screenshot", "Take screenshot of " + selectedDeviceList.size() + " devices?"))
                return;
        }
        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size());
        for (Device device : selectedDeviceList) {
            setDeviceBusy(device, true);
            DeviceManager.getInstance().captureScreenshot(device, image -> {
                setDeviceBusy(device, false);
                boolean isSuccess = image != null;
                if (isSuccess) {
                    // save image to file
                    String downloadFolder = Utils.getDownloadFolder();
                    // 20211215-1441PM-1.png
                    String name = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".png";
                    try {
                        // save to file
                        File outputfile = new File(downloadFolder, name);
                        ImageIO.write(image, "png", outputfile);
                        log.debug("captureScreenshot: DONE: {}x{}, {}", image.getWidth(), image.getHeight(), outputfile.getAbsolutePath());
                        // open with default viewer
                        Utils.openFile(outputfile);
                    } catch (Exception e) {
                        log.error("captureScreenshot: {}", e.getMessage());
                    }
                }
                resultWatcher.handleResult(device.getDisplayName(), isSuccess, null);
            });
        }
    }

    public void setDeviceBusy(Device device, boolean isBusy) {
        device.setBusy(isBusy);

        // only update model on UI thread
        if (SwingUtilities.isEventDispatchThread()) {
            model.updateDevice(device);
        } else SwingUtilities.invokeLater(() -> {
            model.updateDevice(device);
        });
    }

    private void handleConnectButtonClicked(MouseEvent mouseEvent) {
        JPopupMenu popupMenu = new JPopupMenu();

        // adb wireless
        JMenuItem adbItem = new JMenuItem("Connect to ADB Wireless Device", UiUtils.getImageIcon(Icons.ADB, UiUtils.IMG_SIZE_SMALL));
        adbItem.addActionListener(e -> {
            showConnectAdbWirelessDialog();
        });
        popupMenu.add(adbItem);

        // connect to server
        JMenuItem serverItem = new JMenuItem("Connect to Remote Server", UiUtils.getImageIcon(Icons.SERVER, UiUtils.IMG_SIZE_SMALL));
        serverItem.addActionListener(e -> {
            RemoteServerDialog.showRemoteServerDialog(this);
        });
        popupMenu.add(serverItem);

        popupMenu.show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
    }

    private void showConnectAdbWirelessDialog() {
        ConnectDialog.showConnectDialog(this, (isSuccess, error) -> {
            log.debug("handleConnectDevice: {}", isSuccess);
            if (!isSuccess) {
                DialogHelper.showDialog(this, null, "Unable to connect!\n\nCheck if the device is showing an prompt to authorize");
            }
        });
    }

    private void handleDisconnect(Device device) {
        DeviceManager.getInstance().disconnectDevice(device.serial, (isSuccess, error) -> {
            if (!isSuccess) {
                DialogHelper.showDialog(this, null, "Unable to disconnect!");
            }
        });
    }

    private void handleRemoveDevice(Device device) {
        log.debug("handleRemoveDevice: {}", device.serial);
        model.removeDevice(device);
        refreshUi();
    }

    private void handleReconnectDevice(Device device) {
        String[] deviceSplit = device.serial.split(":");
        if (deviceSplit.length < 2) return;

        String ip = deviceSplit[0];
        int port;
        try {
            port = Integer.parseInt(deviceSplit[1]);
        } catch (NumberFormatException e) {
            log.error("Invalid port: " + deviceSplit[1]);
            return;
        }

        DeviceManager.getInstance().connectDevice(ip, port, (isSuccess, error) -> {
            if (!isSuccess) {
                DialogHelper.showDialog(this, null, "Unable to connect!");
            }
        });
    }

    private void handleDeviceDetails(Device device) {
        if (device == null) return;

        JPanel panel = new JPanel(new MigLayout());
        addDeviceDetail(panel, "Serial", device.serial);
        addDeviceDetail(panel, "Nickname", device.nickname);
        addDeviceDetail(panel, "Model", device.model);
        addDeviceDetail(panel, "Phone", device.phone);
        addDeviceDetail(panel, "IMEI", device.imei);
        addDeviceDetail(panel, "Carrier", device.carrier);
        addDeviceDetail(panel, "OS", device.os);
        addDeviceDetail(panel, "SDK", device.sdk);
        addDeviceDetail(panel, "Free Space", FileUtils.bytesToDisplayString(device.freeSpace));
        addDeviceDetail(panel, "Custom1", device.getCustomProperty(Device.CUST_PROP_1));
        addDeviceDetail(panel, "Custom2", device.getCustomProperty(Device.CUST_PROP_2));

        // device properties
        ImageIcon icon = UiUtils.getImageIcon(Icons.ARROW_RIGHT, UiUtils.IMG_SIZE_SMALL);
        HoverLabel devicePropLabel = new HoverLabel("Device Properties", icon);
        UiUtils.addLeftClickListener(devicePropLabel, mouseEvent -> showDeviceProperties(device));
        panel.add(devicePropLabel, "wrap");

        HoverLabel appsLabel = new HoverLabel("Installed Apps / Versions", icon);
        UiUtils.addLeftClickListener(appsLabel, mouseEvent -> showInstalledApps(device));
        panel.add(appsLabel, "wrap");

        DialogHelper.showCustomDialog(this, panel, "Device Info", null);
    }

    private void showInstalledApps(Device device) {
        if (device == null) return;
        DeviceManager.getInstance().getInstalledApps(device, appSet -> {
            final Map<String, String> appMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            // convert set to map
            for (String app : appSet) appMap.put(app, null);
            DialogHelper.showListDialog(this, "Installed Apps", appMap, new DialogHelper.ListListener() {
                @Override
                public void handleDoubleClick(String key, String value) {
                    log.trace("showInstalledApps: click: {}", key);
                    DeviceManager.getInstance().fetchAppVersion(device, key, version -> {
                        String text = String.format("%s = %s", key, version);
                        DialogHelper.showTextDialog(DeviceScreen.this, key, text);
                    });
                }

                @Override
                public void handleRightClick(String key, String value, JPopupMenu popupMenu) {
                    UiUtils.addPopupMenuItem(popupMenu, "Download App", actionEvent -> {
                        extractApk(device, key);
                    });
                }
            });
        });
    }

    private void extractApk(Device device, String key) {
        String command = "pm path " + key;
        DeviceManager deviceManager = DeviceManager.getInstance();
        deviceManager.runCustomCommand(device, command, (result) -> {
            if (!result.isSuccess) {
                String msg = "Unable to download " + key + "\n\n" + result;
                DialogHelper.showDialog(this, "Error", msg);
                return;
            }
            // download to new folder
            String downloadFolder = Utils.getDownloadFolder();
            File appFolder = new File(downloadFolder, key);
            appFolder.mkdirs();

            for (String path : result.resultList) {
                if (!TextUtils.startsWith(path, PACKAGE_PREFIX)) {
                    log.trace("extractApk: BAD LINE: {}", path);
                    continue;
                }
                path = path.substring(PACKAGE_PREFIX.length());
                int pos = path.lastIndexOf('/');
                if (pos < 1) continue;
                DeviceFile file = new DeviceFile();
                file.name = path.substring(pos + 1);
                path = path.substring(0, pos);

                File saveFile = new File(appFolder, file.name);
                deviceManager.downloadFile(device, path, file, saveFile, (isSuccess, error) -> {
                    log.trace("extractApk: {}: {}", isSuccess, error);
                });
            }
        });
    }

    private void showDeviceProperties(Device device) {
        if (device == null || !device.isOnline) return;
        // fetch all device properties & display
        DeviceManager.getInstance().fetchDeviceProperties(device, (isSuccess, propMap) -> {
            TreeMap<String, String> sortedPropMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            sortedPropMap.putAll(propMap);
            DialogHelper.showListDialog(this, "Device Properties", sortedPropMap, null);
        });
    }

    private void addDeviceDetail(JPanel panel, String label, String value) {
        if (!TextUtils.isEmpty(value)) {
            panel.add(new JLabel(label + ": " + value), "wrap");
        }
    }

    private void handleMirrorCommand(Device selectedDevice) {
        List<Device> selectedDeviceList;
        if (selectedDevice == null) {
            selectedDeviceList = getSelectedDevices(true);
        } else {
            selectedDeviceList = Collections.singletonList(selectedDevice);
        }
        if (selectedDeviceList.isEmpty()) return;
        if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            if (!DialogHelper.showConfirmDialog(this, "Mirror Device", "Mirror " + selectedDeviceList.size() + " devices?"))
                return;
        }

        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size());
        for (Device device : selectedDeviceList) {
            setDeviceBusy(device, true);
            DeviceManager.getInstance().mirrorDevice(device, (isSuccess, error) -> {
                setDeviceBusy(device, false);
                resultWatcher.handleResult(device.getDisplayName(), isSuccess, isSuccess ? null : error);
            });
        }
    }

    private void handleRecordCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            if (!DialogHelper.showConfirmDialog(this, "Record Device", "Record " + selectedDeviceList.size() + " devices?"))
                return;
        }

        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size(), (isSuccess, error) -> {
            if (!isSuccess) {
                DialogHelper.showTextDialog(getRootPane(), "Results", error);
            }
        });
        for (Device device : selectedDeviceList) {
            setDeviceBusy(device, true);
            DeviceManager.getInstance().recordScreen(device, (isSuccess, error) -> {
                setDeviceBusy(device, false);
                resultWatcher.handleResult(device.getDisplayName(), isSuccess, isSuccess ? null : error);
            });
        }
    }

    private Device getFirstSelectedDevice() {
        List<Device> selectedDevices = getSelectedDevices(false);
        if (!selectedDevices.isEmpty()) return selectedDevices.get(0);
        else return null;
    }

    /**
     * get list of selected devices
     * NOTE: if no devices are selected but only 1 device, this will be returned
     *
     * @param showError - true to display error if nothing selected
     */
    private List<Device> getSelectedDevices(boolean showError) {
        List<Device> selectedDeviceList = new ArrayList<>();
        int[] selectedRows = table.getSelectedRows();
        for (int selectedRow : selectedRows) {
            // convert view row to data row (in case user changed sort order)
            int dataRow = table.convertRowIndexToModel(selectedRow);
            Device device = model.getDeviceAtRow(dataRow);
            if (device != null && device.isOnline) selectedDeviceList.add(device);
        }
        if (selectedDeviceList.isEmpty() && model.getRowCount() == 1) {
            Device device = model.getDeviceAtRow(0);
            selectedDeviceList.add(device);
        }
        if (showError && selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
        }
        return selectedDeviceList;
    }

    // configurable toolbar buttons
    public enum ToolbarButton {
        CONNECT(Icons.ADD, "Connect", "Connect Device"),
        BROWSE(Icons.BROWSE, "Browse", "File Explorer"),
        LOGS(Icons.FILE_LOGS, "View Logs", "Log Viewer"),
        SAVE_LOGS(Icons.FILE_SAVE, "Save Logs", "Save Logs to Disk"),
        INPUT(Icons.KEYBOARD, "Input", "Enter text"),
        MIRROR(Icons.MIRROR, "Mirror", "Remote Control / Mirror Device"),
        RECORD(Icons.SCREEN_RECORD, "Record", "Record Device (scrcpy)"),
        SCREENSHOT(Icons.SCREENSHOT, "Screenshot", "Screenshot"),
        INSTALL(Icons.FILE_APK, "Install", "Install / Copy file"),
        RESTART(Icons.RESTART, "Reboot", "Reboot Device"),
        TERMINAL(Icons.TERMINAL, "Terminal", "Open Terminal"),
        ADB(Icons.ADB, "ADB", "Run custom adb command"),
        SCRIPTS(Icons.FILE_SCRIPT, "Scripts", "Run custom scripts"),
        FILTER(Icons.CLEAR_FILTER, "Filter", "Filter devices..."),
        REFRESH(Icons.REFRESH, "Refresh", "Refresh Devices"),
        SERVER(Icons.SERVER, "Server", "Start server to share devices"),
        SETTINGS(Icons.SETTINGS, "Settings", "Settings"),
        ;

        public final Icons icn;
        public final String label;
        public final String tooltip;

        ToolbarButton(Icons icn, String label, String tooltip) {
            this.icn = icn;
            this.label = label;
            this.tooltip = tooltip;
        }

        // Backwards compatibility method
        public String getImage() {
            return icn.getName();
        }

        /**
         * HIDE these icons by default
         */
        public boolean hideByDefault() {
            return switch (this) {
                case SAVE_LOGS, INPUT, RECORD, TERMINAL, RESTART, ADB -> true;
                default -> false;
            };
        }

        /**
         * right-align these toolbar buttons
         */
        public boolean isRightAlign() {
            return switch (this) {
                case FILTER, REFRESH, SERVER, SETTINGS -> true;
                default -> false;
            };
        }

        public static ToolbarButton buttonFromLabel(String label) {
            for (ToolbarButton button : values()) {
                if (button.label.equals(label)) return button;
            }
            return null;
        }
    }

    public void setupToolbar() {
        if (toolbar.getComponentCount() > 0) {
            toolbar.removeAll();
            // TODO: not sure this is necessary
            toolbar.revalidate();
            toolbar.doLayout();
            toolbar.repaint();
        }
        toolbar.setRollover(true);

        // get list of all toolbar buttons in default order
        List<ToolbarButton> toolbarButtons = new ArrayList<>(List.of(ToolbarButton.values()));

        // get and remove hidden toolbar buttons
        List<ToolbarButton> hiddenList = getHiddenToolbarButtons();
        // remove hidden buttons
        toolbarButtons.removeAll(hiddenList);

        // TODO: allow re-ordering toolbar
        // List<ToolbarButton> orderList = getToolbarOrder();

        boolean hasRightAlignButtons = false;
        for (ToolbarButton toolbarButton : toolbarButtons) {
            // check if button should be right-aligned
            if (toolbarButton.isRightAlign() && !hasRightAlignButtons) {
                hasRightAlignButtons = true;
                toolbar.add(Box.createHorizontalGlue());
            }
            // special toobar buttons
            switch (toolbarButton) {
                case FILTER:
                    // not a toolbar button
                    addToolbarFilter();
                    continue;
                case SCRIPTS:
                    // this toolbar button only shows up if a script exists
                    addScriptsToolbarButton();
                    continue;
            }

            createToolbarButton(toolbar, toolbarButton, e -> {
                handleButtonClicked(toolbarButton, e);
            });

            if (toolbarButton == ToolbarButton.CONNECT) toolbar.addSeparator();
            else if (toolbarButton == ToolbarButton.SERVER) updateServerButton();
        }
    }

    private void updateServerButton() {
        // find SERVER toolbar button
        JButton button = getToolbarButton(ToolbarButton.SERVER);
        if (button != null) {
            RemoteServerManager server = DeviceManager.getInstance().getRemoteServerManager();
            BufferedImage image = UiUtils.getImage(ToolbarButton.SERVER.icn, UiUtils.IMG_SIZE_TOOLBAR);
            if (image != null) {
                if (server.isRunning()) {
                    image = UiUtils.replaceColor(image, Colors.COLOR_SERVER_RUNNING);
                }
                button.setIcon(new ImageIcon(image));
            }
        }
    }

    private JButton getToolbarButton(ToolbarButton toolbarButton) {
        for (Component component : toolbar.getComponents()) {
            if (component instanceof JButton button && button.getText().equals(toolbarButton.label)) {
                return button;
            }
        }
        return null;
    }

    private void handleButtonClicked(ToolbarButton toolbarButton, MouseEvent mouseEvent) {
        switch (toolbarButton) {
            case CONNECT -> handleConnectButtonClicked(mouseEvent);
            case BROWSE -> handleBrowseCommand(null);
            case LOGS -> handleViewLogsCommand(null);
            case SAVE_LOGS -> handleSaveLogsCommand();
            case INPUT -> handleInputCommand();
            case MIRROR -> handleMirrorCommand(null);
            case RECORD -> handleRecordCommand();
            case SCREENSHOT -> handleScreenshotCommand();
            case INSTALL -> handleInstallCommand();
            case RESTART -> handleRestartCommand();
            case TERMINAL -> handleTermCommand();
            case ADB -> handleRunCustomCommand();
            case REFRESH -> refreshDevices();
            case SERVER -> {
                ShareServerDialog.showShareServerDialog(this);
                updateServerButton();
            }
            case SETTINGS -> SettingsDialog.showSettings(DeviceScreen.this);
            default -> log.warn("handleButtonClicked: unhandled button: {}", toolbarButton);
        }
    }

    private void addToolbarFilter() {
        filterTextField.setPreferredSize(new Dimension(150, UiUtils.IMG_SIZE_TOOLBAR));
        filterTextField.setMinimumSize(new Dimension(10, UiUtils.IMG_SIZE_TOOLBAR));
        filterTextField.setMaximumSize(new Dimension(200, UiUtils.IMG_SIZE_TOOLBAR));
        UiUtils.addRightClickListener(filterTextField, e -> {
            JPopupMenu popupMenu = new JPopupMenu();
            // hide column
            UiUtils.addPopupMenuItem(popupMenu, "Hide " + ToolbarButton.FILTER.label, Icons.EYE_CLOSED, actionEvent -> {
                popupMenu.setVisible(false);
                hideToobarButton(ToolbarButton.FILTER);
                setupToolbar();
            });
            // manage toolbar
            UiUtils.addPopupMenuItem(popupMenu, "Manage Toolbar", Icons.SETTINGS, actionEvent -> SettingsDialog.showManageToolbar(DeviceScreen.this, DeviceScreen.this));

            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        });
        toolbar.add(filterTextField);
    }

    /**
     * @return list of hidden toolbar buttons
     */
    public List<ToolbarButton> getHiddenToolbarButtons() {
        List<ToolbarButton> hiddenList = getToolbarList(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS);
        if (hiddenList == null) {
            // populate list with default hidden buttons
            hiddenList = new ArrayList<>();
            for (ToolbarButton button : ToolbarButton.values()) {
                if (button.hideByDefault()) hiddenList.add(button);
            }
        }
        return hiddenList;
    }

    private static List<ToolbarButton> getToolbarList(PreferenceUtils.Pref pref) {
        String prefStr = PreferenceUtils.getPreference(pref);
        if (prefStr == null) return null;
        List<String> prefList = GsonHelper.stringToList(prefStr, String.class);
        List<ToolbarButton> list = new ArrayList<>();
        for (String item : prefList) {
            try {
                list.add(ToolbarButton.valueOf(item));
            } catch (IllegalArgumentException e) {
                log.warn("getToolbarList: invalid: {}, {}", item, pref);
            }
        }
        return list;
    }

    /**
     * hide toolbar button
     */
    public void hideToobarButton(ToolbarButton toolbarButton) {
        List<ToolbarButton> hiddenList = getHiddenToolbarButtons();
        hiddenList.add(toolbarButton);
        // convert enum list to string list
        List<String> hiddenListStr = new ArrayList<>();
        for (ToolbarButton button : hiddenList) hiddenListStr.add(button.name());
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, GsonHelper.toJson(hiddenListStr));
    }

    protected JButton createToolbarButton(JToolBar toolbar, ToolbarButton toolbarButton, ClickListener listener) {
        String label = toolbarButton.label;
        String tooltip = toolbarButton.tooltip;

        JButton button = createToolbarButton(toolbar, toolbarButton.icn, label, tooltip, UiUtils.IMG_SIZE_TOOLBAR, listener);
        UiUtils.addRightClickListener(button, e -> {
            if (toolbarButton == ToolbarButton.SETTINGS) return;
            JPopupMenu popupMenu = new JPopupMenu();
            UiUtils.addPopupMenuItem(popupMenu, "Hide " + label, Icons.EYE_CLOSED, actionEvent -> {
                hideToobarButton(toolbarButton);
                setupToolbar();
            });
            UiUtils.addPopupMenuItem(popupMenu, "Manage Toolbar", Icons.SETTINGS, actionEvent -> SettingsDialog.showManageToolbar(DeviceScreen.this, DeviceScreen.this));
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        });

        return button;
    }

    private List<File> getCustomScripts() {
        File customFolder = Utils.getDeviceManagerFolder();
        File[] files = customFolder.listFiles();
        if (files == null) return null;
        List<File> scriptList = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (TextUtils.endsWithAny(name, true, ".sh", ".bat")) {
                scriptList.add(file);
            }
        }
        // sort scriptList alphabetically (case insensitive)
        scriptList.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        return scriptList;
    }

    /**
     * add scripts toolbar button if any custom scripts exist
     */
    private void addScriptsToolbarButton() {
        List<File> scriptList = getCustomScripts();
        if (scriptList == null || scriptList.isEmpty()) return;
        createToolbarButton(toolbar, ToolbarButton.SCRIPTS, e -> {
            JPopupMenu popupMenu = new JPopupMenu();
            List<File> list = getCustomScripts();
            for (File script : list) {
                String name = FileUtils.getNameNoExt(script).replaceAll("_", " ");
                JMenuItem item = new JMenuItem(name, UiUtils.getImageIcon(Icons.FILE_SCRIPT, UiUtils.IMG_SIZE_SMALL));
                item.addActionListener(e2 -> handleCustomScriptClicked(script, name));
                popupMenu.add(item);
            }
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        });
    }

    private void handleCustomScriptClicked(File script, String name) {
        List<Device> selectedDeviceList = getSelectedDevices(false);
        //if (selectedDeviceList.isEmpty()) return;

        log.trace("handleCustomScriptClicked: {}, {}", name, script.getAbsolutePath());

        String[] serialArr = new String[selectedDeviceList.size()];
        for (int i = 0; i < selectedDeviceList.size(); i++) {
            Device device = selectedDeviceList.get(i);
            setDeviceBusy(device, true);
            serialArr[i] = device.serial;
        }

        DeviceManager.getInstance().runCustomScript((isSuccess, error) -> {
            log.trace("handleCustomScriptClicked: DONE:{}, {}", isSuccess, error);
            for (Device device : selectedDeviceList) {
                setDeviceBusy(device, false);
            }
        }, script.getAbsolutePath(), serialArr);
    }

    private void refreshDevices() {
        // refresh local devices
        DeviceManager.getInstance().refreshDevices(true);
        // refresh remote devices
        DeviceManager.getInstance().getRemoteConnectionManager().refreshAllDevices(true);
    }

    private void handleRunCustomCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;

        CommandDialog.showCommandDialog(this, selectedDeviceList);
    }

    private void handleRestartCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;

        // prompt to install/copy
        if (!DialogHelper.showConfirmDialog(this, "Restart", "Restart " + selectedDeviceList.size() + " device(s)?"))
            return;

        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().restartDevice(device, (isSuccess, error) -> refreshDevices());
        }
    }

    private void showSelectDevicesDialog() {
        if (model.getRowCount() > 0) {
            DialogHelper.showDialog(this, "No devices selected", "Select 1 or more devices to use this feature");
        }
    }

    private void filterDevices(String text) {
        // TODO: offer option to switch between filter and search
        // if (sorter != null) sorter.setFilterText(text);
        // required to refresh table & scrollview that contains it
        // table.invalidate();
        model.setSearchText(text);
        refreshUi();
    }

    private void handleInstallCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;

        String downloadFolder = Utils.getDownloadFolder();

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(downloadFolder));
        chooser.setDialogTitle("Select File");
        chooser.setApproveButtonText("OK");
        chooser.setMultiSelectionEnabled(true);

        int rc = chooser.showOpenDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            File[] fileArr = chooser.getSelectedFiles();
            if (fileArr == null || fileArr.length == 0) {
                log.debug("handleInstallCommand: nothing selected");
                return;
            }
            handleFilesDropped(Arrays.asList(fileArr));
        }
    }

    public void handleBrowseCommand(Device selectedDevice) {
        if (selectedDevice == null) selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        ExploreScreen exploreScreen = exploreViewMap.get(selectedDevice.serial);
        if (exploreScreen == null) {
            if (!selectedDevice.isOnline) return;
            exploreScreen = new ExploreScreen(this, selectedDevice);
            exploreViewMap.put(selectedDevice.serial, exploreScreen);
        }
        exploreScreen.show();
    }

    public void handleBrowseClosed(String serial) {
        exploreViewMap.remove(serial);
    }

    public void handleLogsClosed(String serial) {
        logsViewMap.remove(serial);
    }

    public void handleInputClosed(String serial) {
        inputViewMap.remove(serial);
    }

    public void handleSaveLogsClosed() {
        if (saveLogsScreen != null) {
            saveLogsScreen = null;
        }
    }

    public void handleViewLogsCommand(Device selectedDevice) {
        if (selectedDevice == null) selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        ViewLogsScreen logsScreen = logsViewMap.get(selectedDevice.serial);
        if (logsScreen == null) {
            if (!selectedDevice.isOnline) return;
            logsScreen = new ViewLogsScreen(this, selectedDevice);
            logsViewMap.put(selectedDevice.serial, logsScreen);
        }
        logsScreen.setVisible(true);
    }

    private void handleSaveLogsCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;

        if (saveLogsScreen == null) {
            saveLogsScreen = new SaveLogsScreen(this);
        }
        saveLogsScreen.setDeviceList(selectedDeviceList);
        saveLogsScreen.show();
    }

    private interface UpdateListener {
        void onUpdateCheckComplete(String version, String desc);
    }

    private void checkForUpdates(UpdateListener updateListener) {
        // must be run off main/UI thread
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

        // update UI on main thread
        String finalVersion = version;
        String finalDesc = desc;
        if (version != null) {
            log.debug("checkForUpdates: LATEST:{}, CURRENT:{}", version, MainApplication.version);
            SwingUtilities.invokeLater(() -> {
                updateVersion = finalVersion;
                updateDesc = finalDesc;
                updateLabel.setToolTipText("Update Available " + updateVersion + ", desc: " + finalDesc);
                BufferedImage image = UiUtils.getImage(Icons.UPDATE, UiUtils.IMG_SIZE_SMALL, UiUtils.IMG_SIZE_SMALL, Colors.COLOR_ERROR);
                if (image != null) updateLabel.setIcon(new ImageIcon(image));
                updateLabel.setVisible(true);
                if (updateListener != null)
                    updateListener.onUpdateCheckComplete(finalVersion, finalDesc);
            });
        } else if (updateListener != null) {
            SwingUtilities.invokeLater(() -> updateListener.onUpdateCheckComplete(null, null));
        }
    }

    /**
     * show dialog with system properties and environment
     */
    private void showSystemEnvironmentDialog(MouseEvent mouseEvent) {
        // get system environment variables
        Map<String, String> envMap = System.getenv();
        TreeMap<String, String> sortedEnvMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sortedEnvMap.putAll(envMap);

        // add in system properties
        Properties properties = System.getProperties();
        for (Map.Entry<Object, Object> prop : properties.entrySet()) {
            Object key = prop.getKey();
            Object value = prop.getValue();
            if (key != null && value != null) {
                sortedEnvMap.put(key.toString(), value.toString());
            }
        }

        DialogHelper.showListDialog(this, "System Environment", sortedEnvMap, null);
    }

    private void handleUpdateClicked(MouseEvent e) {
        if (updateVersion == null) {
            // prompt to check for new version
            if (!DialogHelper.showConfirmDialog(this, "Update Check", "Check for update?")) return;

            checkForUpdates((version, desc) -> {
                if (updateVersion != null) {
                    handleUpdateClicked(null);
                } else {
                    DialogHelper.showDialog(this, null, "No Updates");
                }
            });
            return;
        }
        // jdeploy will auto-update app on start
        String jdeployPath = System.getProperty("jdeploy.launcher.path");
        boolean isJdeploy = jdeployPath != null;
        int index = TextUtils.indexOf(jdeployPath, "/Contents/MacOS/Client4JLauncher");
        if (index > 0) {
            // remove the launcher part and just open "Android Device Manager.app"
            // "/Users/USERNAME/Applications/Android Device Manager.app/Contents/MacOS/Client4JLauncher";
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
        if (DialogHelper.showCustomDialog(this, panel, "Update Available", choices) != JOptionPane.YES_OPTION) return;

        if (isJdeploy) {
            // exit and restart app
            //  ~/Applications/Android Device Manager.app/Contents/MacOS/Client4JLauncher
            final ArrayList<String> command = new ArrayList<>();
            command.add("open");
            command.add(jdeployPath);

            final ProcessBuilder builder = new ProcessBuilder(command);
            try {
                builder.start();
                System.exit(0);
            } catch (IOException ex) {
                log.error("handleVersionClicked: IOException: {}", ex.getMessage());
            }
        } else {
            // NOTE: check if app was launched from console or other (IntelliJ, .app)
            // log.debug("handleVersionClicked: CONSOLE:{}", System.console());
            Utils.openBrowser(URL_GITHUB);
        }
    }

    private void handleVersionClicked(MouseEvent e) {
        // show logs
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        File logsFile = logger.getFileLog();
        boolean rc = Utils.editFile(logsFile);
        if (!rc) {
            // open failed
            DialogHelper.showDialog(this, "Error", "Failed to open logs: " + logsFile.getAbsolutePath());
        }
    }

}
