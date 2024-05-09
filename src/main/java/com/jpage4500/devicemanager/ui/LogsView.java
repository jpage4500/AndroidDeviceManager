package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.*;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.table.LogsTableModel;

import net.coobird.thumbnailator.Thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.*;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * create and manage device view
 */
public class LogsView {
    private static final Logger log = LoggerFactory.getLogger(LogsView.class);

    public static final String PREF_DOWNLOAD_FOLDER = "PREF_DOWNLOAD_FOLDER";
    public static final String PREF_GO_TO_FOLDER_LIST = "PREF_GO_TO_FOLDER_LIST";
    private static final String HINT_FILTER_DEVICES = "Filter files...";

    private JFrame deviceFrame;

    public CustomTable table;
    public LogsTableModel model;

    public CustomFrame frame;
    public JPanel panel;
    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;

    public int selectedColumn = -1;

    private Device selectedDevice;

    public LogsView(JFrame deviceFrame) {
        this.deviceFrame = deviceFrame;
        initalizeUi();
    }

    public void setDevice(Device selectedDevice) {
        this.selectedDevice = selectedDevice;
        frame.setTitle("Browse " + selectedDevice.getDisplayName());
        startLogging();
        show();
    }

    public void show() {
        frame.setVisible(true);
    }

    private void initalizeUi() {
        frame = new CustomFrame("logs");
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
            }
        });
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                table.persist();
            }
        });
        table = new CustomTable(null);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        model = new LogsTableModel();
        table.setModel(model);
        //table.setDefaultRenderer(RemoteFile.class, new IconTableCellRenderer());

        // -- CMD+W = close window --
        Action closeAction = new AbstractAction("Close Window") {
            @Override
            public void actionPerformed(ActionEvent e) {
                log.debug("actionPerformed: CLOSE");
                frame.setVisible(false);
                frame.dispose();
            }
        };
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        KeyStroke closeKey = KeyStroke.getKeyStroke(KeyEvent.VK_W, mask);
        closeAction.putValue(Action.ACCELERATOR_KEY, closeKey);

        // -- CMD+~ = show devices --
        Action switchAction = new AbstractAction("Show Devices") {
            @Override
            public void actionPerformed(ActionEvent e) {
                deviceFrame.toFront();
            }
        };
        KeyStroke switchKey = KeyStroke.getKeyStroke(KeyEvent.VK_1, mask);
        switchAction.putValue(Action.ACCELERATOR_KEY, switchKey);

        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu("Window");
        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.setAction(closeAction);
        menu.add(closeItem);
        JMenuItem switchItem = new JMenuItem("Show Devices");
        switchItem.setAction(switchAction);
        menu.add(switchItem);
        menubar.add(menu);
        frame.setJMenuBar(menubar);

        InputMap inputMap = table.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = table.getActionMap();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBackground(Color.RED);
        panel.add(scrollPane, BorderLayout.CENTER);

        // setup toolbar
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // statusbar
        statusBar = new StatusBar();
        //statusBar.setLeftLabelListener(this::handleGoToFolder);
        panel.add(statusBar, BorderLayout.SOUTH);

        frame.setContentPane(panel);
        frame.setVisible(true);

        JRootPane rootPane = SwingUtilities.getRootPane(table);
        emptyView = new EmptyView("No Files");
        rootPane.setGlassPane(emptyView);
        emptyView.setOpaque(false);
        emptyView.setVisible(true);

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
                } else if (e.getClickCount() == 2) {
                    // double-click
                    selectedColumn = -1;
                }
            }
        });

        setupPopupMenu();

        table.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            if (!listSelectionEvent.getValueIsAdjusting()) {
                refreshUi();
            }
        });
        table.requestFocus();
    }

    private void stopLogging() {
        if (selectedDevice == null) return;
        DeviceManager.getInstance().stopLogging(selectedDevice);
    }

    private void startLogging() {
        if (selectedDevice == null) return;
        DeviceManager.getInstance().startLogging(selectedDevice, new DeviceManager.DeviceLogListener() {
            @Override
            public void handleLogEntries(List<LogEntry> logEntryList) {
                model.addLogEntry(logEntryList);
            }

            @Override
            public void handleLogEntry(LogEntry logEntry) {
                model.addLogEntry(logEntry);
            }
        });
    }

    private void refreshUi() {
        // file path
        //statusBar.setLeftLabel(selectedPath);

        // selected row(s)
        int selectedRowCount = table.getSelectedRowCount();
        int rowCount = table.getRowCount();
        if (selectedRowCount > 0) {
            statusBar.setRightLabel("selected: " + selectedRowCount + " / " + rowCount);
        } else {
            statusBar.setRightLabel("total: " + rowCount);
        }
        emptyView.setVisible(rowCount == 0);
    }

    private void setupPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        table.setComponentPopupMenu(popupMenu);
    }

    private void setupToolbar() {
        if (toolbar == null) {
            toolbar = new JToolBar("Applications");
            toolbar.setRollover(true);
        } else {
            toolbar.removeAll();
        }

        createButton(toolbar, "icon_open_folder.png", "Start", "Open Folder", actionEvent -> startLogging());
        createButton(toolbar, "icon_download.png", "Stop", "Download Files", actionEvent -> stopLogging());
        toolbar.addSeparator();

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

        createButton(toolbar, "icon_refresh.png", "Refresh", "Refresh Device List", actionEvent -> refreshUi());
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

    private Image getIcon(String imageName) {
        Image icon = null;
        try {
            // library offers MUCH better image scaling than ImageIO
            icon = Thumbnails.of(getClass().getResource("/images/" + imageName)).size(40, 40).asBufferedImage();
            //Image image = ImageIO.read(getClass().getResource("/images/" + imageName));
        } catch (Exception e) {
            log.debug("createButton: Exception:{}", e.getMessage());
        }
        return icon;
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

}
