package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.FilterItem;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.table.utils.LogsCellRenderer;
import com.jpage4500.devicemanager.table.utils.LogsRowSorter;
import com.jpage4500.devicemanager.table.utils.TableColumnAdjuster;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public class LogsScreen extends BaseScreen implements DeviceManager.DeviceLogListener {
    private static final Logger log = LoggerFactory.getLogger(LogsScreen.class);

    private static final String HINT_FILTER = "Filter...";
    private static final String HINT_SEARCH = "Search...";

    private final Device device;
    private final DeviceScreen deviceScreen;

    public CustomTable table;
    public LogsTableModel model;

    public StatusBar statusBar;
    public JToolBar toolbar;
    private JCheckBox autoScrollCheckBox;
    private HintTextField searchField;
    private HintTextField filterField;
    private JList<FilterItem> filterList;

    private LogsRowSorter sorter;

    public JButton logButton;
    public boolean isLoggedPaused; // true when user clicks on 'stop logging'

    public LogsScreen(DeviceScreen deviceScreen, Device device) {
        super("logs");
        this.deviceScreen = deviceScreen;
        this.device = device;
        //setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        initalizeUi();
        updateDeviceState();
    }

    public void updateDeviceState() {
        //log.trace("updateDeviceState: ONLINE:{}", device.isOnline);
        if (device.isOnline) {
            setTitle("Logs: [" + device.getDisplayName() + "]");
            startLogging();
        } else {
            setTitle("OFFLINE [" + device.getDisplayName() + "]");
            stopLogging();
        }
    }

    protected void initalizeUi() {
        // ** MAIN PANEL **
        // ---- [toolbar] -----
        // -- [ split pane ] --
        // --- [status bar] ---
        JPanel mainPanel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        setupToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // -- left panel --
        JPanel leftPanel = new JPanel(new BorderLayout());
        filterField = new HintTextField(HINT_FILTER, this::filterDevices);
        leftPanel.add(filterField, BorderLayout.NORTH);

        filterList = new JList<>();
        setupFilterList();
        leftPanel.add(filterList, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());

        // -- table --
        table = new CustomTable("logs");
        setupTable();
        rightPanel.add(table.getScrollPane(), BorderLayout.CENTER);

        // statusbar
        statusBar = new StatusBar();
        setupStatusBar();
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        setupMenuBar();

        setContentPane(mainPanel);
        setVisible(true);
        table.requestFocus();
        autoScrollCheckBox.setSelected(true);
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSED -> {
                // stop logging when window is closed
                stopLogging();
                table.persist();
            }
            case ACTIVATED -> {
                // start logging if user didn't stop
                if (!isLoggedPaused) {
                    startLogging();
                }
            }
        }
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
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> {
            setVisible(false);
            dispose();
        });

        // [CMD + 1] = show devices
        createCmdAction(windowMenu, "Show Devices", KeyEvent.VK_1, e -> deviceScreen.toFront());

        // [CMD + 2] = show explorer
        createCmdAction(windowMenu, "Browse Files", KeyEvent.VK_2, e -> deviceScreen.handleBrowseCommand());

        // [CMD + T] = hide toolbar
        createCmdAction(windowMenu, "Hide Toolbar", KeyEvent.VK_T, e -> hideToolbar());

        JMenu logsMenu = new JMenu("Logs");

        // [CMD + ENTER] = toggle auto scroll
        createCmdAction(logsMenu, "Auto Scroll", KeyEvent.VK_ENTER, e -> {
            autoScrollCheckBox.setSelected(!autoScrollCheckBox.isSelected());
            scrollToFollow();
        });

        // [CMD + KEY_UP] = scroll to top
        createCmdAction(logsMenu, "Scoll to top", KeyEvent.VK_UP, e -> {
            autoScrollCheckBox.setSelected(false);
            table.scrollToTop();
        });

        // [CMD + KEY_DOWN] = scroll to bottom
        createCmdAction(logsMenu, "Scoll to bottom", KeyEvent.VK_DOWN, e -> table.scrollToBottom());

        // [CMD + KEY_UP] = page up
        createOptionAction(logsMenu, "Page Up", KeyEvent.VK_UP, e -> table.pageUp());

        // [CMD + KE_DOWN] = page down
        createOptionAction(logsMenu, "Page Down", KeyEvent.VK_DOWN, e -> table.pageDown());

        // [CMD + K] = clear logs
        createCmdAction(logsMenu, "Clear logs", KeyEvent.VK_K, e -> model.clearLogs());

        // [CMD + F] = focus search field
        createCmdAction(windowMenu, "Search for...", KeyEvent.VK_1, e -> searchField.requestFocus());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(logsMenu);
        setJMenuBar(menubar);
    }

    private void hideToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    private void setupTable() {
        model = new LogsTableModel();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(model);
        table.setDefaultRenderer(LogEntry.class, new LogsCellRenderer());

        // default column sizes
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(LogsTableModel.Columns.LEVEL.ordinal()).setPreferredWidth(28);
        columnModel.getColumn(LogsTableModel.Columns.PID.ordinal()).setPreferredWidth(60);
        columnModel.getColumn(LogsTableModel.Columns.TID.ordinal()).setPreferredWidth(60);
        columnModel.getColumn(LogsTableModel.Columns.DATE.ordinal()).setPreferredWidth(159);
        columnModel.getColumn(LogsTableModel.Columns.APP.ordinal()).setPreferredWidth(150);
        columnModel.getColumn(LogsTableModel.Columns.MSG.ordinal()).setPreferredWidth(700);

        // restore user-defined column sizes
        table.restore();

        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;

            // if row selected, stop auto-scroll
            int numSelected = table.getSelectedRowCount();
            if (numSelected > 0 && autoScrollCheckBox.isSelected()) {
                log.debug("setupTable: disabled auto scroll");
                autoScrollCheckBox.setSelected(false);
            }
        });

        table.setPopupMenuListener((row, column) -> {
            if (row == -1) {
                JPopupMenu popupMenu = new JPopupMenu();
                JMenuItem sizeToFitItem = new JMenuItem("Size to Fit");
                sizeToFitItem.addActionListener(actionEvent -> {
                    TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 0);
                    int tableCol = table.convertColumnIndexToView(column);
                    adjuster.adjustColumn(tableCol);
                });
                popupMenu.add(sizeToFitItem);
                return popupMenu;
            }

            LogsTableModel.Columns columnType = model.getColumnType(column);
            switch (columnType) {
                case APP:
                case TID:
                case PID:
                case LEVEL:
                case TAG:
                    // filter by value
                    String text = model.getTextValue(row, column);
                    JPopupMenu popupMenu = new JPopupMenu();
                    JMenuItem copyFieldItem = new JMenuItem("Add Filter");
                    copyFieldItem.addActionListener(actionEvent -> handleAddFilter(columnType, text));
                    popupMenu.add(copyFieldItem);
                    break;
            }

            return null;
        });

        sorter = new LogsRowSorter(model);
        table.setRowSorter(sorter);

        table.getScrollPane().addMouseWheelListener(event -> {
            int wheelRotation = event.getWheelRotation();
            if (wheelRotation == -1) {
                // scrolling UP - disable auto-scroll
                if (autoScrollCheckBox.isSelected()) {
                    // only if user scrolls past last few lines
                    int lastVisibleRow = getLastVisibleRow();
                    if (lastVisibleRow > 0) {
                        autoScrollCheckBox.setSelected(false);
                    }
                }
            } else if (wheelRotation == 1) {
                // scrolling DOWN
                if (!autoScrollCheckBox.isSelected()) {
                    int lastVisibleRow = getLastVisibleRow();
                    if (lastVisibleRow == -1) {
                        autoScrollCheckBox.setSelected(true);
                        scrollToFollow();
                    }
                }
            }
        });
    }

    private void handleAddFilter(LogsTableModel.Columns columnType, String text) {
        LogFilter filter = LogFilter.parse(columnType.name().toLowerCase() + ":" + text);
        filterField.setText(filter.toString());
    }

    private int getLastVisibleRow() {
        Rectangle visibleRect = table.getVisibleRect();
        return table.rowAtPoint(new Point(visibleRect.x, visibleRect.y + visibleRect.height));
    }

    private void stopLogging() {
        DeviceManager.getInstance().stopLogging(device);
    }

    private void startLogging() {
        if (device.isOnline && !DeviceManager.getInstance().isLogging(device)) {
            Long startTime = model.getLastLogTime();
            if (startTime == null) {
                // by default only display logs from the last few hours
                // - can speed up initial launch
                startTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2);
            }
            DeviceManager.getInstance().startLogging(device, startTime, this);
        }
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

    private void setupToolbar() {
        toolbar.setRollover(true);

        logButton = createSmallToolbarButton(toolbar, null, null, "Start Logging", actionEvent -> toggleLoggingButton());
        updateLoggingButton();

        toolbar.add(Box.createHorizontalGlue());

        // toolbar.addSeparator(new Dimension(10, 0));

        searchField = new HintTextField(HINT_SEARCH, this::doSearch);
        searchField.setPreferredSize(new Dimension(250, 30));
        searchField.setMinimumSize(new Dimension(10, 30));
        searchField.setMaximumSize(new Dimension(250, 30));
        toolbar.add(searchField);

        toolbar.addSeparator(new Dimension(10, 0));

        createSmallToolbarButton(toolbar, "icon_trash.png", "Clear", "Clear Logs", actionEvent -> clearLogs());
    }

    private void clearLogs() {
        model.clearLogs();
    }

    private void toggleLoggingButton() {
        isLoggedPaused = !isLoggedPaused;
        updateLoggingButton();
        if (isLoggedPaused) {
            stopLogging();
        } else {
            startLogging();
        }
    }

    private void updateLoggingButton() {
        String imageName = isLoggedPaused ? "icon_play.png" : "icon_stop.png";
        ImageIcon icon = UiUtils.getImageIcon(imageName, 20, 20);
        logButton.setIcon(icon);
        logButton.setText(isLoggedPaused ? "Start" : "Stop");
    }

    private void doSearch(String text) {
        if (TextUtils.isEmpty(text)) {
            model.setSearchText(null);
        } else {
            model.setSearchText(text);
        }
    }

    private void setupFilterList() {
        Preferences preferences = Preferences.userRoot();
        String prefListStr = preferences.get("PREF_FILTER_LIST", null);
        List<FilterItem> filterItemList = GsonHelper.stringToList(prefListStr, FilterItem.class);

        // add basic log level filters
        addLogLevel(filterItemList, "All Messages", null);
        addLogLevel(filterItemList, "Log Level Debug+", "level:D+");
        addLogLevel(filterItemList, "Log Level Info+", "level:I+");
        addLogLevel(filterItemList, "Log Level Warn+", "level:W+");
        addLogLevel(filterItemList, "Log Level Error+", "level:E");

        // sort A-Z (name)
        filterItemList.sort((lhs, rhs) -> TextUtils.compareToIgnoreCase(lhs.name, rhs.name));

        filterList.setListData(filterItemList.toArray(new FilterItem[0]));
        filterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filterList.addListSelectionListener(e -> filterDevices(filterField.getCleanText()));
    }

    private void addLogLevel(List<FilterItem> filterItemList, String label, String filter) {
        FilterItem item = new FilterItem();
        item.name = label;
        item.filter = LogFilter.parse(filter);
        log.debug("addLogLevel: {}, {}", label, item.filter);
        filterItemList.add(item);
    }

    private void filterDevices(String text) {
        List<LogFilter> list = new ArrayList<>();

        // get currently selected filter
        List<FilterItem> selectedList = filterList.getSelectedValuesList();
        StringBuilder sb = new StringBuilder();
        for (FilterItem item : selectedList) {
            if (item.filter != null) list.add(item.filter);
            if (!sb.isEmpty()) sb.append(" && ");
            sb.append(item.name);
        }

        if (TextUtils.notEmpty(text)) {
            LogFilter searchFilter = LogFilter.parse("*:*" + text + "*");
            log.debug("filterDevices: {}", searchFilter);
            list.add(searchFilter);
            if (!sb.isEmpty()) sb.append(" && ");
            sb.append(text);
        }

        sorter.setFilter(list.toArray(new LogFilter[0]));

        // TODO: set label
        statusBar.setCenterLabel(sb.toString());
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
        SwingUtilities.invokeLater(() -> model.setProcessMap(processMap));
    }

}
