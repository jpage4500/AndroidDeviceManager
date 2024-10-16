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
import com.jpage4500.devicemanager.ui.dialog.AddFilterDialog;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    // filter logs
    private HintTextField filterField;
    private JList<FilterItem> filterList;
    private int numSystemFilters;

    private LogsRowSorter sorter;
    private MessageViewScreen viewScreen;

    public JButton logButton;
    public boolean isLoggedPaused; // true when user clicks on 'stop logging'

    public LogsScreen(DeviceScreen deviceScreen, Device device) {
        super("logs-" + device.serial, 1100, 800);
        this.deviceScreen = deviceScreen;
        this.device = device;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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

        // ** left panel **
        JPanel leftPanel = new JPanel(new BorderLayout());

        // -- filter text --
        filterField = new HintTextField(HINT_FILTER, this::filterDevices);
        leftPanel.add(filterField, BorderLayout.NORTH);

        // -- filter list --
        filterList = new JList<>();
        setupFilterList();
        leftPanel.add(filterList, BorderLayout.CENTER);

        // -- add filter button --
        JButton addFilterButton = new JButton("Add Filter");
        addFilterButton.setIcon(UiUtils.getImageIcon("icon_add.png", 20));
        addFilterButton.addActionListener(this::handleAddFilterClicked);
        leftPanel.add(addFilterButton, BorderLayout.SOUTH);

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

        // restore previous filter
        String recentFilterText = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_RECENT_MESSAGE_FILTER);
        filterField.setText(recentFilterText);
    }

    private void handleAddFilterClicked(ActionEvent actionEvent) {
        // TODO...
        AddFilterDialog.showAddFilterDialog(this, null);
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSED -> {
                // stop logging when window is closed
                stopLogging();
                saveFrameSize();
                table.saveTable();
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
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> closeWindow());

        // [CMD + 1] = show devices
        createCmdAction(windowMenu, DeviceScreen.SHOW_DEVICE_LIST, KeyEvent.VK_1, e -> {
            deviceScreen.setVisible(true);
            deviceScreen.toFront();
        });

        // [CMD + 2] = show explorer
        createCmdAction(windowMenu, DeviceScreen.SHOW_BROWSE, KeyEvent.VK_2, e -> deviceScreen.handleBrowseCommand(device));

        // [CMD + T] = hide toolbar
        createCmdAction(windowMenu, "Hide Toolbar", KeyEvent.VK_T, e -> hideToolbar());

        JMenu logsMenu = new JMenu("Logs");

        // [CMD + ENTER] = toggle auto scroll
        createCmdAction(logsMenu, "Auto Scroll", KeyEvent.VK_ENTER, e -> {
            autoScrollCheckBox.setSelected(!autoScrollCheckBox.isSelected());
            scrollToFollow();
        });

        // [CMD + K] = clear logs
        createCmdAction(logsMenu, "Clear logs", KeyEvent.VK_K, e -> model.clearLogs());

        // [CMD + KEY_UP] = scroll to top
        createCmdAction(logsMenu, "Scoll to top", KeyEvent.VK_UP, e -> {
            autoScrollCheckBox.setSelected(false);
            table.scrollToTop();
        });

        JMenu editMenu = new JMenu("Edit");

        // [CMD + KEY_DOWN] = scroll to bottom
        createCmdAction(editMenu, "Scoll to bottom", KeyEvent.VK_DOWN, e -> table.scrollToBottom());

        // [CMD + KEY_UP] = page up
        createOptionAction(editMenu, "Page Up", KeyEvent.VK_UP, e -> table.pageUp());

        // [CMD + KE_DOWN] = page down
        createOptionAction(editMenu, "Page Down", KeyEvent.VK_DOWN, e -> table.pageDown());

        // [CMD + +] = increase font size
        createCmdAction(editMenu, "Increase Font Size", KeyEvent.VK_EQUALS, e -> increaseFontSize());

        // [CMD + -] = increase font size
        createCmdAction(editMenu, "Decrease Font Size", KeyEvent.VK_MINUS, e -> decreaseFontSize());

        // [CMD + F] = focus search field
        createCmdAction(editMenu, "Search for...", KeyEvent.VK_F, e -> searchField.requestFocus());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(editMenu);
        menubar.add(logsMenu);
        setJMenuBar(menubar);
    }

    public void increaseFontSize() {
        int fontOffset = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, 0);
        fontOffset++;
        setFontSize(fontOffset);
    }

    private void setFontSize(int fontOffset) {
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, fontOffset);

        LogsCellRenderer cellRenderer = (LogsCellRenderer) table.getDefaultRenderer(LogEntry.class);
        cellRenderer.notifyFontChanged();
        model.fireTableDataChanged();
    }

    public void decreaseFontSize() {
        int fontOffset = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, 0);
        fontOffset--;
        setFontSize(fontOffset);
    }

    private void closeWindow() {
        log.trace("closeWindow: {}", device.getDisplayName());
        // save last filter
        String filterText = filterField.getCleanText();
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_RECENT_MESSAGE_FILTER, filterText);
        //stopLogging();
        deviceScreen.handleLogsClosed(device.serial);
        dispose();
    }

    private void hideToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    private void setupTable() {
        model = new LogsTableModel();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(model);
        table.setDefaultRenderer(LogEntry.class, new LogsCellRenderer());

        // restore user-defined column sizes
        if (!table.restoreTable()) {
            // use some default column sizes
            table.setPreferredColWidth(LogsTableModel.Columns.LEVEL.toString(), 28);
            table.setPreferredColWidth(LogsTableModel.Columns.PID.toString(), 60);
            table.setPreferredColWidth(LogsTableModel.Columns.TID.toString(), 60);
            table.setPreferredColWidth(LogsTableModel.Columns.DATE.toString(), 159);
            table.setPreferredColWidth(LogsTableModel.Columns.APP.toString(), 150);
            table.setPreferredColWidth(LogsTableModel.Columns.TAG.toString(), 200);
            table.setPreferredColWidth(LogsTableModel.Columns.MSG.toString(), 700);
        }

        table.setMaxColWidth(LogsTableModel.Columns.LEVEL.toString(), 35);
        table.setMaxColWidth(LogsTableModel.Columns.PID.toString(), 100);
        table.setMaxColWidth(LogsTableModel.Columns.TID.toString(), 100);

        // ENTER -> view message
        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, "Enter");
        table.getActionMap().put("Enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogClicked();
            }
        });

        // CMD+SHIFT+V -> view message
        KeyStroke view = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK);
        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(view, "View");
        table.getActionMap().put("View", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogClicked();
            }
        });

