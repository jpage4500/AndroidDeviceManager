package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.GithubRelease;
import com.jpage4500.devicemanager.data.NpmRelease;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.utils.DeviceCellRenderer;
import com.jpage4500.devicemanager.table.utils.DeviceRowSorter;
import com.jpage4500.devicemanager.table.utils.TableColumnAdjuster;
import com.jpage4500.devicemanager.ui.dialog.CommandDialog;
import com.jpage4500.devicemanager.ui.dialog.ConnectDialog;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.ui.views.TrayMenuItem;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * create and manage device view
 */
public class DeviceScreen extends BaseScreen implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceScreen.class);

    private static final String HINT_FILTER_DEVICES = "Filter devices...";
    public static final String SHOW_DEVICE_LIST = "Show Device List";
    public static final String SHOW_BROWSE = "Show File Browser";
    public static final String SHOW_LOG_VIEWER = "Show Device Logs";

    // update check for github releases
    public static final boolean UPDATE_CHECK_GITHUB = true;
    public static final String UPDATE_SOURCE_GITHUB = "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases";
    public static final String URL_GITHUB = "https://github.com/jpage4500/AndroidDeviceManager/releases";

    // update check for npm (which jdeploy uses)
    public static final boolean UPDATE_CHECK_NPM = false;
    public static final String UPDATE_SOURCE_NPM = "https://registry.npmjs.org/android-device-manager/latest";
    public static final String URL_NPM = "https://www.jdeploy.com/~android-device-manager";

    public CustomTable table;
    public DeviceTableModel model;
    private DeviceRowSorter sorter;
    public JToolBar toolbar;
    private HintTextField filterTextField;
    private JPopupMenu trayPopupMenu;

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
    private final Map<String, LogsScreen> logsViewMap = new HashMap<>();
    private final Map<String, InputScreen> inputViewMap = new HashMap<>();

    public DeviceScreen() {
        super("main", 900, 300);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        initalizeUi();

        connectAdbServer();

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
            updateExecutorService.scheduleAtFixedRate(this::checkForUpdates, 5, TimeUnit.HOURS.toSeconds(12), TimeUnit.SECONDS);
        }
    }

    protected void initalizeUi() {
        setTitle("Device Manager");
        JPanel panel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // -- table --
        table = new CustomTable("devices");
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

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
                desktop.setQuitHandler((quitEvent, quitResponse) -> {
                    exitApp(true);
                    quitResponse.performQuit();
                });
            } else {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    exitApp(true);
                }));
            }
        } else {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> exitApp(true)));
        }
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSING -> {
                exitApp(false);
            }
            case DEACTIVATED -> {
                if (trayPopupMenu != null) trayPopupMenu.setVisible(false);
            }
        }
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

        // save positions/sizes of any other open windows (only save position of
        if (!exploreViewMap.isEmpty()) (exploreViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSED);
        if (!logsViewMap.isEmpty()) (logsViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSED);
        if (!inputViewMap.isEmpty()) (inputViewMap.values().iterator().next()).onWindowStateChanged(WindowState.CLOSED);

        DeviceManager.getInstance().handleExit();

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
        ImageIcon icon = UiUtils.getImageIcon("icon_update.png", 15);
        updateLabel = new HoverLabel(icon);
        updateLabel.setVisible(false);
        leftPanel.add(updateLabel);
        UiUtils.addClickListener(updateLabel, this::handleUpdateClicked);

        // version
        versionLabel = new HoverLabel();
        leftPanel.add(versionLabel);
        UiUtils.addClickListener(versionLabel, this::handleVersionClicked);
        versionLabel.setText("v" + MainApplication.version);

        // memory
        icon = UiUtils.getImageIcon("memory.png", 15);
        memoryLabel = new HoverLabel(icon);
        memoryLabel.setBorder(0, 0);
        leftPanel.add(memoryLabel);
        UiUtils.addClickListener(memoryLabel, this::showSystemEnvironmentDialog);

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
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> {
            exitApp(false);
        });

        // [CMD + 2] = show explorer
        createCmdAction(windowMenu, SHOW_BROWSE, KeyEvent.VK_2, e -> handleBrowseCommand(null));

        // [CMD + 3] = show logs
        createCmdAction(windowMenu, SHOW_LOG_VIEWER, KeyEvent.VK_3, e -> handleLogsCommand(null));

        // [CMD + ,] = settings
        createCmdAction(windowMenu, "Settings", KeyEvent.VK_COMMA, e -> handleSettingsClicked());

        // [CMD + T] = hide toolbar
        createCmdAction(windowMenu, "Hide Toolbar", KeyEvent.VK_T, e -> hideToolbar());

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
        createCmdAction(deviceMenu, "Filter", KeyEvent.VK_F, e -> filterTextField.requestFocus());

        // [CMD + N] = connect device
        createCmdAction(deviceMenu, "Connect Device", KeyEvent.VK_N, e -> handleConnectDevice());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(deviceMenu);
        setJMenuBar(menubar);
    }

    private void hideToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    private void setupTable() {
        model = new DeviceTableModel();

        // restore previous settings
        List<String> appList = SettingsDialog.getCustomApps();
        model.setAppList(appList);

        List<String> hiddenColList = SettingsDialog.getHiddenColumnList();
        model.setHiddenColumns(hiddenColList);

        //table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(model);
        table.setDefaultRenderer(Device.class, new DeviceCellRenderer());
        table.setEmptyText("No Connected Devices!");

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
            if (column == DeviceTableModel.Columns.CUSTOM1.ordinal()) {
                // edit custom 1 field
                handleSetProperty(1);
            } else if (column == DeviceTableModel.Columns.CUSTOM2.ordinal()) {
                // edit custom 1 field
                handleSetProperty(2);
            } else {
                handleMirrorCommand();
            }
        });

        // support drag and drop of files IN TO deviceView
        new DropTarget(table, new FileDragAndDropListener(table, this::handleFilesDropped));

        table.setPopupMenuListener((row, column) -> getPopupMenu(row, column));

        table.setTooltipListener((row, col) -> {
            int modelCol = table.convertColumnIndexToModel(col);
            DeviceTableModel.Columns columnType = model.getColumnType(modelCol);
            if (row >= 0 && columnType == DeviceTableModel.Columns.BATTERY) {
                // always show battery level and power status in tooltip
                int modelRow = table.convertRowIndexToModel(row);
                Device device = (Device) model.getValueAt(modelRow, modelCol);
                String tooltip = device.batteryLevel + "%";
                if (device.powerStatus != Device.PowerStatus.POWER_NONE) tooltip += " (" + device.powerStatus + ")";
                return tooltip;
            } else {
                return table.getTextIfTruncated(row, col);
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            refreshUi();
        });

        filterTextField.setupSearch(table);
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
                UiUtils.addPopupMenuItem(popupMenu, "Hide " + columnType.name(), actionEvent -> handleHideColumn(column));
                UiUtils.addPopupMenuItem(popupMenu, "Size to Fit", actionEvent -> {
                    TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 0);
                    adjuster.adjustColumn(column);
                });
                return popupMenu;
            }
            return null;
        }
        Device device = model.getDeviceAtRow(row);
        if (device == null) return null;

        JPopupMenu popupMenu = new JPopupMenu();

        if (device.isOnline) {
            UiUtils.addPopupMenuItem(popupMenu, "Copy Field to Clipboard", actionEvent -> handleCopyClipboardFieldCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Copy Line to Clipboard", actionEvent -> handleCopyClipboardCommand());
            popupMenu.addSeparator();
            UiUtils.addPopupMenuItem(popupMenu, "Device Details", actionEvent -> handleDeviceDetails(device));
            UiUtils.addPopupMenuItem(popupMenu, "Mirror Device", actionEvent -> handleMirrorCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Record Device", actionEvent -> handleRecordCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Capture Screenshot", actionEvent -> handleScreenshotCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Restart Device", actionEvent -> handleRestartCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Open Terminal", actionEvent -> handleTermCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Edit Custom Field 1...", actionEvent -> handleSetProperty(1));
            UiUtils.addPopupMenuItem(popupMenu, "Edit Custom Field 2...", actionEvent -> handleSetProperty(2));

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

    private void setupSystemTray() {
        if (!SystemTray.isSupported()) return;

        BufferedImage image = UiUtils.getImage("system_tray.png", 100, 100);
        TrayIcon trayIcon = new TrayIcon(image, "Android Device Manager");
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                log.trace("mouseClicked: {}", trayPopupMenu != null);
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
            log.error("initializeUI: Exception: {}", e.getMessage());
        }
    }

    private void showSystemTray(MouseEvent e) {
        trayPopupMenu = new JPopupMenu();
        trayPopupMenu.setLocation(e.getX(), e.getY());
        trayPopupMenu.setInvoker(trayPopupMenu);
        List<Device> devices = DeviceManager.getInstance().getDevices();
        if (devices.isEmpty()) {
            UiUtils.addPopupMenuItem(trayPopupMenu, "Open", "icon_open.png", actionEvent -> {
                trayPopupMenu.setVisible(false);
                bringWindowToFront();
            });
        } else {
            for (Device device : devices) {
                addTrayMenuItem(device);
            }
        }
        trayPopupMenu.addSeparator();
        UiUtils.addPopupMenuItem(trayPopupMenu, "Quit", "icon_close.png", actionEvent -> exitApp(true));

        trayPopupMenu.setVisible(true);
    }

    private void connectAdbServer() {
        DeviceManager.getInstance().connectAdbServer(true, this);
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        SwingUtilities.invokeLater(() -> {
            if (deviceList != null) {
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
            }
        });
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        SwingUtilities.invokeLater(() -> {
            model.updateDevice(device);
            updateDeviceState(device);
            sorter.sort();
        });
    }

    @Override
    public void handleDeviceRemoved(Device device) {
        SwingUtilities.invokeLater(() -> {
            model.removeDevice(device);
            updateDeviceState(device);
            sorter.sort();
        });

    }

    @Override
    public void handleException(Exception e) {
        SwingUtilities.invokeLater(() -> {
            Object[] choices = {"Retry", "Cancel"};
            int rc = JOptionPane.showOptionDialog(DeviceScreen.this,
                    "Unable to connect to ADB server. Please check that it's running and re-try"
                    , "ADB Server", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, null);
            if (rc != JOptionPane.YES_OPTION) return;

            connectAdbServer();
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
        if (exploreScreen != null) exploreScreen.updateDeviceState();

        LogsScreen logsScreen = logsViewMap.get(device.serial);
        if (logsScreen != null) logsScreen.updateDeviceState();

        InputScreen inputScreen = inputViewMap.get(device.serial);
        if (inputScreen != null) inputScreen.updateDeviceState();
    }

    private void refreshUi() {
        int selectedRowCount = table.getSelectedRowCount();
        int rowCount = table.getRowCount();
        String filterText = filterTextField.getCleanText();
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

        // badge number
        if (Taskbar.isTaskbarSupported()) {
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
                log.error("refreshUi: Exception: {}", e.getMessage());
            }
        }
    }

    private void addTrayMenuItem(Device device) {
        BufferedImage image = UiUtils.getImage("device_status.png", 20, 20);
        if (device.isOnline) {
            image = UiUtils.replaceColor(image, new Color(24, 134, 0));
        }

        TrayMenuItem item = new TrayMenuItem(device.getDisplayName(), new ImageIcon(image));
        item.addButton("Browse", actionEvent -> {
            trayPopupMenu.setVisible(false);
            handleBrowseCommand(device);
        });
        item.addButton("Logs", actionEvent -> {
            trayPopupMenu.setVisible(false);
            handleLogsCommand(device);
        });
        item.addActionListener(e2 -> {
            trayPopupMenu.setVisible(false);
            trayDeviceClicked(device);
        });
        trayPopupMenu.add(item);
    }

    private void trayDeviceClicked(Device device) {
        log.debug("trayDeviceClicked: {}", device.getDisplayName());
        bringWindowToFront();
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

        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        if (table.getSelectedColumn() < 0) return;

        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (!sb.isEmpty()) sb.append("\n");
            String value = model.deviceValue(device, table.getSelectedColumn());
            sb.append(value != null ? value : "");
        }
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private void handleCopyClipboardCommand() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (sb.length() > 0) sb.append("\n");
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
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        } else if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(this, "Open Terminal for " + selectedDeviceList.size() + " devices?", "Open Terminal", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;
        }
        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().openTerminal(device, (isSuccess, error) -> {

            });
        }
    }

    /**
     * called when user double-clicks on .apk file or selects open with device manager
     * - similar to handleFilesDropped() but waits a bit until a device is connected
     */
    public void handleFilesOpened(List<File> fileList) {
        log.debug("handleFilesOpened: {}", fileList.size());
        if (table.getRowCount() > 0) {
            handleFilesDropped(fileList);
        } else {
            Utils.runDelayed(1000, true, () -> {
                handleFilesDropped(fileList);
            });
        }
    }

    public void handleFilesDropped(List<File> fileList) {
        log.debug("handleFilesDropped: {}", fileList.size());
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }
        installOrCopyFiles(selectedDeviceList, fileList, null);
    }

    public void installOrCopyFiles(List<Device> selectedDeviceList, List<File> fileList, DeviceManager.TaskListener listener) {
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
        int rc = JOptionPane.showConfirmDialog(dialog, msg, title, JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;
        if (isInstall) {
            installFiles(selectedDeviceList, fileList, listener);
        } else {
            copyFiles(selectedDeviceList, fileList, listener);
        }
    }

    private void copyFiles(List<Device> selectedDeviceList, List<File> fileList, DeviceManager.TaskListener listener) {
        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size(), listener);
        String desc = String.format("Copying %d file(s) to %d device(s)", fileList.size(), selectedDeviceList.size());
        resultWatcher.setDesc(desc);
        // TODO: where to put files on device?
        String destFolder = "/sdcard/Download/";
        for (Device device : selectedDeviceList) {
            setDeviceBusy(device, true);
            DeviceManager.getInstance().copyFiles(device, fileList, destFolder, (numCompleted, numTotal, msg) -> {
                // TOOD: show progress
            }, (isSuccess, error) -> {
                setDeviceBusy(device, false);
                resultWatcher.handleResult(device.serial, isSuccess, error);
            });
        }
    }

    private void installFiles(List<Device> selectedDeviceList, List<File> apkList, DeviceManager.TaskListener listener) {
        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size() * apkList.size(), listener);
        for (Device device : selectedDeviceList) {
            for (File file : apkList) {
                String filename = file.getName();
                setDeviceBusy(device, true);
                DeviceManager.getInstance().installApp(device, file, (isSuccess, error) -> {
                    setDeviceBusy(device, false);
                    resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? filename : error);
                });
            }
        }
    }

    /**
     * set device property
     * uses "persist.dm.custom[number]" for key and prompts user for value
     */
    private void handleSetProperty(int number) {
        List<Device> selectedDeviceList = getSelectedDevices();
        String customValue = "";
        String message;
        if (selectedDeviceList.size() == 1) {
            Device device = selectedDeviceList.get(0);
            customValue = device.getCustomProperty(Device.CUSTOM_PROP_X + number);
            message = "Enter Custom Note";
        } else {
            message = "Enter Custom Note for " + selectedDeviceList.size() + " devices";
        }

        String result = (String) JOptionPane.showInputDialog(this,
                message,
                "Custom Note (" + number + ")",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                customValue);
        // allow empty input to go through (clear current value)
        if (result == null) return;

        for (Device device : selectedDeviceList) {
            String prop = "custom" + number;
            DeviceManager.getInstance().setProperty(device, prop, result, (isSuccess, error) -> {

            });
            device.setCustomProperty(Device.CUSTOM_PROP_X + number, result);
            model.updateDevice(device);
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
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        } else if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(this, "Take screenshot of " + selectedDeviceList.size() + " devices?", "Screenshot", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;
        }
        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size());
        for (Device device : selectedDeviceList) {
            setDeviceBusy(device, true);
            DeviceManager.getInstance().captureScreenshot(device, (isSuccess, error) -> {
                setDeviceBusy(device, false);
                resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? null : error);
            });
        }
    }

    public void setDeviceBusy(Device device, boolean isBusy) {
        if (isBusy) {
            int busyCounter = device.busyCounter.incrementAndGet();
            // check if already busy (ie: no change)
            if (busyCounter > 1) return;
        } else {
            int busyCounter = device.busyCounter.decrementAndGet();
            // check if still busy (ie: no change)
            if (busyCounter > 0) return;
        }

        if (SwingUtilities.isEventDispatchThread()) {
            model.updateDevice(device);
        } else SwingUtilities.invokeLater(() -> model.updateDevice(device));
    }

    private void handleConnectDevice() {
        ConnectDialog.showConnectDialog(this, (isSuccess, error) -> {
            log.debug("handleConnectDevice: {}", isSuccess);
            if (!isSuccess) {
                JOptionPane.showMessageDialog(this, "Unable to connect!\n\nCheck if the device is showing an prompt to authorize");
            }
        });
    }

    private void handleDisconnect(Device device) {
        DeviceManager.getInstance().disconnectDevice(device.serial, (isSuccess, error) -> {
            if (!isSuccess) JOptionPane.showMessageDialog(this, "Unable to disconnect!");
        });
    }

    private void handleRemoveDevice(Device device) {
        log.debug("handleRemoveDevice: {}", device.serial);
        model.removeDevice(device);
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
            if (!isSuccess) JOptionPane.showMessageDialog(this, "Unable to connect!");
        });
    }

    private void handleDeviceDetails(Device device) {
        if (device == null) return;

        JPanel panel = new JPanel(new MigLayout());
        addDeviceDetail(panel, "Serial", device.serial);
        addDeviceDetail(panel, "Nickname", device.nickname);
        addDeviceDetail(panel, "Model", device.getProperty(Device.PROP_MODEL));
        addDeviceDetail(panel, "Phone", device.phone);
        addDeviceDetail(panel, "IMEI", device.imei);
        addDeviceDetail(panel, "Carrier", device.getProperty(Device.PROP_CARRIER));
        addDeviceDetail(panel, "OS", device.getProperty(Device.PROP_OS));
        addDeviceDetail(panel, "SDK", device.getProperty(Device.PROP_SDK));
        addDeviceDetail(panel, "Free Space", FileUtils.bytesToDisplayString(device.freeSpace));
        addDeviceDetail(panel, "Custom1", device.getCustomProperty(Device.CUST_PROP_1));
        addDeviceDetail(panel, "Custom2", device.getCustomProperty(Device.CUST_PROP_2));

        // device properties
        ImageIcon icon = UiUtils.getImageIcon("arrow_right.png", 15);
        if (device.propMap != null) {
            HoverLabel devicePropLabel = new HoverLabel("Device Properties", icon);
            UiUtils.addClickListener(devicePropLabel, mouseEvent -> showDeviceProperties(device));
            panel.add(devicePropLabel, "wrap");
        }

        HoverLabel appsLabel = new HoverLabel("Installed Apps / Versions", icon);
        UiUtils.addClickListener(appsLabel, mouseEvent -> showInstalledApps(device));
        panel.add(appsLabel, "wrap");

        JOptionPane.showOptionDialog(this, panel, "Device Info", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
    }

    private void showInstalledApps(Device device) {
        if (device == null) return;
        DeviceManager.getInstance().getInstalledApps(device, appSet -> {
            final Map<String, String> appMep = new TreeMap<>();
            // convert set to map
            for (String app : appSet) appMep.put(app, null);
            DialogHelper.showListDialog(this, "Installed Apps", appMep, (key, value) -> {
                log.trace("showInstalledApps: click: {}", key);
                DeviceManager.getInstance().fetchAppVersion(device, key, version -> {
                    String text = String.format("%s = %s", key, version);
                    DialogHelper.showTextDialog(this, key, text);
                });
            });
        });
    }

    private void showDeviceProperties(Device device) {
        if (device == null || device.propMap == null) return;
        DialogHelper.showListDialog(this, "Device Properties", device.propMap, null);
    }

    private void addDeviceDetail(JPanel panel, String label, String value) {
        if (!TextUtils.isEmpty(value)) {
            panel.add(new JLabel(label + ": " + value), "wrap");
        }
    }

    private void handleMirrorCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        } else if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(this,
                    "Mirror " + selectedDeviceList.size() + " devices?",
                    "Mirror Device",
                    JOptionPane.YES_NO_OPTION
            );
            if (rc != JOptionPane.YES_OPTION) return;
        }

        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size());
        for (Device device : selectedDeviceList) {
            setDeviceBusy(device, true);
            DeviceManager.getInstance().mirrorDevice(device, (isSuccess, error) -> {
                setDeviceBusy(device, false);
                resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? null : error);
            });
        }
    }

    private void handleRecordCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        } else if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(this,
                    "Record " + selectedDeviceList.size() + " devices?",
                    "Record Device",
                    JOptionPane.YES_NO_OPTION
            );
            if (rc != JOptionPane.YES_OPTION) return;
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
                resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? null : error);
            });
        }
    }

    private Device getFirstSelectedDevice() {
        List<Device> selectedDevices = getSelectedDevices();
        if (!selectedDevices.isEmpty()) return selectedDevices.get(0);
        else return null;
    }

    private List<Device> getSelectedDevices() {
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
        return selectedDeviceList;
    }

    // configurable toolbar buttons
    public enum ToolbarButton {
        CONNECT("icon_add.png", "Connect", "Connect Device"),
        BROWSE("icon_browse.png", "Browse", "File Explorer"),
        LOGS("icon_logs.png", "Logs", "Log Viewer"),
        INPUT("keyboard.png", "Input", "Enter text"),
        MIRROR("icon_scrcpy.png", "Mirror", "Mirror Device (scrcpy)"),
        RECORD("record.png", "Record", "Record Device (scrcpy)"),
        SCREENSHOT("icon_screenshot.png", "Screenshot", "Screenshot"),
        INSTALL("icon_install.png", "Install", "Install / Copy file"),
        TERMINAL("icon_terminal.png", "Terminal", "Open Terminal"),
        ADB("icon_adb.png", "ADB", "Run custom adb command"),
        SCRIPTS("icon_custom.png", "Scripts", "Run custom scripts"),
        FILTER(null, "Filter", "Filter devices..."),
        REFRESH("icon_refresh.png", "Refresh", "Refresh Devices"),
        SETTINGS("icon_settings.png", "Settings", "Settings"),
        ;

        public final String image;
        public final String label;
        public final String tooltip;

        ToolbarButton(String image, String label, String tooltip) {
            this.image = image;
            this.label = label;
            this.tooltip = tooltip;
        }
    }

    public void setupToolbar() {
        if (toolbar.getComponentCount() > 0) {
            toolbar.removeAll();
            toolbar.revalidate();
            toolbar.doLayout();
            toolbar.repaint();
        }

        toolbar.setRollover(true);
        JButton connectBtn = createToolbarButton(toolbar, ToolbarButton.CONNECT, actionEvent -> handleConnectDevice());
        if (connectBtn != null) toolbar.addSeparator();

        JButton browseBtn = createToolbarButton(toolbar, ToolbarButton.BROWSE, actionEvent -> handleBrowseCommand(null));

        JButton logsBtn = createToolbarButton(toolbar, ToolbarButton.LOGS, actionEvent -> handleLogsCommand(null));

        JButton inputBtn = createToolbarButton(toolbar, ToolbarButton.INPUT, actionEvent -> handleInputCommand());

        if (browseBtn != null || logsBtn != null || inputBtn != null) toolbar.addSeparator();

        JButton mirrorBtn = createToolbarButton(toolbar, ToolbarButton.MIRROR, actionEvent -> handleMirrorCommand());

        JButton recordBtn = createToolbarButton(toolbar, ToolbarButton.RECORD, actionEvent -> handleRecordCommand());

        JButton screenBtn = createToolbarButton(toolbar, ToolbarButton.SCREENSHOT, actionEvent -> handleScreenshotCommand());

        JButton installBtn = createToolbarButton(toolbar, ToolbarButton.INSTALL, actionEvent -> handleInstallCommand());
        JButton termBtn = createToolbarButton(toolbar, ToolbarButton.TERMINAL, actionEvent -> handleTermCommand());

        if (mirrorBtn != null || recordBtn != null || screenBtn != null || installBtn != null || termBtn != null) toolbar.addSeparator();

        // create custom action buttons
        createToolbarButton(toolbar, ToolbarButton.ADB, actionEvent -> handleRunCustomCommand());

        loadCustomScripts(toolbar);

        // -- right side toolbar buttons --

        toolbar.add(Box.createHorizontalGlue());

        filterTextField = new HintTextField(HINT_FILTER_DEVICES, this::filterDevices);
        if (!isToobarHidden(ToolbarButton.FILTER)) {
            filterTextField.setPreferredSize(new Dimension(150, 40));
            filterTextField.setMinimumSize(new Dimension(10, 40));
            filterTextField.setMaximumSize(new Dimension(200, 40));
            filterTextField.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        JPopupMenu popupMenu = new JPopupMenu();
                        JMenuItem hideItem = new JMenuItem("Hide " + ToolbarButton.FILTER.label);
                        hideItem.addActionListener(actionEvent -> {
                            popupMenu.setVisible(false);
                            SettingsDialog.addHiddenToolbarItem(ToolbarButton.FILTER.label);
                            setupToolbar();
                        });
                        popupMenu.add(hideItem);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            });
            toolbar.add(filterTextField);
        }

        createToolbarButton(toolbar, ToolbarButton.REFRESH, actionEvent -> refreshDevices());
        createToolbarButton(toolbar, ToolbarButton.SETTINGS, actionEvent -> handleSettingsClicked());
    }

    protected JButton createToolbarButton(JToolBar toolbar, ToolbarButton toolbarButton, ActionListener listener) {
        if (isToobarHidden(toolbarButton)) return null;

        String imageName = toolbarButton.image;
        String label = toolbarButton.label;
        String tooltip = toolbarButton.tooltip;

        JButton button = createToolbarButton(toolbar, imageName, label, tooltip, 40, listener);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) && toolbarButton != ToolbarButton.SETTINGS) {
                    JPopupMenu popupMenu = new JPopupMenu();
                    UiUtils.addPopupMenuItem(popupMenu, "Hide " + label, actionEvent -> {
                        SettingsDialog.addHiddenToolbarItem(toolbarButton.label);
                        setupToolbar();
                    });
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        return button;
    }

    private boolean isToobarHidden(ToolbarButton toolbarButton) {
        List<String> hiddenToolbarList = SettingsDialog.getHiddenToolbarList();
        return hiddenToolbarList.contains(toolbarButton.label);
    }

    private List<File> getCustomScripts() {
        String homeFolder = Utils.getUserHomeFolder();
        File customFolder = new File(homeFolder, ".device_manager");
        if (!customFolder.exists()) return null;
        File[] files = customFolder.listFiles();
        if (files == null) return null;
        List<File> scriptList = new ArrayList<>();
        for (File file : files) {
            String name = file.getName();
            if (TextUtils.endsWithAny(name, true, ".sh", ".bat")) {
                scriptList.add(file);
            }
        }
        return scriptList;
    }

    private void loadCustomScripts(JToolBar toolbar) {
        List<File> scriptList = getCustomScripts();
        if (scriptList == null || scriptList.isEmpty()) return;
        JButton scriptButton = createToolbarButton(toolbar, ToolbarButton.SCRIPTS, null);
        if (scriptButton == null) return;
        scriptButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JPopupMenu popupMenu = new JPopupMenu();
                List<File> scriptList = getCustomScripts();
                for (File script : scriptList) {
                    String name = FileUtils.getNameNoExt(script);
                    JMenuItem item = new JMenuItem(name, UiUtils.getImageIcon("icon_custom.png", 15));
                    item.addActionListener(e2 -> handleCustomScriptClicked(script, name));
                    popupMenu.add(item);
                }
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    private void handleCustomScriptClicked(File script, String name) {
        log.trace("loadCustomScripts: clicked: {}", name);
        List<String> serialList = new ArrayList<>();
        for (Device device : getSelectedDevices()) serialList.add(device.serial);

        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), 1);
        resultWatcher.setDesc("Running script \"" + name + "\" with " + serialList.size() + " device(s)");
        DeviceManager.getInstance().runCustomScript((isSuccess, error) -> {
            log.trace("mousePressed: DONE:{}, {}", isSuccess, error);
            resultWatcher.handleResult(null, isSuccess, error);
        }, script.getAbsolutePath(), serialList.toArray(new String[0]));
    }

    private void handleSettingsClicked() {
        SettingsDialog.showSettings(this);
    }

    private void refreshDevices() {
        DeviceManager.getInstance().refreshDevices(this);
    }

    private void handleRunCustomCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        CommandDialog.showCommandDialog(this, selectedDeviceList);
    }

    private void handleRestartCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        // prompt to install/copy
        int rc = JOptionPane.showConfirmDialog(this,
                "Restart " + selectedDeviceList.size() + " device(s)?",
                "Restart devices?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().restartDevice(device, (isSuccess, error) -> refreshDevices());
        }
    }

    private void showSelectDevicesDialog() {
        if (model.getRowCount() > 0) {
            JOptionPane.showConfirmDialog(this, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
        }
    }

    private void filterDevices(String text) {
        if (sorter != null) sorter.setFilterText(text);
        refreshUi();
    }

    private void handleInstallCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

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

    public void handleLogsCommand(Device selectedDevice) {
        if (selectedDevice == null) selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        LogsScreen logsScreen = logsViewMap.get(selectedDevice.serial);
        if (logsScreen == null) {
            if (!selectedDevice.isOnline) return;
            logsScreen = new LogsScreen(this, selectedDevice);
            logsViewMap.put(selectedDevice.serial, logsScreen);
        }
        logsScreen.show();
    }

    private void checkForUpdates() {
        // must be run off main/UI thread
        if (SwingUtilities.isEventDispatchThread()) {
            Utils.runBackground(this::checkForUpdates);
            return;
        }
        String version = null;
        String desc = null;
        if (UPDATE_CHECK_GITHUB) {
            String response = NetworkUtils.getRequest(UPDATE_SOURCE_GITHUB);
            List<GithubRelease> releases = GsonHelper.stringToList(response, GithubRelease.class);
            if (!releases.isEmpty()) {
                GithubRelease latestRelease = releases.get(0);
                Utils.CompareResult compareResult = Utils.compareVersion(MainApplication.version, latestRelease.tagName);
                if (compareResult == Utils.CompareResult.VERSION_NEWER) {
                    version = latestRelease.tagName;
                    desc = latestRelease.body;
                }
            }
        } else if (UPDATE_CHECK_NPM) {
            // use npm to check for updates
            String response = NetworkUtils.getRequest(UPDATE_SOURCE_NPM);
            NpmRelease npmRelease = GsonHelper.fromJson(response, NpmRelease.class);
            if (npmRelease != null) {
                Utils.CompareResult compareResult = Utils.compareVersion(MainApplication.version, npmRelease.version);
                if (compareResult == Utils.CompareResult.VERSION_NEWER) {
                    version = npmRelease.version;
                }
            }
        }

        if (version != null) {
            log.debug("checkForUpdates: LATEST:{}, CURRENT:{} ({})", version, MainApplication.version, (UPDATE_CHECK_GITHUB ? "github" : (UPDATE_CHECK_NPM ? "npm" : null)));
            // update UI on main thread
            String finalVersion = version;
            String finalDesc = desc;
            SwingUtilities.invokeLater(() -> {
                updateVersion = finalVersion;
                updateDesc = finalDesc;
                updateLabel.setToolTipText("Update Available " + updateVersion + ", desc: " + finalDesc);
                updateLabel.setVisible(true);
            });
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
        if (updateVersion == null) return;
        // Jdeploy will auto-update app on start
        String jdeployPath = System.getProperty("jdeploy.launcher.path");
        boolean isJdeploy = jdeployPath != null;
        int index = TextUtils.indexOf(jdeployPath, "/Contents/MacOS/Client4JLauncher");
        if (index > 0) {
            // remove the launcher part and just open "Android Device Manager.app"
            // "/Users/USERNAME/Applications/Android Device Manager.app/Contents/MacOS/Client4JLauncher";
            jdeployPath = jdeployPath.substring(0, index);
        }

        String msg = String.format("Update %s Available\n\n%s", updateVersion, updateDesc);
        String yesOption;
        if (isJdeploy) {
            msg += "\n\nRestart app?";
            yesOption = "Restart";
        } else {
            msg += "\n\nView release in browser?";
            yesOption = "View";
        }
        Object[] choices = {yesOption, "Cancel"};
        int rc = JOptionPane.showOptionDialog(DeviceScreen.this,
                msg, "Update Available", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, choices, null);
        if (rc != JOptionPane.YES_OPTION) return;

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
            if (UPDATE_CHECK_NPM) {
                Utils.openBrowser(URL_NPM);
            } else if (UPDATE_CHECK_GITHUB) {
                Utils.openBrowser(URL_GITHUB);
            }
        }
    }

    private void handleVersionClicked(MouseEvent e) {
        // show logs
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        File logsFile = logger.getFileLog();
        boolean rc = Utils.editFile(logsFile);
        if (!rc) {
            // open failed
            JOptionPane.showConfirmDialog(this, "Failed to open logs: " + logsFile.getAbsolutePath(), "Error", JOptionPane.DEFAULT_OPTION);
        }
    }

}
