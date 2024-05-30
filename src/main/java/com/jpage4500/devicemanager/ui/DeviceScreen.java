package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.table.utils.DeviceCellRenderer;
import com.jpage4500.devicemanager.table.utils.DeviceRowSorter;
import com.jpage4500.devicemanager.ui.dialog.CommandDialog;
import com.jpage4500.devicemanager.ui.dialog.ConnectDialog;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.EmptyView;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public class DeviceScreen extends BaseScreen implements DeviceManager.DeviceListener, CustomTable.TableListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceScreen.class);

    private static final String HINT_FILTER_DEVICES = "Filter devices...";

    public CustomTable table;
    public DeviceTableModel model;
    private DeviceRowSorter sorter;
    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;
    private HintTextField filterTextField;

    private final List<JButton> deviceButtonList = new ArrayList<>();

    // open windows (per device)
    private final Map<String, ExploreScreen> exploreViewMap = new HashMap<>();
    private final Map<String, LogsScreen> logsViewMap = new HashMap<>();
    private final Map<String, InputScreen> inputViewMap = new HashMap<>();

    public DeviceScreen() {
        super("main");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initalizeUi();

        connectAdbServer();
    }

    private void connectAdbServer() {
        DeviceManager.getInstance().connectAdbServer(this);
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        SwingUtilities.invokeLater(() -> {
            if (deviceList != null) {
                model.setDeviceList(deviceList);

                // auto-select first device
//                if (!deviceList.isEmpty() && table.getSelectedRow() == -1) {
//                    table.changeSelection(0, 0, false, false);
//                }

                refreshUi();

                for (Device device : deviceList) {
                    updateDeviceState(device);
                }
            }
            updateVersionLabel();
        });
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        SwingUtilities.invokeLater(() -> {
            model.updateRowForDevice(device);
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

    protected void initalizeUi() {
        setTitle("Device Manager");
        JPanel panel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // -- table --
        table = new CustomTable("devices", this);
        setupTable();
        panel.add(table.getScrollPane(), BorderLayout.CENTER);

        // -- statusbar --
        statusBar = new StatusBar();
        statusBar.setLeftLabelListener(this::handleVersionClicked);
        panel.add(statusBar, BorderLayout.SOUTH);
        updateVersionLabel();

        setupMenuBar();
        setupSystemTray();
        setContentPane(panel);
        setVisible(true);

        // -- empty view --
        // NOTE: must be done after setContentPane() above
        JRootPane rootPane = SwingUtilities.getRootPane(table);
        emptyView = new EmptyView("No Android Devices!");
        rootPane.setGlassPane(emptyView);
        emptyView.setOpaque(false);
        emptyView.setVisible(true);

        table.requestFocus();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            //log.debug("initializeUI: EXIT");
            table.persist();
            DeviceManager.getInstance().handleExit();
        }));
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> {
            setVisible(false);
            dispose();
            System.exit(0);
        });

        // [CMD + 2] = show explorer
        createCmdAction(windowMenu, "Browse Files", KeyEvent.VK_2, e -> {
            handleBrowseCommand();
        });

        // [CMD + 3] = show logs
        createCmdAction(windowMenu, "View Logs", KeyEvent.VK_3, e -> {
            handleLogsCommand();
        });

        // [CMD + T] = hide toolbar
        createCmdAction(windowMenu, "Hide Toolbar", KeyEvent.VK_T, e -> {
            hideToolbar();
        });

        JMenu deviceMenu = new JMenu("Devices");

        // [CMD + F] = focus search box
        createCmdAction(deviceMenu, "Filter", KeyEvent.VK_F, e -> {
            filterTextField.requestFocus();
        });

        // [CMD + N] = connect device
        createCmdAction(deviceMenu, "Connect Device", KeyEvent.VK_N, e -> {
            handleConnectDevice();
        });

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(deviceMenu);
        setJMenuBar(menubar);
    }

    private void hideToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    private void setupTable() {
        table.setShowTooltips(true);
        model = new DeviceTableModel();

        // restore previous settings
        List<String> appList = SettingsDialog.getCustomApps();
        model.setAppList(appList);

        List<String> hiddenColList = SettingsDialog.getHiddenColumnList();
        model.setHiddenColumns(hiddenColList);

        //table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(model);
        table.setDefaultRenderer(Device.class, new DeviceCellRenderer());

        // default column sizes
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(DeviceTableModel.Columns.NAME.ordinal()).setPreferredWidth(185);
        columnModel.getColumn(DeviceTableModel.Columns.SERIAL.ordinal()).setPreferredWidth(152);
        columnModel.getColumn(DeviceTableModel.Columns.PHONE.ordinal()).setPreferredWidth(116);
        columnModel.getColumn(DeviceTableModel.Columns.IMEI.ordinal()).setPreferredWidth(147);
        columnModel.getColumn(DeviceTableModel.Columns.BATTERY.ordinal()).setPreferredWidth(31);
        columnModel.getColumn(DeviceTableModel.Columns.BATTERY.ordinal()).setMaxWidth(31);
        columnModel.getColumn(DeviceTableModel.Columns.FREE.ordinal()).setPreferredWidth(66);

        sorter = new DeviceRowSorter(model);
        table.setRowSorter(sorter);

        // support drag and drop of files IN TO deviceView
        new DropTarget(table, new FileDragAndDropListener(table, this::handleFilesDropped));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            refreshUi();
        });
    }

    private void setupSystemTray() {
        TrayIcon trayIcon;
        if (SystemTray.isSupported()) {
            // get the SystemTray instance
            SystemTray tray = SystemTray.getSystemTray();

            BufferedImage image = UiUtils.getImage("android.png", 40, 40);
            PopupMenu popup = new PopupMenu();
            MenuItem openItem = new MenuItem("Open");
            openItem.addActionListener(e2 -> {
                bringWindowToFront();
            });
            MenuItem quitItem = new MenuItem("Quit");
            quitItem.addActionListener(e2 -> {
                System.exit(0);
            });
            popup.add(openItem);
            popup.add(quitItem);
            trayIcon = new TrayIcon(image, "Android Device Manager", popup);
            trayIcon.setImageAutoSize(true);
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                log.error("initializeUI: Exception: {}", e.getMessage());
            }
        }
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
                Utils.runDelayed(300, true, () -> {
                    setState(JFrame.NORMAL);
                });
            });
        });
    }

    private void updateDeviceState(Device device) {
        ExploreScreen exploreScreen = exploreViewMap.get(device.serial);
        if (exploreScreen != null) {
            exploreScreen.updateDeviceState();
        }

        LogsScreen logsScreen = logsViewMap.get(device.serial);
        if (logsScreen != null) {
            logsScreen.updateDeviceState();
        }
    }

    private void refreshUi() {
        int selectedRowCount = table.getSelectedRowCount();
        int rowCount = table.getRowCount();
        String filterText = filterTextField.getCleanText();
        if (TextUtils.notEmpty(filterText)) {
            int totalDevices = model.getRowCount();
            statusBar.setRightLabel("found: " + rowCount + " / " + totalDevices);
        } else if (selectedRowCount > 0) {
            statusBar.setRightLabel("selected: " + selectedRowCount + " / " + rowCount);
        } else {
            statusBar.setRightLabel("total: " + rowCount);
        }
        emptyView.setVisible(rowCount == 0);

        int numSelected = table.getSelectedRowCount();
        for (JButton button : deviceButtonList) {
            button.setEnabled(numSelected > 0);
        }
    }

    private void updateVersionLabel() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        String memUsage = FileUtils.bytesToDisplayString(usedMemory);

        statusBar.setLeftLabel("v" + MainApplication.version + " / " + memUsage);
    }

    @Override
    public void showPopupMenu(int row, int column, MouseEvent e) {
        Device device = model.getDeviceAtRow(row);
        if (device == null) return;

        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem copyFieldItem = new JMenuItem("Copy Field to Clipboard");
        copyFieldItem.addActionListener(actionEvent -> handleCopyClipboardFieldCommand());
        popupMenu.add(copyFieldItem);

        JMenuItem copyItem = new JMenuItem("Copy Line to Clipboard");
        copyItem.addActionListener(actionEvent -> handleCopyClipboardCommand());
        popupMenu.add(copyItem);

        popupMenu.addSeparator();

        JMenuItem detailsItem = new JMenuItem("Device Details");
        detailsItem.addActionListener(actionEvent -> handleDeviceDetails());
        popupMenu.add(detailsItem);

        JMenuItem mirrorItem = new JMenuItem("Mirror Device");
        mirrorItem.addActionListener(actionEvent -> handleMirrorCommand());
        popupMenu.add(mirrorItem);

        JMenuItem screenshotItem = new JMenuItem("Capture Screenshot");
        screenshotItem.addActionListener(actionEvent -> handleScreenshotCommand());
        popupMenu.add(screenshotItem);

        JMenuItem restartDevice = new JMenuItem("Restart Device");
        restartDevice.addActionListener(actionEvent -> handleRestartCommand());
        popupMenu.add(restartDevice);

        JMenuItem termItem = new JMenuItem("Open Terminal");
        termItem.addActionListener(actionEvent -> handleTermCommand());
        popupMenu.add(termItem);

        JMenuItem serverItem = new JMenuItem("Edit Custom Field 1...");
        serverItem.addActionListener(actionEvent -> handleSetProperty(1));
        popupMenu.add(serverItem);

        JMenuItem notesItem = new JMenuItem("Edit Custom Field 2...");
        notesItem.addActionListener(actionEvent -> handleSetProperty(2));
        popupMenu.add(notesItem);

        if (device.isWireless()) {
            popupMenu.addSeparator();
            JMenuItem disconnectItem = new JMenuItem("Disconnect " + device.getDisplayName());
            disconnectItem.addActionListener(actionEvent -> handleDisconnect(device));
            popupMenu.add(disconnectItem);
        }

        popupMenu.show(e.getComponent(), e.getX(), e.getY());
        //table.setComponentPopupMenu(popupMenu);
    }

    @Override
    public void showHeaderPopupMenu(int column, MouseEvent e) {
        JPopupMenu popupMenu = new JPopupMenu();
        DeviceTableModel.Columns columnType = model.getColumnType(column);
        if (columnType != null) {
            JMenuItem hideItem = new JMenuItem("Hide Column " + columnType.name());
            hideItem.addActionListener(actionEvent -> handleHideColumn(column));
            popupMenu.add(hideItem);

            // NOTE: move out of if() block when more menu items are added
            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    private void handleHideColumn(int column) {
        DeviceTableModel.Columns columnType = model.getColumnType(column);
        if (columnType == null) return;
        List<String> hiddenColList = SettingsDialog.getHiddenColumnList();
        hiddenColList.add(columnType.name());
        Preferences preferences = Preferences.userRoot();
        preferences.put(SettingsDialog.PREF_HIDDEN_COLUMNS, GsonHelper.toJson(hiddenColList));
        model.setHiddenColumns(hiddenColList);
    }

    @Override
    public void handleTableDoubleClick(int row, int column, MouseEvent e) {
        if (column == DeviceTableModel.Columns.CUSTOM1.ordinal()) {
            // edit custom 1 field
            handleSetProperty(1);
        } else if (column == DeviceTableModel.Columns.CUSTOM2.ordinal()) {
            // edit custom 1 field
            handleSetProperty(2);
        } else {
            handleMirrorCommand();
        }
    }

    private void handleCopyClipboardFieldCommand() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        if (table.selectedColumn < 0) return;

        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (!sb.isEmpty()) sb.append("\n");
            String value = model.deviceValue(device, table.selectedColumn);
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

    private void handleFilesDropped(List<File> fileList) {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        boolean isApk = false;
        StringBuilder name = new StringBuilder();
        for (File file : fileList) {
            if (name.length() > 0) name.append(", ");
            String filename = file.getName();
            name.append(filename);
            if (filename.endsWith(".apk")) {
                isApk = true;
                break;
            }
        }

        String title = isApk ? "Install App" : "Copy File";
        String msg = isApk ? "Install " : "Copy ";
        msg += name.toString();
        msg += " to " + selectedDeviceList.size() + " device(s)?";

        // prompt to install/copy
        // NOTE: using JDialog.setAlwaysOnTap to bring app to foreground on drag and drop operations
        final JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);
        int rc = JOptionPane.showConfirmDialog(dialog, msg, title, JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        log.debug("handleFilesDropped: installing: {}", name);

        for (Device device : selectedDeviceList) {
            for (File file : fileList) {
                String filename = file.getName();
                if (filename.endsWith(".apk")) {
                    DeviceManager.getInstance().installApp(device, file, (isSuccess, error) -> {

                    });
                } else {
                    // TODO: where to put files?
                    DeviceManager.getInstance().copyFile(device, file, "/sdcard/Download/", (isSuccess, error) -> {
                    });
                }
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
            model.updateRowForDevice(device);
        }
    }

    private void handleInputCommand() {
        Device device = getFirstSelectedDevice();
        if (device == null) return;

        InputScreen inputScreen = inputViewMap.get(device.serial);
        if (inputScreen == null) {
            inputScreen = new InputScreen(this, device);
            inputViewMap.put(device.serial, inputScreen);
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
        for (Device device : selectedDeviceList) {
            device.isBusy = true;
            model.updateRowForDevice(device);
            DeviceManager.getInstance().captureScreenshot(device, (isSuccess, error) -> {
                device.isBusy = false;
                model.updateRowForDevice(device);
                if (!isSuccess && selectedDeviceList.size() == 1) {
                    // only show dialog if command was run on a single device
                    String msg = "RESULTS:\n\n" + error;
                    JOptionPane.showMessageDialog(this, msg);
                }
            });
        }
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

    private void handleDeviceDetails() {
        Device device = getFirstSelectedDevice();
        if (device == null) return;

        JPanel panel = new JPanel(new MigLayout());
        addDeviceDetail(panel, "Serial", device.serial);
        addDeviceDetail(panel, "Model", device.getProperty(Device.PROP_MODEL));
        addDeviceDetail(panel, "Phone", device.phone);
        addDeviceDetail(panel, "IMEI", device.imei);
        addDeviceDetail(panel, "Carrier", device.getProperty(Device.PROP_CARRIER));
        addDeviceDetail(panel, "OS", device.getProperty(Device.PROP_OS));
        addDeviceDetail(panel, "SDK", device.getProperty(Device.PROP_SDK));
        addDeviceDetail(panel, "Free Space", FileUtils.bytesToDisplayString(device.freeSpace));
        addDeviceDetail(panel, "Custom1", device.getCustomProperty(Device.CUST_PROP_1));
        addDeviceDetail(panel, "Custom2", device.getCustomProperty(Device.CUST_PROP_2));

        if (device.propMap != null) {
            DefaultListModel<String> listModel = new DefaultListModel<>();
            for (Map.Entry<String, String> entry : device.propMap.entrySet()) {
                listModel.addElement(entry.getKey() + " : " + entry.getValue());
            }
            JList<String> list = new JList<>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new AlternatingBackgroundColorRenderer());
            list.setVisibleRowCount(6);
            JScrollPane scroll = new JScrollPane(list);
            panel.add(scroll, "grow, span, wrap");
        }

        JOptionPane.showOptionDialog(this, panel, "Device Info", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
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

        for (Device device : selectedDeviceList) {
            device.isBusy = true;
            model.updateRowForDevice(device);
            DeviceManager.getInstance().mirrorDevice(device, (isSuccess, error) -> {
                device.isBusy = false;
                model.updateRowForDevice(device);
                // only show dialog if mirror was run on a single device
                if (selectedDeviceList.size() == 1 && !isSuccess) {
                    String msg = "RESULTS:\n\n" + error;
                    JOptionPane.showMessageDialog(this, msg);
                }
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
            if (device != null) selectedDeviceList.add(device);
        }
        if (selectedDeviceList.isEmpty() && model.getRowCount() == 1) {
            Device device = model.getDeviceAtRow(0);
            selectedDeviceList.add(device);
        }
        return selectedDeviceList;
    }

    private void setupToolbar() {
        toolbar.setRollover(true);
        JButton button;

        createToolbarButton(toolbar, "icon_add.png", "Connect", "Connect Device", actionEvent -> handleConnectDevice());
        toolbar.addSeparator();

        button = createToolbarButton(toolbar, "icon_scrcpy.png", "Mirror", "Mirror (scrcpy)", actionEvent -> handleMirrorCommand());
        deviceButtonList.add(button);

        button = createToolbarButton(toolbar, "icon_screenshot.png", "Screenshot", "Screenshot", actionEvent -> handleScreenshotCommand());
        deviceButtonList.add(button);

        button = createToolbarButton(toolbar, "icon_edit.png", "Input", "Enter text", actionEvent -> handleInputCommand());
        deviceButtonList.add(button);

        toolbar.addSeparator();

        button = createToolbarButton(toolbar, "icon_browse.png", "Browse", "File Explorer", actionEvent -> handleBrowseCommand());
        deviceButtonList.add(button);

        button = createToolbarButton(toolbar, "icon_browse.png", "Logs", "Log Viewer", actionEvent -> handleLogsCommand());
        deviceButtonList.add(button);
        button = createToolbarButton(toolbar, "icon_install.png", "Install", "Install / Copy file", actionEvent -> handleInstallCommand());
        deviceButtonList.add(button);
        button = createToolbarButton(toolbar, "icon_terminal.png", "Terminal", "Open Terminal (adb shell)", actionEvent -> handleTermCommand());
        deviceButtonList.add(button);

        toolbar.addSeparator();
        //createToolbarButton(toolbar, "icon_variable.png", "Set Property", actionEvent -> handleSetPropertyCommand());

        // create custom action buttons
        button = createToolbarButton(toolbar, "icon_custom.png", "ADB", "Run custom adb command", actionEvent -> handleRunCustomCommand());
        deviceButtonList.add(button);

        toolbar.add(Box.createHorizontalGlue());

        filterTextField = new HintTextField(HINT_FILTER_DEVICES, this::filterDevices);
        filterTextField.setPreferredSize(new Dimension(150, 40));
        filterTextField.setMinimumSize(new Dimension(10, 40));
        filterTextField.setMaximumSize(new Dimension(200, 40));
        toolbar.add(filterTextField);
//        toolbar.add(Box.createHorizontalGlue());

        createToolbarButton(toolbar, "icon_refresh.png", "Refresh", "Refresh Device List", actionEvent -> refreshDevices());
        createToolbarButton(toolbar, "icon_settings.png", "Settings", "Settings", actionEvent -> handleSettingsClicked());
    }

    private void handleSettingsClicked() {
        SettingsDialog.showSettings(this, model);
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
            DeviceManager.getInstance().restartDevice(device, (isSuccess, error) -> {
                refreshDevices();
            });
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

        Preferences preferences = Preferences.userRoot();
        String downloadFolder = preferences.get(ExploreScreen.PREF_DOWNLOAD_FOLDER, System.getProperty("user.home"));

        JFileChooser chooser = new NativeJFileChooser();
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

    public void handleBrowseCommand() {
        Device selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        ExploreScreen exploreScreen = exploreViewMap.get(selectedDevice.serial);
        if (exploreScreen == null) {
            exploreScreen = new ExploreScreen(this, selectedDevice);
            exploreViewMap.put(selectedDevice.serial, exploreScreen);
        }
        exploreScreen.show();
    }

    public void handleLogsCommand() {
        Device selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        LogsScreen logsScreen = logsViewMap.get(selectedDevice.serial);
        if (logsScreen == null) {
            logsScreen = new LogsScreen(this, selectedDevice);
            logsViewMap.put(selectedDevice.serial, logsScreen);
        }
        logsScreen.show();
    }

    private void handleVersionClicked() {
        // show logs
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        File logsFile = logger.getFileLog();
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.EDIT)) {
                desktop.edit(logsFile);
                return;
            } else if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(logsFile);
                return;
            }
        } catch (Exception e) {
            log.error("handleVersionClicked: Exception: {}, {}", logsFile.getAbsolutePath(), e.getMessage());
        }
        // open failed
        JOptionPane.showConfirmDialog(this, "Failed to open logs: " + logsFile.getAbsolutePath(), "Error", JOptionPane.DEFAULT_OPTION);
    }

}
