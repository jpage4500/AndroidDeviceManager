package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.*;
import com.jpage4500.devicemanager.utils.ColumnsAutoSizer;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.MyDragDropListener;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.viewmodel.DeviceTableModel;
import com.jpage4500.devicemanager.viewmodel.LogsTableModel;

import net.coobird.thumbnailator.Thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

public class MainApplication implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private static final String HINT_FILTER_DEVICES = "Filter devices...";
    public static final String PREF_CUSTOM_COMMAND_LIST = "PREF_CUSTOM_COMMAND_LIST";

    public JPanel panel;
    public CustomTable table;
    public CustomFrame frame;
    public DeviceTableModel model;
    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;

    public MainApplication() {
        setupLogging();
        log.debug("MainApplication: APP START: {}", Build.versionName);

        SwingUtilities.invokeLater(this::initializeUI);
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Device Manager");

        MainApplication app = new MainApplication();
    }

    /**
     * configuring SLF4J custom logger implementation
     * - call soon after Application:onCreate(); can be called again if debug mode changes
     */
    private void setupLogging() {
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        // tag prefix allows for easy filtering: ie: 'adb logcat | grep PM_'
        //logger.setTagPrefix("DM");
        // set log level that application should log at (and higher)
        logger.setDebugLevel(Log.VERBOSE);
        logger.setMainThreadId(Thread.currentThread().getId());
        logger.setLogToFile(true);
        logger.setFileLogLevel(Log.INFO);
    }

    private void initializeUI() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            log.error("initializeUI: {}", e.getMessage());
        }

        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("defaultFont", new Font("Arial", Font.PLAIN, 16));

        // set docker app icon (mac)
        final Taskbar taskbar = Taskbar.getTaskbar();
        try {
            Image image = ImageIO.read(getClass().getResource("/images/logo.png"));
            taskbar.setIconImage(image);
        } catch (final Exception e) {
            log.error("Exception: {}", e.getMessage());
        }

        frame = new CustomFrame("Device Manager");
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                log.trace("windowActivated: ");
                DeviceManager.getInstance().startDevicePolling(MainApplication.this, 10);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                log.trace("windowDeactivated: ");
                DeviceManager.getInstance().stopDevicePolling();
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        table = new CustomTable();
        model = new DeviceTableModel();

        List<String> appList = SettingsScreen.getCustomApps();
        model.setAppList(appList);

        table.setModel(model);

        // TODO: find way to auto-size columns and also remember user sizes
        //model.addTableModelListener(e -> ColumnsAutoSizer.sizeColumnsToFit(table));
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.RED);
        panel.add(scrollPane, BorderLayout.CENTER);

        // setup toolbar
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // statusbar
        statusBar = new StatusBar();
        statusBar.setLeftLabel("Version " + Build.versionName);
        statusBar.setLeftLabelListener(this::handleVersionClicked);
        panel.add(statusBar, BorderLayout.SOUTH);

        frame.setContentPane(panel);
        frame.setVisible(true);

        JRootPane rootPane = SwingUtilities.getRootPane(table);
        emptyView = new EmptyView();
        rootPane.setGlassPane(emptyView);
        emptyView.setOpaque(false);
        emptyView.setVisible(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("initializeUI: EXIT");
            table.persist();
            DeviceManager.getInstance().handleExit();
        }));

        // support drag and drop of files
        MyDragDropListener dragDropListener = new MyDragDropListener(table, this::handleFilesDropped);
        new DropTarget(table, dragDropListener);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point point = e.getPoint();
                int row = table.rowAtPoint(point);
                int column = table.columnAtPoint(point);
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (!table.isRowSelected(row)) {
                        table.changeSelection(row, column, false, false);
                    }
                } else if (e.getClickCount() == 2) {
                    // double-click
                    if (column == DeviceTableModel.Columns.CUSTOM1.ordinal()) {
                        handleSetProperty(1);
                    } else if (column == DeviceTableModel.Columns.CUSTOM2.ordinal()) {
                        handleSetProperty(2);
                    } else {
                        handleMirrorCommand();
                    }
                }
            }
        });

        setupPopupMenu();

        table.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            if (!listSelectionEvent.getValueIsAdjusting()) {
                int selectedRowCount = table.getSelectedRowCount();
                if (selectedRowCount > 0) {
                    statusBar.setRightLabel("selected: " + selectedRowCount);
                } else {
                    statusBar.setRightLabel(null);
                }
            }
        });
        table.requestFocus();
    }

    private void setupPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

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

        JMenuItem serverItem = new JMenuItem("Custom Field 1...");
        serverItem.addActionListener(actionEvent -> handleSetProperty(1));
        popupMenu.add(serverItem);

        JMenuItem notesItem = new JMenuItem("Custom Field 2...");
        notesItem.addActionListener(actionEvent -> handleSetProperty(2));
        popupMenu.add(notesItem);

        table.setComponentPopupMenu(popupMenu);
    }

    private void handleTermCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.size() == 0) {
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
        if (selectedDeviceList.size() == 0) {
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
        int rc = JOptionPane.showConfirmDialog(frame, msg, title, JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (Device device : selectedDeviceList) {
            for (File file : fileList) {
                String filename = file.getName();
                if (filename.endsWith(".apk")) {
                    DeviceManager.getInstance().installApp(device, file, this);
                } else {
                    DeviceManager.getInstance().copyFile(device, file, this);
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
        String message = "";
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
            String prop = "persist.dm.custom" + number;
            DeviceManager.getInstance().setProperty(device, prop, result);
            if (number == 1) device.custom1 = result;
            else if (number == 2) device.custom2 = result;
            model.updateRowForDevice(device);
        }
    }

    private void handleScreenshotCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.size() == 0) {
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

    private void handleMirrorCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.size() == 0) {
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
        return selectedDeviceList;
    }

    private void setupToolbar() {
        if (toolbar == null) {
            toolbar = new JToolBar("Applications");
            toolbar.setRollover(true);
        } else {
            toolbar.removeAll();
        }

        createButton(toolbar, "icon_power.png", "Restart", "Restart Device", actionEvent -> handleRestartCommand());
        toolbar.addSeparator();
        createButton(toolbar, "icon_scrcpy.png", "Mirror", "Mirror (scrcpy)", actionEvent -> handleMirrorCommand());
        createButton(toolbar, "icon_screenshot.png", "Screenshot", "Screenshot", actionEvent -> handleScreenshotCommand());
        toolbar.addSeparator();
        createButton(toolbar, "icon_install.png", "Install", "Install / Copy file", actionEvent -> handleInstallCommand());
        createButton(toolbar, "icon_terminal.png", "Terminal", "Open Terminal (adb shell)", actionEvent -> handleTermCommand());
        //createButton(toolbar, "icon_variable.png", "Set Property", actionEvent -> handleSetPropertyCommand());

        // create custom action buttons
        createButton(toolbar, "icon_custom.png", "ADB", "Run custom adb command", actionEvent -> handleRunCustomCommand());

        // TODO: add the 'add custom' button
        // createButton(toolbar, "icon_add.png", "add custom", "Run custom adb command", actionEvent -> handleAddCustomCommand());

        toolbar.add(Box.createHorizontalGlue());

        HintTextField textField = new HintTextField(HINT_FILTER_DEVICES);
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

    private void handleSettingsClicked() {
        SettingsScreen.showSettings(this);
    }

    private void handleRefreshCommand() {
        // TODO: probably a better way to force a refresh
        DeviceManager.getInstance().stopDevicePolling();
        DeviceManager.getInstance().startDevicePolling(MainApplication.this, 10);
    }

    private void handleRunCustomCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.size() == 0) {
            showSelectDevicesDialog();
            return;
        }
        Preferences preferences = Preferences.userRoot();
        String customCommands = preferences.get(PREF_CUSTOM_COMMAND_LIST, null);
        List<String> customList = GsonHelper.stringToList(customCommands, String.class);

        JComboBox comboBox = new JComboBox(customList.toArray(new String[]{}));
        comboBox.setEditable(true);
        int rc = JOptionPane.showOptionDialog(frame, comboBox, "Custom adb command", -1, JOptionPane.QUESTION_MESSAGE, null, null, null);
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
        if (selectedDeviceList.size() == 0) {
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
        JOptionPane.showConfirmDialog(frame, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
    }

    private void filterDevices(String text) {
        final TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);
        if (text.length() > 0 && !TextUtils.equals(text, HINT_FILTER_DEVICES)) {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        } else {
            sorter.setRowFilter(null);
        }
    }

    private void handleInstallCommand() {
        List<Device> selectedDeviceList = getSelectedDevices();
        if (selectedDeviceList.size() == 0) {
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

    private void createButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        Image icon;
        try {
            // library offers MUCH better image scaling than ImageIO
            icon = Thumbnails.of(getClass().getResource("/images/" + imageName)).size(40, 40).asBufferedImage();
            //Image image = ImageIO.read(getClass().getResource("/images/" + imageName));
            if (icon == null) {
                log.error("createButton: image not found! {}", imageName);
                return;
            }
        } catch (Exception e) {
            log.debug("createButton: Exception:{}", e.getMessage());
            return;
        }
        JButton button = new JButton(new ImageIcon(icon));
        if (label != null) button.setText(label);

        button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        if (tooltip != null) button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.addActionListener(listener);
        toolbar.add(button);
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        if (deviceList != null) {
            model.setDeviceList(deviceList);

            int deviceCount = deviceList.size();
            statusBar.setRightLabel("connected: " + deviceCount);
            emptyView.setVisible(deviceCount == 0);
        }
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        model.updateRowForDevice(device);
    }

    private void handleVersionClicked() {
        // show logs
        JFrame frame = new JFrame("Logs");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        CustomTable table = new CustomTable();
        table.setRowHeight(30);
        LogsTableModel model = new LogsTableModel();
        table.setModel(model);
        model.addTableModelListener(e -> ColumnsAutoSizer.sizeColumnsToFit(table));

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        frame.setContentPane(panel);
        frame.pack();
        frame.setVisible(true);

        new Thread(() -> {
            log.debug("handleVersionClicked: loading...");
            AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
            File logsFile = logger.getFileLog();
            List<LogEntry> logEntryList = new ArrayList<>();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logsFile)));
                for (int numLines = 0; ; numLines++) {
                    String line = reader.readLine();
                    if (line == null) break;

                    String[] lineArr = line.split(": ", 4);
                    if (lineArr.length < 4) {
                        log.debug("handleVersionClicked: invalid line: {}", line);
                        continue;
                    }
                    LogEntry entry = new LogEntry();
                    entry.date = lineArr[0];
                    entry.thread = lineArr[1];
                    entry.level = lineArr[2];
                    entry.message = lineArr[3];
                    logEntryList.add(entry);
                }
                reader.close();
            } catch (Exception e) {
                log.error("handleVersionClicked: Exception:{}", e.getMessage());
            }
            log.debug("handleVersionClicked: DONE loading {} logs", logEntryList.size());
            model.setLogsList(logEntryList);
        }).start();
    }

}
