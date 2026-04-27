package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.utils.DeviceCellRenderer;
import com.jpage4500.devicemanager.table.utils.DeviceRowSorter;
import com.jpage4500.devicemanager.table.utils.TableColumnAdjuster;
import com.jpage4500.devicemanager.ui.dialog.CommandDialog;
import com.jpage4500.devicemanager.ui.dialog.ConnectDialog;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.ui.dialog.ShareServerDialog;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
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
import java.util.*;
import java.util.List;

/**
 * create and manage device view
 */
public class DeviceScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(DeviceScreen.class);

    private static final String HINT_FILTER_DEVICES = "Search";
    public static final String PREF_KEY_DEVICES = "devices";
    public static final String PACKAGE_PREFIX = "package:";

    public CustomTable table;
    public DeviceTableModel model;
    private DeviceRowSorter sorter;
    public JToolBar toolbar;
    private HintTextField filterTextField;

    // status bar items
    private HoverLabel updateLabel;         // update
    private HoverLabel versionLabel;        // version
    private HoverLabel memoryLabel;
    private JLabel countLabel;             // total devices

    private boolean hasSelectedDevice;

    public DeviceScreen(App app) {
        super(app, null, "main", 900, 300);
        initalizeUi();
    }

    protected void initalizeUi() {
        setTitle("Device Manager");
        JPanel panel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // -- table --
        table = new CustomTable(PREF_KEY_DEVICES);
        setupTable();
        panel.add(table.getScrollPane(), BorderLayout.CENTER);

        // -- statusbar --
        setupStatusBar(panel);

        setupMenuBar();
        setContentPane(panel);

        setVisible(true);

        refreshUi();
        table.requestFocus();
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSING -> closeWindow();
            case DEACTIVATED -> {
                if (app instanceof AppController controller) controller.hideTrayPopup();
            }
        }
    }

    @Override
    public void closeWindow() {
        app.exit(false);
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
        UiUtils.addLeftClickListener(updateLabel, e -> {
            if (app instanceof AppController controller) controller.handleUpdateClicked(this);
        });

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
        JMenu windowMenu = buildWindowMenu();

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

    @Override
    public void toggleToolbar() {
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
            handleMirrorCommand();
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
                UiUtils.addPopupMenuItem(popupMenu, "Hide " + columnType.name(), actionEvent -> handleHideColumn(column));
            }
            UiUtils.addPopupMenuItem(popupMenu, "Size to Fit", actionEvent -> {
                TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 0);
                adjuster.adjustColumn(column);
            });

            popupMenu.addSeparator();

            UiUtils.addPopupMenuItem(popupMenu, "Manage Columns", actionEvent -> SettingsDialog.showManageDeviceColumnsDialog(app, this));

            boolean autoResize = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_DEVICE_AUTO_RESIZE, true);
            String resizeDesc = autoResize ? "ON" : "OFF";
            UiUtils.addPopupMenuItem(popupMenu, "Auto Resize: " + resizeDesc, actionEvent -> {
                boolean update = !autoResize;
                PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_DEVICE_AUTO_RESIZE, update);
                int flag = update ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF;
                table.setAutoResizeMode(flag);
            });
            if (!autoResize) {
                UiUtils.addPopupMenuItem(popupMenu, "Size ALL to Fit", actionEvent -> {
                    TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 0);
                    adjuster.adjustColumns();
                });
            }
            return popupMenu;
        }
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

            UiUtils.addPopupMenuItem(popupMenu, "Copy Field to Clipboard", actionEvent -> handleCopyClipboardFieldCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Copy Line to Clipboard", actionEvent -> handleCopyClipboardCommand());
            popupMenu.addSeparator();
            UiUtils.addPopupMenuItem(popupMenu, "Device Details", actionEvent -> handleDeviceDetails(device));
            UiUtils.addPopupMenuItem(popupMenu, "Mirror Device", actionEvent -> handleMirrorCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Record Device", actionEvent -> handleRecordCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Capture Screenshot", actionEvent -> handleScreenshotCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Restart Device", actionEvent -> handleRestartCommand());
            UiUtils.addPopupMenuItem(popupMenu, "Open Terminal", actionEvent -> handleTermCommand());

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

    public void handleDevicesUpdated(List<Device> deviceList) {
        if (deviceList == null) return;
        model.setDeviceList(deviceList);

        // auto-select first device
        if (!hasSelectedDevice && !deviceList.isEmpty() && table.getSelectedRow() == -1) {
            table.changeSelection(0, 0, false, false);
            hasSelectedDevice = true;
        }

        refreshUi();
        log.trace("handleDevicesUpdated: deviceList: {}", deviceList.size());
    }

    public void handleDeviceUpdated(Device device) {
        model.updateDevice(device);
        sorter.sort();
    }

    public void handleDeviceRemoved(Device device) {
        model.removeDevice(device);
        sorter.sort();
    }

    /** called by AppController when an update is found */
    public void notifyUpdateAvailable(String version, String desc) {
        updateLabel.setToolTipText("Update Available " + version + ", desc: " + desc);
        BufferedImage image = UiUtils.getImage(Icons.UPDATE, UiUtils.IMG_SIZE_SMALL, UiUtils.IMG_SIZE_SMALL, Colors.COLOR_ERROR);
        if (image != null) updateLabel.setIcon(new ImageIcon(image));
        updateLabel.setVisible(true);
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
     * called when user double-clicks on .apk file or selects open with device manager
     * - similar to handleFilesDropped() but waits a bit until a device is connected
     */
    public void handleFilesOpened(List<File> fileList) {
        log.debug("handleFilesOpened: {}", fileList.size());
        if (table.getRowCount() > 0) {
            handleFilesDropped(fileList);
        } else {
            Utils.runDelayed(1000, true, () -> handleFilesDropped(fileList));
        }
    }

    public void handleFilesDropped(List<File> fileList) {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) {
            log.error("handleFilesDropped: no devices! {}", fileList);
            return;
        }
        log.debug("handleFilesDropped: {}, #devices:{}", fileList, selectedDeviceList.size());
        installOrCopyFiles(selectedDeviceList, fileList);
    }

    public void installOrCopyFiles(List<Device> selectedDeviceList, List<File> fileList) {
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
        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size(), (isSuccess, error) -> {

        });
        // TODO(merge): ResultWatcher.setDesc was removed; if a progress dialog comes back, restore description.
        String desc = String.format("Copying %d file(s) to %d device(s)", fileList.size(), selectedDeviceList.size());
        log.debug("copyFiles: {}", desc);
        // TODO: where to put files on device?
        String destFolder = "/sdcard/Download/";
        for (Device device : selectedDeviceList) {
            app.setDeviceBusy(device, true);
            DeviceManager.getInstance().copyFiles(device, fileList, destFolder, (numCompleted, numTotal, msg) -> {
                // TOOD: show progress
            }, (isSuccess, error) -> {
                app.setDeviceBusy(device, false);
                resultWatcher.handleResult(device.serial, isSuccess, error);
            });
        }
    }

    private void installFiles(List<Device> selectedDeviceList, List<File> apkList) {
        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size() * apkList.size(), (isSuccess, error) -> {
            if (isSuccess) {
                // TODO: prompt to open app
                // - requires figuring out the package name from .apk (aapt2?)
            } else {
                DialogHelper.showDialog(this, "Install Failed", error);
            }
        });
        for (Device device : selectedDeviceList) {
            for (File file : apkList) {
                String filename = file.getName();
                app.setDeviceBusy(device, true);
                DeviceManager.getInstance().installApp(device, file, (isSuccess, error) -> {
                    app.setDeviceBusy(device, false);
                    resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? filename : error);
                    if (isSuccess) {
                        // update device details after installing an app
                        DeviceManager.getInstance().fetchDeviceDetails(device, true);
                    }
                });
            }
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

            });
        }
    }

    private void handleInputCommand() {
        Device selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;
        app.showInput(selectedDevice);
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
            app.setDeviceBusy(device, true);
            DeviceManager.getInstance().captureScreenshot(device, image -> {
                app.setDeviceBusy(device, false);
                if (image == null) {
                    resultWatcher.handleResult(device.serial, false, "screenshot failed");
                    return;
                }
                try {
                    String name = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()) + ".png";
                    File outFile = new File(Utils.getDownloadFolder(), name);
                    javax.imageio.ImageIO.write(image, "png", outFile);
                    Utils.openFile(outFile);
                    resultWatcher.handleResult(device.serial, true, null);
                } catch (Exception e) {
                    log.error("handleScreenshotCommand: {}", e.getMessage());
                    resultWatcher.handleResult(device.serial, false, e.getMessage());
                }
            });
        }
    }

    private void handleConnectDevice() {
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
                deviceManager.downloadFile(device, path, file, saveFile, false, (isSuccess, error) -> {
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

    private void handleMirrorCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;
        if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            if (!DialogHelper.showConfirmDialog(this, "Mirror Device", "Mirror " + selectedDeviceList.size() + " devices?"))
                return;
        }

        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size());
        for (Device device : selectedDeviceList) {
            app.setDeviceBusy(device, true);
            DeviceManager.getInstance().mirrorDevice(device, false, (isSuccess, error) -> {
                app.setDeviceBusy(device, false);
                resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? null : error);
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
            app.setDeviceBusy(device, true);
            DeviceManager.getInstance().recordScreen(device, (isSuccess, error) -> {
                app.setDeviceBusy(device, false);
                resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? null : error);
            });
        }
    }

    public Device getFirstSelectedDevice() {
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
        LOGS(Icons.LOGS, "View Logs", "Log Viewer"),
        SAVE_LOGS(Icons.SAVE, "Save Logs", "Save Logs to Disk"),
        INPUT(Icons.KEYBOARD, "Input", "Enter text"),
        MIRROR(Icons.SCRCPY, "Mirror", "Mirror Device (scrcpy)"),
        RECORD(Icons.SCREEN_RECORD, "Record", "Record Device (scrcpy)"),
        SCREENSHOT(Icons.SCREENSHOT, "Screenshot", "Screenshot"),
        INSTALL(Icons.DOWNLOAD, "Install", "Install / Copy file"),
        TERMINAL(Icons.TERMINAL, "Terminal", "Open Terminal"),
        ADB(Icons.ADB, "ADB", "Run custom adb command"),
        SCRIPTS(Icons.SCRIPT, "Scripts", "Run custom scripts"),
        FILTER(null, "Filter", "Filter devices..."),
        REFRESH(Icons.REFRESH, "Refresh", "Refresh Devices"),
        SHARE_SERVER(Icons.SHARE_OFF, "Share", "Share Devices"),
        SETTINGS(Icons.SETTINGS, "Settings", "Settings"),
        ;

        public final Icons image;
        public final String label;
        public final String tooltip;

        ToolbarButton(Icons image, String label, String tooltip) {
            this.image = image;
            this.label = label;
            this.tooltip = tooltip;
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
            toolbar.revalidate();
            toolbar.doLayout();
            toolbar.repaint();
        }

        toolbar.setRollover(true);
        JButton connectBtn = createToolbarButton(toolbar, ToolbarButton.CONNECT, actionEvent -> handleConnectDevice());
        if (connectBtn != null) toolbar.addSeparator();

        JButton browseBtn = createToolbarButton(toolbar, ToolbarButton.BROWSE, actionEvent -> app.showFileBrowser(null));

        JButton viewLogsBtn = createToolbarButton(toolbar, ToolbarButton.LOGS, actionEvent -> app.showLogs(null));

        JButton saveLogsBtn = createToolbarButton(toolbar, ToolbarButton.SAVE_LOGS, actionEvent -> handleSaveLogsCommand());

        JButton inputBtn = createToolbarButton(toolbar, ToolbarButton.INPUT, actionEvent -> handleInputCommand());

        if (browseBtn != null || viewLogsBtn != null || inputBtn != null || saveLogsBtn != null)
            toolbar.addSeparator();

        JButton mirrorBtn = createToolbarButton(toolbar, ToolbarButton.MIRROR, actionEvent -> handleMirrorCommand());

        JButton recordBtn = createToolbarButton(toolbar, ToolbarButton.RECORD, actionEvent -> handleRecordCommand());

        JButton screenBtn = createToolbarButton(toolbar, ToolbarButton.SCREENSHOT, actionEvent -> handleScreenshotCommand());

        JButton installBtn = createToolbarButton(toolbar, ToolbarButton.INSTALL, actionEvent -> handleInstallCommand());
        JButton termBtn = createToolbarButton(toolbar, ToolbarButton.TERMINAL, actionEvent -> handleTermCommand());

        if (mirrorBtn != null || recordBtn != null || screenBtn != null || installBtn != null || termBtn != null)
            toolbar.addSeparator();

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
            UiUtils.addRightClickListener(filterTextField, e -> {
                JPopupMenu popupMenu = new JPopupMenu();
                JMenuItem hideItem = new JMenuItem("Hide " + ToolbarButton.FILTER.label);
                hideItem.addActionListener(actionEvent -> {
                    popupMenu.setVisible(false);
                    SettingsDialog.addHiddenToolbarItem(ToolbarButton.FILTER.label);
                    setupToolbar();
                });
                popupMenu.add(hideItem);
                UiUtils.addPopupMenuItem(popupMenu, "Manage Toolbar", actionEvent -> SettingsDialog.showManageToolbar(app, DeviceScreen.this));
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            });
            toolbar.add(filterTextField);
        }

        createToolbarButton(toolbar, ToolbarButton.REFRESH, actionEvent -> refreshDevices());

        // start/stop server
        JButton serverButton = createToolbarButton(toolbar, ToolbarButton.SHARE_SERVER, actionEvent -> ShareServerDialog.showShareServerDialog(this));
        boolean isServerRunning = DeviceManager.getInstance().getRemoteServerManager().isRunning();
        if (isServerRunning) {
            serverButton.setIcon(UiUtils.getImageIcon(Icons.SHARE_ON, UiUtils.IMG_SIZE_TOOLBAR));
            int numConnected = DeviceManager.getInstance().getRemoteServerManager().getConnectedClients().size();
            if (numConnected > 0) {
                serverButton.setText(String.format("#%d", numConnected));
            }
        }

        createToolbarButton(toolbar, ToolbarButton.SETTINGS, actionEvent -> SettingsDialog.showSettings(app, this));
    }

    protected JButton createToolbarButton(JToolBar toolbar, ToolbarButton toolbarButton, ActionListener listener) {
        if (isToobarHidden(toolbarButton)) return null;

        Icons icn = toolbarButton.image;
        String label = toolbarButton.label;
        String tooltip = toolbarButton.tooltip;

        // adapt ActionListener -> ClickListener for the base helper
        ClickListener clickListener = mouseEvent -> {
            if (listener != null) listener.actionPerformed(new ActionEvent(mouseEvent.getSource(), ActionEvent.ACTION_PERFORMED, null));
        };
        JButton button = createToolbarButton(toolbar, icn, label, tooltip, 40, clickListener);
        UiUtils.addRightClickListener(button, e -> {
            if (toolbarButton == ToolbarButton.SETTINGS) return;
            JPopupMenu popupMenu = new JPopupMenu();
            UiUtils.addPopupMenuItem(popupMenu, "Hide " + label, actionEvent -> {
                SettingsDialog.addHiddenToolbarItem(toolbarButton.label);
                setupToolbar();
            });
            UiUtils.addPopupMenuItem(popupMenu, "Manage Toolbar", actionEvent -> SettingsDialog.showManageToolbar(app, DeviceScreen.this));
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        });

        return button;
    }

    private boolean isToobarHidden(ToolbarButton toolbarButton) {
        List<String> hiddenToolbarList = SettingsDialog.getHiddenToolbarList();
        return hiddenToolbarList.contains(toolbarButton.label);
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
        return scriptList;
    }

    private void loadCustomScripts(JToolBar toolbar) {
        List<File> scriptList = getCustomScripts();
        if (scriptList == null || scriptList.isEmpty()) return;
        JButton scriptButton = createToolbarButton(toolbar, ToolbarButton.SCRIPTS, null);
        if (scriptButton == null) return;
        UiUtils.addLeftClickListener(scriptButton, e -> {
            JPopupMenu popupMenu = new JPopupMenu();
            List<File> list = getCustomScripts();
            for (File script : list) {
                String name = FileUtils.getNameNoExt(script).replaceAll("_", " ");
                JMenuItem item = new JMenuItem(name, UiUtils.getImageIcon(Icons.SCRIPT, UiUtils.IMG_SIZE_SMALL));
                item.addActionListener(e2 -> handleCustomScriptClicked(script, name));
                popupMenu.add(item);
            }
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        });
    }

    private void handleCustomScriptClicked(File script, String name) {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        //if (selectedDeviceList.isEmpty()) return;

        log.trace("handleCustomScriptClicked: {}, {}", name, script.getAbsolutePath());

        String[] serialArr = new String[selectedDeviceList.size()];
        for (int i = 0; i < selectedDeviceList.size(); i++) {
            Device device = selectedDeviceList.get(i);
            app.setDeviceBusy(device, true);
            serialArr[i] = device.serial;
        }

        DeviceManager.getInstance().runCustomScript((isSuccess, error) -> {
            log.trace("handleCustomScriptClicked: DONE:{}, {}", isSuccess, error);
            for (Device device : selectedDeviceList) {
                app.setDeviceBusy(device, false);
            }
        }, script.getAbsolutePath(), serialArr);
    }

    private void refreshDevices() {
        DeviceManager.getInstance().refreshDevices(true);
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

    private void handleSaveLogsCommand() {
        List<Device> selectedDeviceList = getSelectedDevices(true);
        if (selectedDeviceList.isEmpty()) return;
        app.showSaveLogs(selectedDeviceList);
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
