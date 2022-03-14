package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.CustomFrame;
import com.jpage4500.devicemanager.ui.CustomTable;
import com.jpage4500.devicemanager.ui.EmptyView;
import com.jpage4500.devicemanager.ui.HintTextField;
import com.jpage4500.devicemanager.utils.MyDragDropListener;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.viewmodel.DeviceTableModel;

import net.coobird.thumbnailator.Thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

class MainApplication implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private static final String HINT_FILTER_DEVICES = "Filter devices...";

    private JPanel panel;
    private CustomTable table;
    private CustomFrame frame;
    private DeviceTableModel model;
    private EmptyView emptyView;

    public MainApplication() {
        setupLogging();

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
        logger.setDebugLevel(Log.DEBUG);
        logger.setMainThreadId(Thread.currentThread().getId());
        // send all logs to EventLogger as well
        //logger.setLogListener(EventLogger.getInstance());
    }

    private void initializeUI() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            log.error("initializeUI: {}", e.getMessage());
        }

        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("defaultFont", new Font("Arial", Font.PLAIN, 16));

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
        table.setModel(model);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.RED);
        panel.add(scrollPane, BorderLayout.CENTER);

        // setup toolbar
        setupToolbar(panel);

        frame.setContentPane(panel);
        frame.setVisible(true);

        JRootPane rootPane = SwingUtilities.getRootPane(scrollPane);
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
                    handleMirrorCommand();
                }
            }
        });

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

        JMenuItem serverItem = new JMenuItem("Custom Field 1...");
        serverItem.addActionListener(actionEvent -> handleSetCustom1Command());
        popupMenu.add(serverItem);

        JMenuItem notesItem = new JMenuItem("Custom Field 2...");
        notesItem.addActionListener(actionEvent -> handleSetCustom2Command());
        popupMenu.add(notesItem);

        table.setComponentPopupMenu(popupMenu);
        table.requestFocus();
    }

    private void handleFilesDropped(List<File> fileList) {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showConfirmDialog(frame, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
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
        msg += " to " + selectedRows.length + " device(s)?";

        // prompt to install/copy
        int rc = JOptionPane.showConfirmDialog(frame, msg, title, JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (int selectedRow : selectedRows) {
            Device device = model.getDeviceAtRow(selectedRow);
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

    private void handleSetCustom1Command() {
        int[] selectedRows = table.getSelectedRows();
        String customValue = "";
        String message = "";
        if (selectedRows.length == 1) {
            Device device = model.getDeviceAtRow(0);
            customValue = device.custom1;
            message = "Enter Custom Note";
        } else {
            message = "Enter Custom Note for " + selectedRows.length + " devices";
        }

        String result = (String) JOptionPane.showInputDialog(frame,
            message,
            "Custom Note",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            customValue);

        if (TextUtils.notEmpty(result)) {
            for (int selectedRow : selectedRows) {
                Device device = model.getDeviceAtRow(selectedRow);
                DeviceManager.getInstance().setProperty(device, "debug.dm.custom1", result);
                device.custom1 = result;
                model.updateDevice(device);
            }
        }
    }

    private void handleSetCustom2Command() {
        int[] selectedRows = table.getSelectedRows();
        String customValue = "";
        String message = "";
        if (selectedRows.length == 1) {
            Device device = model.getDeviceAtRow(0);
            customValue = device.custom2;
            message = "Enter Custom Note";
        } else {
            message = "Enter Custom Note for " + selectedRows.length + " devices";
        }

        String result = (String) JOptionPane.showInputDialog(frame,
            message,
            "Custom Note",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            customValue);

        if (TextUtils.notEmpty(result)) {
            for (int selectedRow : selectedRows) {
                Device device = model.getDeviceAtRow(selectedRow);
                DeviceManager.getInstance().setProperty(device, "debug.dm.custom2", result);
                device.custom2 = result;
                model.updateDevice(device);
            }
        }
    }

    private void handleScreenshotCommand() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showConfirmDialog(frame, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
            return;
        } else if (selectedRows.length > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(frame, "Take screenshot of " + selectedRows.length + " devices?", "Screenshot", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;
        }
        for (int selectedRow : selectedRows) {
            Device device = model.getDeviceAtRow(selectedRow);
            DeviceManager.getInstance().captureScreenshot(device, this);
        }
    }

    private void handleMirrorCommand() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showConfirmDialog(frame, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
            return;
        } else if (selectedRows.length > 1) {
            // prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(frame,
                "Mirror " + selectedRows.length + " devices?",
                "Mirror Device",
                JOptionPane.YES_NO_OPTION
            );
            if (rc != JOptionPane.YES_OPTION) return;
        }
        for (int selectedRow : selectedRows) {
            Device device = model.getDeviceAtRow(selectedRow);
            DeviceManager.getInstance().mirrorDevice(device, this);
        }
    }

    private void setupToolbar(JPanel panel) {
        JToolBar toolbar = new JToolBar("Applications");

        createButton(toolbar, "icon_scrcpy.png", "Mirror", actionEvent -> handleMirrorCommand());
        createButton(toolbar, "icon_screenshot.png", "Screenshot", actionEvent -> handleScreenshotCommand());
        createButton(toolbar, "icon_install.png", "Install", actionEvent -> handleInstallCommand());
        createButton(toolbar, "icon_restart.png", "Restart", actionEvent -> handleRestartCommand());

        toolbar.add(Box.createHorizontalGlue());

        HintTextField textField = new HintTextField(HINT_FILTER_DEVICES);
        textField.setMaximumSize(new Dimension(350, 40));
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

        panel.add(toolbar, BorderLayout.NORTH);
    }

    private void handleRestartCommand() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showConfirmDialog(frame, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
            return;
        }

        // prompt to install/copy
        int rc = JOptionPane.showConfirmDialog(frame,
            "Restart " + selectedRows.length + " device(s)?",
            "Restart devices?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (int selectedRow : selectedRows) {
            Device device = model.getDeviceAtRow(selectedRow);
            DeviceManager.getInstance().restartDevice(device, this);
        }
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
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showConfirmDialog(frame, "Select 1 or more devices to use this feature", "No devices selected", JOptionPane.DEFAULT_OPTION);
            return;
        }
        FileDialog dialog = new FileDialog(frame, "Select File to Install/Copy to selected devices");
        dialog.setMode(FileDialog.LOAD);
        dialog.setVisible(true);
        File[] fileArr = dialog.getFiles();
        if (fileArr == null || fileArr.length == 0) return;
        handleFilesDropped(Arrays.asList(fileArr));
    }

    private void createButton(JToolBar toolbar, String imageName, String tooltip, ActionListener listener) {
        Image icon;
        try {
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
        button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.addActionListener(listener);
        toolbar.add(button);
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        if (deviceList != null) {
            model.setDeviceList(deviceList);

            emptyView.setVisible(deviceList.size() == 0);
        }
    }

    @Override
    public void handleDeviceUpdated(Device device) {
        model.updateDevice(device);
    }

}
