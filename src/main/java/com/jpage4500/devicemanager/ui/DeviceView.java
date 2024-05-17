package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.Build;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.table.utils.DeviceCellRenderer;
import com.jpage4500.devicemanager.ui.views.*;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public class DeviceView extends BaseFrame implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceView.class);

    private static final String HINT_FILTER_DEVICES = "Filter devices...";
    public static final String PREF_CUSTOM_COMMAND_LIST = "PREF_CUSTOM_COMMAND_LIST";

    public CustomTable table;
    public DeviceTableModel model;
    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;
    private HintTextField textField;

    public int selectedColumn = -1;
    private final List<JButton> deviceButtonList = new ArrayList<>();

    // open windows (per device)
    private final Map<String, ExploreView> exploreViewMap = new HashMap<>();
    private final Map<String, LogsView> logsViewMap = new HashMap<>();

    public DeviceView() {
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
        if (deviceList != null) {
            model.setDeviceList(deviceList);

            if (!deviceList.isEmpty() && table.getSelectedRow() == -1) {
                table.changeSelection(0, 0, false, false);
            }

            refreshUi();

            for (Device device : deviceList) {
                updateDeviceState(device);
            }
        }
        updateVersionLabel();
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        model.updateRowForDevice(device);
        updateDeviceState(device);
    }

    @Override
    public void handleDeviceRemoved(Device device) {
        updateDeviceState(device);
    }

    @Override
    public void handleException(Exception e) {
        Object[] choices = {"Retry", "Cancel"};
        int rc = JOptionPane.showOptionDialog(DeviceView.this,
                "Unable to connect to ADB server. Please check that it's running and re-try"
                , "ADB Server", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, null);
        if (rc != JOptionPane.YES_OPTION) return;

        connectAdbServer();
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
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.RED);
        panel.add(scrollPane, BorderLayout.CENTER);

        // -- statusbar --
        statusBar = new StatusBar();
        statusBar.setLeftLabelListener(this::handleVersionClicked);
        panel.add(statusBar, BorderLayout.SOUTH);
        updateVersionLabel();

        setupMenuBar();

        setContentPane(panel);
        setVisible(true);

        // -- empty view --
        // NOTE: must be done after setContentPane() above
        JRootPane rootPane = SwingUtilities.getRootPane(table);
        emptyView = new EmptyView("No Android Devices!");
        rootPane.setGlassPane(emptyView);
        emptyView.setOpaque(false);
        emptyView.setVisible(true);

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
        });

        // [CMD + 2] = show explorer
        createCmdAction(windowMenu, "Browse Files", KeyEvent.VK_2, e -> {
            handleBrowseCommand();
        });

        // [CMD + 3] = show logs
        createCmdAction(windowMenu, "View Logs", KeyEvent.VK_3, e -> {
            handleLogsCommand();
        });

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    private void setupTable() {
        table.setShowTooltips(true);
        model = new DeviceTableModel();

        // restore previous settings
        List<String> appList = SettingsScreen.getCustomApps();
        model.setAppList(appList);
        List<String> hiddenColList = SettingsScreen.getHiddenColumnList();
        model.setHiddenColumns(hiddenColList);

        table.setModel(model);
        table.setDefaultRenderer(Device.class, new DeviceCellRenderer());

        // right-align size column
//        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
//        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
//        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);

        // support drag and drop of files
        MyDragDropListener dragDropListener = new MyDragDropListener(table, true, this::handleFilesDropped);
        new DropTarget(table, dragDropListener);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // single click
                Point point = e.getPoint();
                int row = table.rowAtPoint(point);
                int column = table.columnAtPoint(point);
                if (SwingUtilities.isRightMouseButton(e)) {
                    // right-click
                    if (!table.isRowSelected(row)) {
                        table.changeSelection(row, column, false, false);
                    }
                    selectedColumn = column;
                    setupPopupMenu(row, e);
                } else if (e.getClickCount() == 2) {
                    // double-click
                    selectedColumn = -1;
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
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            refreshUi();
        });
        table.requestFocus();
    }

    private void updateDeviceState(Device device) {
        ExploreView exploreView = exploreViewMap.get(device.serial);
        if (exploreView != null) {
            exploreView.updateDeviceState();
        }

        LogsView logsView = logsViewMap.get(device.serial);
        if (logsView != null) {
            logsView.updateDeviceState();
        }
    }

    private void refreshUi() {
        int selectedRowCount = table.getSelectedRowCount();
        int rowCount = table.getRowCount();
        if (selectedRowCount > 0) {
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

        statusBar.setLeftLabel(Build.versionName + " / " + memUsage);
    }

    private void setupPopupMenu(int row, MouseEvent e) {
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

    private void handleCopyClipboardFieldCommand() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        if (selectedColumn < 0) return;

        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (!sb.isEmpty()) sb.append("\n");
            String value = model.deviceValue(device, selectedColumn);
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
            DeviceManager.getInstance().openTerminal(device, this);
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

        for (Device device : selectedDeviceList) {
            for (File file : fileList) {
                String filename = file.getName();
                if (filename.endsWith(".apk")) {
                    DeviceManager.getInstance().installApp(device, file, this);
                } else {
                    DeviceManager.getInstance().copyFile(device, file, "/sdcard/Downloads/", this);
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
            DeviceManager.getInstance().setProperty(device, prop, result);
            device.setCustomProperty(Device.CUSTOM_PROP_X + number, result);
            model.updateRowForDevice(device);
        }
    }

    private void handleInputCommand() {
        Device device = getFirstSelectedDevice();
        if (device == null) return;

        new InputScreen(this, device).show();
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
            DeviceManager.getInstance().captureScreenshot(device, this);
        }
    }

    private void handleConnectDevice() {
        ConnectScreen.showConnectDialog(this, isSuccess -> {
            log.debug("handleConnectDevice: {}", isSuccess);
            if (!isSuccess) JOptionPane.showMessageDialog(this, "Unable to connect!");
        });
    }

    private void handleDisconnect(Device device) {
        DeviceManager.getInstance().disconnectDevice(device.serial, isSuccess -> {
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
            DeviceManager.getInstance().mirrorDevice(device, this);
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
        // if only 1 device exists - just use it
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

        // TODO: add the 'add custom' button
        // createToolbarButton(toolbar, "icon_add.png", "add custom", "Run custom adb command", actionEvent -> handleAddCustomCommand());

        loadCustomScripts(toolbar);

        toolbar.add(Box.createHorizontalGlue());

        textField = new HintTextField(HINT_FILTER_DEVICES, this::filterDevices);
        textField.setPreferredSize(new Dimension(150, 40));
        textField.setMinimumSize(new Dimension(10, 40));
        textField.setMaximumSize(new Dimension(200, 40));
        toolbar.add(textField);
//        toolbar.add(Box.createHorizontalGlue());

        createToolbarButton(toolbar, "icon_refresh.png", "Refresh", "Refresh Device List", actionEvent -> handleRefreshCommand());
        createToolbarButton(toolbar, "icon_settings.png", "Settings", "Settings", actionEvent -> handleSettingsClicked());
    }

    private void loadCustomScripts(JToolBar toolbar) {
        String path = System.getProperty("user.home");
        File folder = new File(path, ".device_manager");
        if (!folder.exists()) {
            boolean ok = folder.mkdir();
            log.debug("loadCustomScripts: creating folder: {}, {}", ok, folder.getAbsolutePath());
        } else {
            File[] files = folder.listFiles();
            if (files == null) return;
            final JPopupMenu popup = new JPopupMenu();
            int numScripts = 0;
            for (File file : files) {
                String name = file.getName();
                if (!TextUtils.endsWith(name, ".sh")) return;
                numScripts++;
                ImageIcon icon = UiUtils.getImageIcon("icon_script.png", 20);
                JMenuItem menuItem = new JMenuItem(name, icon);
                menuItem.addActionListener(e -> {
                    runCustomScript(file);
                });
                popup.add(menuItem);
            }
            if (numScripts > 0) {
                ImageIcon imageIcon = UiUtils.getImageIcon("icon_script.png", 40, 40);
                JSplitButton button = new JSplitButton(imageIcon);
                button.setText("Custom");
                button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
                button.setToolTipText("View custom scripts");
                button.setVerticalTextPosition(SwingConstants.BOTTOM);
                button.setHorizontalTextPosition(SwingConstants.CENTER);
                button.setPopupMenu(popup);
                toolbar.add(button);
                deviceButtonList.add(button);
            }
        }
    }

    private void runCustomScript(File file) {
        log.debug("runCustomScript: run:{}", file.getAbsolutePath());

        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        } else if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(this, "Run script: " + file.getName() + " on selected devices?", "Run Script?", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;
        }
        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().runUserScript(device, file, this);
        }
    }

    private void handleSettingsClicked() {
        SettingsScreen.showSettings(this, model);
    }

    private void handleRefreshCommand() {
        DeviceManager.getInstance().refreshDevices(this);
    }

    private void handleRunCustomCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }
        Preferences preferences = Preferences.userRoot();
        String customCommands = preferences.get(PREF_CUSTOM_COMMAND_LIST, null);
        List<String> customList = GsonHelper.stringToList(customCommands, String.class);

        JComboBox comboBox = new JComboBox(customList.toArray(new String[]{}));
        comboBox.setEditable(true);
        int rc = JOptionPane.showOptionDialog(this, comboBox, "Custom adb command", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return;
        String selectedItem = comboBox.getSelectedItem().toString();
        if (TextUtils.isEmpty(selectedItem)) return;
        // remove "adb " from commands
        if (selectedItem.startsWith("adb ")) {
            selectedItem = selectedItem.substring("adb ".length());
        }
        // remove from list
        customList.remove(selectedItem);
        // add to top of list
        customList.add(0, selectedItem);
        // only save last 10 entries
        if (customList.size() > 10) {
            customList = customList.subList(0, 10);
        }
        preferences.put(PREF_CUSTOM_COMMAND_LIST, GsonHelper.toJson(customList));
        log.debug("handleRunCustomCommand: {}", selectedItem);
        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().runCustomCommand(device, selectedItem, this);
        }
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
            DeviceManager.getInstance().restartDevice(device, this);
        }
    }

    private void showSelectDevicesDialog() {
        if (model.getRowCount() > 0) {
            JOptionPane.showConfirmDialog(this, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
        }
    }

    private void filterDevices(String text) {
        final TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);
        if (!text.isEmpty() && !TextUtils.equals(text, HINT_FILTER_DEVICES)) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        } else {
            sorter.setRowFilter(null);
        }
    }

    private void handleInstallCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }
        FileDialog dialog = new FileDialog(this, "Select File to Install/Copy to selected devices");
        dialog.setMode(FileDialog.LOAD);
        dialog.setVisible(true);
        File[] fileArr = dialog.getFiles();
        if (fileArr == null || fileArr.length == 0) return;
        handleFilesDropped(Arrays.asList(fileArr));
    }

    public void handleBrowseCommand() {
        Device selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        ExploreView exploreView = exploreViewMap.get(selectedDevice.serial);
        if (exploreView == null) {
            exploreView = new ExploreView(this, selectedDevice);
            exploreViewMap.put(selectedDevice.serial, exploreView);
        }
        exploreView.show();
    }

    public void handleLogsCommand() {
        Device selectedDevice = getFirstSelectedDevice();
        if (selectedDevice == null) return;

        LogsView logsView = logsViewMap.get(selectedDevice.serial);
        if (logsView == null) {
            logsView = new LogsView(this, selectedDevice);
            logsViewMap.put(selectedDevice.serial, logsView);
        }
        logsView.show();
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
