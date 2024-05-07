package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.Build;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.*;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.MyDragDropListener;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.viewmodel.DeviceTableModel;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public class DeviceView implements DeviceManager.DeviceListener, KeyListener {
    private static final Logger log = LoggerFactory.getLogger(DeviceView.class);

    private static final String HINT_FILTER_DEVICES = "Filter devices...";
    public static final String PREF_CUSTOM_COMMAND_LIST = "PREF_CUSTOM_COMMAND_LIST";

    // TODO: for testing logging
    private static final boolean TEST_LOGGING = false;

    public JPanel panel;
    public CustomTable table;
    public CustomFrame frame;
    public DeviceTableModel model;
    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;
    private HintTextField textField;

    public int selectedColumn = -1;
    private final List<JButton> deviceButtonList = new ArrayList<>();
    private final List<Device> wirelessDeviceList = new ArrayList<>();

    private ExploreView exploreView;
    private LogsView logsView;

    public DeviceView() {
        initalizeUi();
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        if (deviceList != null) {
            model.setDeviceList(deviceList);

            // check if any devices are wireless
            for (Device device : deviceList) {
                checkWirelessDevice(device);
            }

            refreshUi();
        }
        updateVersionLabel();
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        model.updateRowForDevice(device);
        checkWirelessDevice(device);
    }

    private void initalizeUi() {
        frame = new CustomFrame("main");
        frame.setTitle("Device Manager");
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                DeviceManager.getInstance().startDevicePolling(DeviceView.this, 10);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                DeviceManager.getInstance().stopDevicePolling();
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // -- CMD+2 = show devices --
        Action switchAction = new AbstractAction("Show Explorer") {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleBrowseCommand();
            }
        };
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        KeyStroke switchKey = KeyStroke.getKeyStroke(KeyEvent.VK_2, mask);
        switchAction.putValue(Action.ACCELERATOR_KEY, switchKey);

        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu("Window");
        JMenuItem switchItem = new JMenuItem("Show Explorer");

        if (TEST_LOGGING) {
            // -- CMD+3 = show log window --
            Action logAction = new AbstractAction("Show Log View") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    handleLogsCommand();
                }
            };
            KeyStroke logKey = KeyStroke.getKeyStroke(KeyEvent.VK_3, mask);
            logAction.putValue(Action.ACCELERATOR_KEY, logKey);
            switchItem.setAction(logAction);
        }

        menu.add(switchItem);
        menubar.add(menu);
        frame.setJMenuBar(menubar);

        table = new CustomTable("devices");
        model = new DeviceTableModel();

        // restore previous settings
        List<String> appList = SettingsScreen.getCustomApps();
        model.setAppList(appList);
        List<String> hiddenColList = SettingsScreen.getHiddenColumnList();
        model.setHiddenColumns(hiddenColList);

        table.setModel(model);

        // right-align size column
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        table.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.RED);
        panel.add(scrollPane, BorderLayout.CENTER);

        // setup toolbar
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // statusbar
        statusBar = new StatusBar();
        statusBar.setLeftLabelListener(this::handleVersionClicked);
        panel.add(statusBar, BorderLayout.SOUTH);
        updateVersionLabel();

        frame.setContentPane(panel);
        frame.setVisible(true);

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

        table.addKeyListener(this);

        // restore wireless device list
        Preferences preferences = Preferences.userRoot();
        String wirelessStr = preferences.get(ConnectScreen.PREF_RECENT_WIRELESS_DEVICES, null);
        List<Device> wirelessList = GsonHelper.stringToList(wirelessStr, Device.class);
        wirelessDeviceList.addAll(wirelessList);
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
            JMenuItem disconnectItem = new JMenuItem("Disconnect " + device.model);
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

        DeviceTableModel.Columns column = null;
        if (selectedColumn >= 0 && selectedColumn < DeviceTableModel.Columns.values().length) {
            column = DeviceTableModel.Columns.values()[selectedColumn];
        }
        if (column == null) return;

        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(getDeviceField(device, column));
        }
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private String getDeviceField(Device device, DeviceTableModel.Columns column) {
        String val;
        switch (column) {
            case SERIAL:
                val = device.serial;
                break;
            case MODEL:
                val = device.model;
                break;
            case PHONE:
                val = device.phone;
                break;
            case IMEI:
                val = device.imei;
                break;
            case FREE:
                val = String.valueOf(device.freeSpace);
                break;
            case CUSTOM1:
                val = device.custom1;
                break;
            case CUSTOM2:
                val = device.custom2;
                break;
            case STATUS:
                val = device.status;
                break;
            default:
                val = column.name();
                break;
        }
        if (TextUtils.isEmpty(val)) return "";
        else return val;
    }

    private void handleCopyClipboardCommand() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        DeviceTableModel.Columns[] columns = DeviceTableModel.Columns.values();
        StringBuilder sb = new StringBuilder();
        for (Device device : selectedDeviceList) {
            if (sb.length() > 0) sb.append("\n");
            for (int i = 0; i < columns.length; i++) {
                DeviceTableModel.Columns column = columns[i];
                if (i > 0) sb.append(", ");
                sb.append(getDeviceField(device, column));
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
            int rc = JOptionPane.showConfirmDialog(frame, "Open Terminal for " + selectedDeviceList.size() + " devices?", "Open Terminal", JOptionPane.YES_NO_OPTION);
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
            if (number == 1) customValue = device.custom1;
            else if (number == 2) customValue = device.custom2;
            message = "Enter Custom Note";
        } else {
            message = "Enter Custom Note for " + selectedDeviceList.size() + " devices";
        }

        String result = (String) JOptionPane.showInputDialog(frame,
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
            if (number == 1) device.custom1 = result;
            else if (number == 2) device.custom2 = result;
            model.updateRowForDevice(device);
        }
    }

    private void handleScreenshotCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        } else if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(frame, "Take screenshot of " + selectedDeviceList.size() + " devices?", "Screenshot", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;
        }
        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().captureScreenshot(device, this);
        }
    }

    private void handleConnectDevice() {
        ConnectScreen.showConnectDialog(frame, isSuccess -> {
            log.debug("handleConnectDevice: {}", isSuccess);
            if (isSuccess) refreshRetry();
            else JOptionPane.showMessageDialog(frame, "Unable to connect!");
        });
    }

    private void handleDisconnect(Device device) {
        DeviceManager.getInstance().disconnectDevice(device.serial, isSuccess -> {
            if (isSuccess) refreshRetry();
            else JOptionPane.showMessageDialog(frame, "Unable to disconnect!");
        });
    }

    private void handleMirrorCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        } else if (selectedDeviceList.size() > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(frame,
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
        if (toolbar == null) {
            toolbar = new JToolBar("Applications");
            toolbar.setRollover(true);
        } else {
            toolbar.removeAll();
        }
        deviceButtonList.clear();
        JButton button;

        createButton(toolbar, "icon_add.png", "Connect", "Connect Device", actionEvent -> handleConnectDevice());
        toolbar.addSeparator();

        button = createButton(toolbar, "icon_scrcpy.png", "Mirror", "Mirror (scrcpy)", actionEvent -> handleMirrorCommand());
        deviceButtonList.add(button);

        button = createButton(toolbar, "icon_screenshot.png", "Screenshot", "Screenshot", actionEvent -> handleScreenshotCommand());
        deviceButtonList.add(button);
        toolbar.addSeparator();

        button = createButton(toolbar, "icon_browse.png", "Browse", "File Explorer", actionEvent -> handleBrowseCommand());
        deviceButtonList.add(button);

        if (TEST_LOGGING) {
            button = createButton(toolbar, "icon_browse.png", "Logs", "Log Viewer", actionEvent -> handleLogsCommand());
            deviceButtonList.add(button);
        }
        button = createButton(toolbar, "icon_install.png", "Install", "Install / Copy file", actionEvent -> handleInstallCommand());
        deviceButtonList.add(button);
        button = createButton(toolbar, "icon_terminal.png", "Terminal", "Open Terminal (adb shell)", actionEvent -> handleTermCommand());
        deviceButtonList.add(button);

        toolbar.addSeparator();
        //createButton(toolbar, "icon_variable.png", "Set Property", actionEvent -> handleSetPropertyCommand());

        // create custom action buttons
        button = createButton(toolbar, "icon_custom.png", "ADB", "Run custom adb command", actionEvent -> handleRunCustomCommand());
        deviceButtonList.add(button);

        // TODO: add the 'add custom' button
        // createButton(toolbar, "icon_add.png", "add custom", "Run custom adb command", actionEvent -> handleAddCustomCommand());

        loadCustomScripts(toolbar);

        toolbar.add(Box.createHorizontalGlue());

        textField = new HintTextField(HINT_FILTER_DEVICES);
        textField.setPreferredSize(new Dimension(150, 40));
        textField.setMinimumSize(new Dimension(10, 40));
        textField.setMaximumSize(new Dimension(200, 40));
        textField.getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent documentEvent) {
                        filterDevices(textField.getText());
                    }

                    @Override
                    public void removeUpdate(DocumentEvent documentEvent) {
                        filterDevices(textField.getText());
                    }

                    @Override
                    public void changedUpdate(DocumentEvent documentEvent) {
                    }
                });
        toolbar.add(textField);
