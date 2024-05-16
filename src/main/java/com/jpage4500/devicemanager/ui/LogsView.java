package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.FilterItem;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.table.utils.LogsCellRenderer;
import com.jpage4500.devicemanager.table.utils.LogsRowFilter;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.EmptyView;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * create and manage device view
 */
public class LogsView extends BaseFrame implements DeviceManager.DeviceLogListener {
    private static final Logger log = LoggerFactory.getLogger(LogsView.class);

    private static final String HINT_FILTER = "Filter...";
    private static final String HINT_SEARCH = "Search...";

    private final Device device;
    private final JFrame deviceFrame;

    public CustomTable table;
    public LogsTableModel model;

    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;
    private JCheckBox autoScrollCheckBox;

    private LogsRowFilter rowFilter;
    private TableRowSorter<LogsTableModel> sorter;
    public int selectedColumn = -1;

    public boolean isLoggedPaused; // true when user clicks on 'stop logging'

    public LogsView(JFrame deviceFrame, Device device) {
        super("logs");
        this.deviceFrame = deviceFrame;
        this.device = device;
        initalizeUi();
        setTitle("Browse " + device.getDisplayName());
    }

    protected void initalizeUi() {
        // ** MAIN PANEL **
        // ---- [toolbar] -----
        // -- [ split pane ] --
        // --- [status bar] ---
        JPanel mainPanel = new JPanel(new BorderLayout());

        // NOTE: this breaks dragging the scrollbar on Mac
        //getRootPane().putClientProperty("apple.awt.draggableWindowBackground", true);

        toolbar = new JToolBar("Applications");
        setupToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        JPanel leftPanel = new JPanel(new BorderLayout());
        HintTextField filterField = new HintTextField(HINT_FILTER, this::filterDevices);
//        filterField.setPreferredSize(new Dimension(150, 30));
//        filterField.setMinimumSize(new Dimension(10, 40));
//        filterField.setMaximumSize(new Dimension(200, 40));
        leftPanel.add(filterField, BorderLayout.NORTH);

        JList<FilterItem> filterList = new JList<>();
        leftPanel.add(filterList, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        table = new CustomTable("logs");
        setupTable();
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        // statusbar
        statusBar = new StatusBar();
        setupStatusBar();
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                log.debug("windowClosing: ");
                table.persist();
                stopLogging();
                setVisible(false);
                dispose();
            }
        });
        //setDefaultCloseOperation(JHIDE_ON_CLOSE);

        setupMenuBar();
        setupPopupMenu();

        setContentPane(mainPanel);
        setVisible(true);
        table.requestFocus();
    }

    @Override
    public void show() {
        super.show();
        startLogging();
    }

    private void setupStatusBar() {
        autoScrollCheckBox = new JCheckBox("Auto Scroll");
        autoScrollCheckBox.setBorder(new EmptyBorder(0, 10, 0, 10));
        autoScrollCheckBox.setHorizontalAlignment(SwingConstants.TRAILING);
        autoScrollCheckBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scrollToFollow();
            }
        });
        statusBar.setRightComponent(autoScrollCheckBox);
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> {
            setVisible(false);
            dispose();
        });

        // [CMD + 1] = show devices
        createAction(windowMenu, "Show Devices", KeyEvent.VK_1, e -> {
            deviceFrame.toFront();
        });

        JMenu logsMenu = new JMenu("Logs");

        // [CMD + ENTER] = toggle auto scroll
        createAction(logsMenu, "Auto Scroll", KeyEvent.VK_ENTER, e -> {
            autoScrollCheckBox.setSelected(!autoScrollCheckBox.isSelected());
            scrollToFollow();
        });

        // [CMD + KEY_UP] = scroll to top
        createAction(logsMenu, "Scoll to top", KeyEvent.VK_UP, e -> {
            autoScrollCheckBox.setSelected(false);
            table.scrollToTop();
        });

        // [CMD + KEY_DOWN] = scroll to bottom
        createAction(logsMenu, "Scoll to bottom", KeyEvent.VK_DOWN, e -> {
            table.scrollToBottom();
        });

        // [CMD + KEY_UP] = page up
        createOptionAction(logsMenu, "Page Up", KeyEvent.VK_UP, e -> {
            table.pageUp();
        });

        // [CMD + KE_DOWN] = page down
        createOptionAction(logsMenu, "Page Down", KeyEvent.VK_DOWN, e -> {
            table.pageDown();
        });

        // [CMD + K] = clear logs
        createAction(logsMenu, "Clear logs", KeyEvent.VK_K, e -> {
            model.clearLogs();
        });

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(logsMenu);
        setJMenuBar(menubar);
    }

    private void setupTable() {
        // prevent sorting
        //table.getTableHeader().setEnabled(false);

        model = new LogsTableModel();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(model);
        table.setDefaultRenderer(LogEntry.class, new LogsCellRenderer());

        // default column sizes
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(LogsTableModel.Columns.LEVEL.ordinal()).setPreferredWidth(28);
        columnModel.getColumn(LogsTableModel.Columns.PID.ordinal()).setPreferredWidth(60);
        columnModel.getColumn(LogsTableModel.Columns.TID.ordinal()).setPreferredWidth(60);
        columnModel.getColumn(LogsTableModel.Columns.DATE.ordinal()).setPreferredWidth(130);
        columnModel.getColumn(LogsTableModel.Columns.APP.ordinal()).setPreferredWidth(150);
        columnModel.getColumn(LogsTableModel.Columns.MSG.ordinal()).setPreferredWidth(700);

        table.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            if (listSelectionEvent.getValueIsAdjusting()) return;
            refreshUi();

            // if row selected, stop auto-scroll
            int numSelected = table.getSelectedRowCount();
            if (numSelected > 0 && autoScrollCheckBox.isSelected()) {
                log.debug("setupTable: disabled auto scroll");
                autoScrollCheckBox.setSelected(false);
            }
        });

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

        rowFilter = new LogsRowFilter();
        sorter = new TableRowSorter<>(model);
        sorter.setRowFilter(rowFilter);
        table.setRowSorter(sorter);
    }

    private void stopLogging() {
        isLoggedPaused = true;
        DeviceManager.getInstance().stopLogging(device);
    }

    private void startLogging() {
        isLoggedPaused = false;
        DeviceManager.getInstance().startLogging(device, this);
    }

    private void scrollToFollow() {
        if (autoScrollCheckBox.isSelected()) {
            table.scrollToBottom();
        }
    }

    private void refreshUi() {
        // viewing X
        int rowCount = table.getRowCount();
        int totalRows = model.getRowCount();
        String msg = "viewing " + rowCount;
        if (totalRows > 0 && totalRows > rowCount) {
            msg += " / " + totalRows;
        }
        statusBar.setLeftLabel(msg);
    }

    private void setupPopupMenu() {
        JPopupMenu popupMenu = new JPopupMenu();
        table.setComponentPopupMenu(popupMenu);
    }

    private void setupToolbar() {
        toolbar.setRollover(true);
        toolbar.removeAll();

        createToolbarButton(toolbar, "icon_open_folder.png", null, "Open Folder", actionEvent -> startLogging());
        createToolbarButton(toolbar, "icon_download.png", null, "Download Files", actionEvent -> stopLogging());
        toolbar.addSeparator();

        toolbar.add(Box.createHorizontalGlue());

        HintTextField searchField = new HintTextField(HINT_SEARCH, this::doSearch);
        searchField.setPreferredSize(new Dimension(150, 30));
        searchField.setMinimumSize(new Dimension(10, 40));
        searchField.setMaximumSize(new Dimension(200, 40));
        toolbar.add(searchField);

        createToolbarButton(toolbar, "icon_refresh.png", null, "Refresh Device List", actionEvent -> refreshUi());
    }

    private void doSearch(String text) {
        log.debug("search: {}", text);
        if (TextUtils.isEmpty(text) || TextUtils.equals(text, HINT_SEARCH)) {
            model.setSearchText(null);
        } else {
            model.setSearchText(text);
        }
    }

    private void filterDevices(String text) {
        log.debug("filterDevices: filter:{}", text);
        if (TextUtils.isEmpty(text) || TextUtils.equals(text, HINT_FILTER)) {
            rowFilter.setFilter(null);
            statusBar.setCenterLabel(null);
        } else {
            rowFilter.setFilter(text);
            statusBar.setCenterLabel("Filter: " + text);
        }
        model.fireTableDataChanged();
    }

    @Override
    public void handleLogEntries(List<LogEntry> logEntryList) {
        // save log entries as they'll get cleared after this method returns
        List<LogEntry> logList = new ArrayList<>(logEntryList);
        SwingUtilities.invokeLater(() -> {
            model.addLogEntry(logList);
            scrollToFollow();
            refreshUi();
        });
    }

    @Override
    public void handleProcessMap(Map<String, String> processMap) {
        SwingUtilities.invokeLater(() -> {
            model.setProcessMap(processMap);
        });
    }
}
