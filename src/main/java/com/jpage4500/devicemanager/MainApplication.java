package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.utils.MyDragDropListener;
import com.jpage4500.devicemanager.viewmodel.DeviceTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.*;

import static javax.swing.JOptionPane.YES_OPTION;

class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private static final String FRAME_X = "frame-x";
    private static final String FRAME_Y = "frame-y";
    private static final String FRAME_W = "frame-w";
    private static final String FRAME_H = "frame-h";

    private JPanel panel;
    private JTable table;
    private JFrame frame;
    private DeviceTableModel model;

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
        logger.setDebugLevel(Log.VERBOSE);
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

        final Taskbar taskbar = Taskbar.getTaskbar();
        try {
            Image image = ImageIO.read(getClass().getResource("/images/logo.png"));
            taskbar.setIconImage(image);
        } catch (final Exception e) {
            log.error("Exception: {}", e.getMessage());
        }

        frame = new JFrame("Device Manager");
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        restoreFrame();
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                saveFrameSize();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                saveFrameSize();
            }
        });
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        table = new JTable();
        table.setRowHeight(30);
        //table.getTableHeader().setBackground(Color.LIGHT_GRAY);

        model = new DeviceTableModel();
        table.setModel(model);

        JScrollPane scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);

        // setup toolbar
        setupToolbar(panel);

        frame.setContentPane(panel);
        //frame.pack();
        frame.setVisible(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("initializeUI: EXIT");
            DeviceManager.getInstance().handleExit();
        }));

        // Create the drag and drop listener
        MyDragDropListener myDragDropListener = new MyDragDropListener(table);

        // Connect the label with a drag and drop listener
        new DropTarget(table, myDragDropListener);

        DeviceManager.getInstance().listDevices(new DeviceManager.DeviceListUpdatedListener() {
            @Override
            public void handleDevicesUpdated(List<Device> deviceList) {
                model.setDeviceList(deviceList);
            }

            @Override
            public void handleDeviceUpdated(Device device) {
                model.updateDevice(device);
            }
        });

        table.setAutoCreateRowSorter(true);
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
                    log.debug("mouseClicked: RIGHT-CLICK:{}", row);
                } else if (e.getClickCount() == 2) {
                    log.debug("mouseClicked: DOUBLE-CLICK:{}", row);
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

        JMenuItem serverItem = new JMenuItem("Set Server...");
        serverItem.addActionListener(actionEvent -> handleSetServerCommand());
        popupMenu.add(serverItem);

        JMenuItem notesItem = new JMenuItem("Set Notes...");
        notesItem.addActionListener(actionEvent -> handleSetNotesCommand());
        popupMenu.add(notesItem);

        table.setComponentPopupMenu(popupMenu);
    }

    private void handleSetNotesCommand() {
    }

    private void handleSetServerCommand() {

    }

    private void handleScreenshotCommand() {

    }

    private void handleMirrorCommand() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length > 1) {
            // TODO: prompt to open multiple devices at once
            int rc = JOptionPane.showConfirmDialog(frame,
                "Mirror " + selectedRows.length + " devices?",
                "Mirror Device",
                JOptionPane.YES_NO_OPTION
            );
            if (rc != JOptionPane.YES_OPTION) return;
        }
        for (int selectedRow : selectedRows) {
            Device device = model.getDeviceAtRow(selectedRow);
            DeviceManager.getInstance().mirrorDevice(device);
        }

    }

    private void setupToolbar(JPanel panel) {
        // TODO
//        JToolBar toolbar = new JToolBar("Applications");
//
//        JButton btnPhone = new JButton(new ImageIcon("images/Phone.png"));
//        btnPhone.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                JOptionPane.showMessageDialog(frame, "Phone clicked");
//            }
//        });
//
//        toolbar.add(btnCalendar);
//        panel.add(toolbar, BorderLayout.NORTH);
    }

    private void saveFrameSize() {
        Preferences prefs = Preferences.userRoot();
        prefs.putInt(FRAME_X, frame.getX());
        prefs.putInt(FRAME_Y, frame.getY());
        prefs.putInt(FRAME_W, frame.getWidth());
        prefs.putInt(FRAME_H, frame.getHeight());
    }

    private void restoreFrame() {
        Preferences prefs = Preferences.userRoot();
        int x = prefs.getInt(FRAME_X, 200);
        int y = prefs.getInt(FRAME_Y, 200);
        int w = prefs.getInt(FRAME_W, 500);
        int h = prefs.getInt(FRAME_H, 300);

        log.debug("restoreFrame: x:{}, y:{}, w:{}, h:{}", x, y, w, h);
        frame.setLocation(x, y);
        frame.setSize(w, h);
    }


}