//        // CMD+PLUS -> inceaase font
//        KeyStroke increaseFont = KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.META_DOWN_MASK);
//        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(increaseFont, "Increase Font Size");
//        table.getActionMap().put("Increase Font Size", new AbstractAction() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                increaseFontSize();
//            }
//        });
//
//        // CMD+MINUS -> decrease font
//        KeyStroke decreaseFont = KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.META_DOWN_MASK);
//        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(decreaseFont, "Decrease Font Size");
//        table.getActionMap().put("Decrease Font Size", new AbstractAction() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                decreaseFontSize();
//            }
//        });

        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;

            // if row selected, stop auto-scroll
            int numSelected = table.getSelectedRowCount();
            if (numSelected > 0 && autoScrollCheckBox.isSelected()) {
                log.trace("setupTable: disabled auto scroll");
                autoScrollCheckBox.setSelected(false);
            }
        });

        table.setPopupMenuListener((row, column) -> {
            JPopupMenu popupMenu = new JPopupMenu();
            if (row == -1) {
                UiUtils.addPopupMenuItem(popupMenu, "Size to Fit", actionEvent -> {
                    TableColumnAdjuster adjuster = new TableColumnAdjuster(table, 0);
                    int tableCol = table.convertColumnIndexToView(column);
                    adjuster.adjustColumn(tableCol);
                });
                return popupMenu;
            }

            int selectedRows = table.getSelectedRowCount();
            if (selectedRows == 1) {
                LogsTableModel.Columns columnType = model.getColumnType(column);
                switch (columnType) {
                    case APP:
                    case TID:
                    case PID:
                    case LEVEL:
                    case TAG:
                        // filter by value
                        String text = model.getTextValue(row, column);
                        UiUtils.addPopupMenuItem(popupMenu, "Add Filter", actionEvent -> handleQuickAddFilter(columnType, text));
                }
            }

            UiUtils.addPopupMenuItem(popupMenu, "Copy", actionEvent -> handleCopyClicked());
            UiUtils.addPopupMenuItem(popupMenu, "Copy Message", actionEvent -> handleCopyMessageClicked());
            UiUtils.addPopupMenuItem(popupMenu, "View Message", actionEvent -> handleLogClicked());

            return popupMenu;
        });

        sorter = new LogsRowSorter(model);
        table.setRowSorter(sorter);

        table.setDoubleClickListener((row, column, e) -> {
            LogEntry logEntry = (LogEntry) model.getValueAt(row, column);
            if (logEntry == null) return;
            viewMessage(logEntry);
        });

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

    private void handleCopyMessageClicked() {
        List<LogEntry> logEntryList = getSelectedLogEntries();
        StringBuilder sb = new StringBuilder();
        for (LogEntry logEntry : logEntryList) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(logEntry.message);
        }
        if (sb.isEmpty()) return;

        StringSelection stringSelection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private void handleCopyClicked() {
        List<LogEntry> logEntryList = getSelectedLogEntries();
        StringBuilder sb = new StringBuilder();
        for (LogEntry logEntry : logEntryList) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(logEntry.date);
            sb.append(", ");
            sb.append(logEntry.app);
            sb.append(", ");
            sb.append(logEntry.tid);
            sb.append(", ");
            sb.append(logEntry.pid);
            sb.append(", ");
            sb.append(logEntry.level);
            sb.append(", ");
            sb.append(logEntry.tag);
            sb.append(", ");
            sb.append(logEntry.message);
        }
        if (sb.isEmpty()) return;

        StringSelection stringSelection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private void handleLogClicked() {
        List<LogEntry> logEntryList = getSelectedLogEntries();
        if (!logEntryList.isEmpty()) {
            viewMessage(logEntryList.toArray(new LogEntry[0]));
        }
    }

    private List<LogEntry> getSelectedLogEntries() {
        List<LogEntry> logEntryList = new ArrayList<>();
        int[] selectedRows = table.getSelectedRows();
        for (int selectedRow : selectedRows) {
            int realRow = table.convertRowIndexToModel(selectedRow);
            LogEntry logEntry = (LogEntry) model.getValueAt(realRow, 0);
            logEntryList.add(logEntry);
        }
        return logEntryList;
    }

    private void viewMessage(LogEntry... logEntry) {
        if (viewScreen == null) viewScreen = new MessageViewScreen(deviceScreen);
        viewScreen.setLogEntry(logEntry);
        viewScreen.setVisible(true);
    }

    private void handleQuickAddFilter(LogsTableModel.Columns columnType, String text) {
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
        int rowCount = table.getRowCount();
        String msg = "viewing " + rowCount;

        LogFilter[] filter = sorter.getFilter();
        if (filter != null) {
            int totalRows = model.getRowCount();
            // viewing X / Y
            if (totalRows > 0 && totalRows > rowCount) {
                msg += " / " + totalRows;
            }
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
        populateFilters();
        filterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filterList.addListSelectionListener(e -> filterDevices(filterField.getCleanText()));
        filterList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // single click
                Point point = e.getPoint();
                int i = filterList.locationToIndex(point);
                if (i < 0) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    // select item
                    filterList.setSelectedIndex(i);

                    // only show popup menu for user filters
                    if (i < numSystemFilters) return;

                    JPopupMenu popupMenu = new JPopupMenu();
                    UiUtils.addPopupMenuItem(popupMenu, "Edit Filter", actionEvent -> handleEditFilterClicked());
                    UiUtils.addPopupMenuItem(popupMenu, "Delete Filter", actionEvent -> handleDeleteFilterClicked());
                }
            }
        });
    }

    private void populateFilters() {
        List<FilterItem> filterItemList = new ArrayList<>();
        // -- system filteres --
        addLogLevel(filterItemList, "All Messages", null);
        addLogLevel(filterItemList, "Log Level Debug+", "level:D+");
        addLogLevel(filterItemList, "Log Level Info+", "level:I+");
        addLogLevel(filterItemList, "Log Level Warn+", "level:W+");
        addLogLevel(filterItemList, "Log Level Error+", "level:E");
        numSystemFilters = filterItemList.size();

        // -- user filters --
        String filterStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_MESSAGE_FILTERS);
        List<FilterItem> userFilterList = GsonHelper.stringToList(filterStr, FilterItem.class);
        // sort A-Z (name)
        userFilterList.sort((lhs, rhs) -> TextUtils.compareToIgnoreCase(lhs.name, rhs.name));

        filterItemList.addAll(userFilterList);
        filterList.setListData(filterItemList.toArray(new FilterItem[0]));
    }

    private void handleDeleteFilterClicked() {

    }

    private void handleEditFilterClicked() {

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
            LogFilter searchFilter;
            if (TextUtils.indexOf(text, ':') >= 0) {
                searchFilter = LogFilter.parse(text);
            } else {
                searchFilter = LogFilter.parse("*:*" + text + "*");
            }
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