//        toolbar.add(Box.createHorizontalGlue());

        createButton(toolbar, "icon_refresh.png", "Refresh", "Refresh Device List", actionEvent -> handleRefreshCommand());
        createButton(toolbar, "icon_settings.png", "Settings", "Settings", actionEvent -> handleSettingsClicked());
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
                Image icon = loadImage("icon_script.png", 20, 20);
                JMenuItem menuItem = new JMenuItem(name, new ImageIcon(icon));
                menuItem.addActionListener(e -> {
                    runCustomScript(file);
                });
                popup.add(menuItem);
            }
            if (numScripts > 0) {
                JButton button = createButton(toolbar, "icon_overflow.png", "Custom", "View custom scripts", null);
                button.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                            popup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                });
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
            int rc = JOptionPane.showConfirmDialog(frame, "Run script: " + file.getName() + " on selected devices?", "Run Script?", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;
        }
        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().runUserScript(device, file, this);
        }
    }

    private void handleSettingsClicked() {
        SettingsScreen.showSettings(frame, model);
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
        int rc = JOptionPane.showOptionDialog(frame, comboBox, "Custom adb command", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
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
        int rc = JOptionPane.showConfirmDialog(frame,
                "Restart " + selectedDeviceList.size() + " device(s)?",
                "Restart devices?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().restartDevice(device, this);
        }
    }

    private void showSelectDevicesDialog() {
        if (model.getRowCount() > 0) {
            JOptionPane.showConfirmDialog(frame, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
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
        FileDialog dialog = new FileDialog(frame, "Select File to Install/Copy to selected devices");
        dialog.setMode(FileDialog.LOAD);
        dialog.setVisible(true);
        File[] fileArr = dialog.getFiles();
        if (fileArr == null || fileArr.length == 0) return;
        handleFilesDropped(Arrays.asList(fileArr));
    }

    private void handleBrowseCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }
        Device selectedDevice = selectedDeviceList.get(0);

        if (exploreView == null) {
            exploreView = new ExploreView(frame);
        }
        exploreView.setDevice(selectedDevice);
    }

    private void handleLogsCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }
        Device selectedDevice = selectedDeviceList.get(0);

        if (logsView == null) {
            logsView = new LogsView(frame);
        }
        logsView.setDevice(selectedDevice);
    }

    private Image loadImage(String path, int w, int h) {
        try {
            // library offers MUCH better image scaling than ImageIO
            Image icon = Thumbnails.of(getClass().getResource("/images/" + path)).size(w, h).asBufferedImage();
            //Image image = ImageIO.read(getClass().getResource("/images/" + imageName));
            if (icon != null) return icon;
            log.error("createButton: image not found! {}", path);
        } catch (Exception e) {
            log.error("createButton: Exception:{}", e.getMessage());
        }
        return null;
    }

    private JButton createButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        Image icon = loadImage(imageName, 40, 40);
        if (icon == null) return null;
        JButton button = new JButton(new ImageIcon(icon));
        if (label != null) button.setText(label);

        button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        if (tooltip != null) button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.addActionListener(listener);
        toolbar.add(button);
        return button;
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
        JOptionPane.showConfirmDialog(frame, "Failed to open logs: " + logsFile.getAbsolutePath(), "Error", JOptionPane.DEFAULT_OPTION);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        int keyCode = e.getExtendedKeyCode();
        char keyChar = e.getKeyChar();
        // ignore any key press along with a modifier key (CTRL/OPTION/CMD) -- except SHIFT
        int modifiers = e.getModifiersEx();
        if (modifiers != 0 && !e.isShiftDown()) return;
        String text = textField.getText();
        if (text.equalsIgnoreCase(HINT_FILTER_DEVICES)) text = "";
        boolean isHandled = false;
        switch (keyCode) {
            case KeyEvent.VK_DELETE:
            case KeyEvent.VK_BACK_SPACE:
                if (text.isEmpty()) break;
                text = text.substring(0, text.length() - 1);
                isHandled = true;
                break;
            case KeyEvent.VK_ESCAPE:
                text = "";
                isHandled = true;
                break;
            default:
                if (Character.isLetterOrDigit(keyChar) || keyChar == KeyEvent.VK_PERIOD || keyChar == KeyEvent.VK_MINUS) {
                    text += keyChar;
                    isHandled = true;
                }
                break;
        }
        if (isHandled) {
            if (text.isEmpty()) textField.setText(HINT_FILTER_DEVICES);
            else textField.setText(text);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        char keyChar = e.getKeyChar();
        if (e.isMetaDown()) {
            // handle shortcut keys
            if (TEST_LOGGING && keyChar == '1') handleLogsCommand();
            else if (keyChar == '2') handleBrowseCommand();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    private void refreshRetry() {
        // refresh every few seconds to pick-up the new device
        new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                handleRefreshCommand();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }).start();
    }

    /**
     * if device is wireless (IP:PORT), remember it to quick connect to later
     */
    private void checkWirelessDevice(Device device) {
        if (!device.isWireless()) return;

        for (Iterator<Device> iterator = wirelessDeviceList.iterator(); iterator.hasNext(); ) {
            Device compareDevice = iterator.next();
            if (TextUtils.equals(compareDevice.serial, device.serial)) {
                if (TextUtils.equals(compareDevice.model, device.model)) {
                    // already exists & is the same
                    return;
                } else {
                    // model changed - remove and re-add to top of list
                    iterator.remove();
                }
            }
        }
        log.trace("checkWireless: ADD: {}", GsonHelper.toJson(device));

        Preferences preferences = Preferences.userRoot();
        // add device to TOP of list
        wirelessDeviceList.add(0, device);

        // max 10 devices
        if (wirelessDeviceList.size() > 10) {
            wirelessDeviceList.remove(wirelessDeviceList.size() - 1);
        }

        preferences.put(ConnectScreen.PREF_RECENT_WIRELESS_DEVICES, GsonHelper.toJson(wirelessDeviceList));
    }

}
