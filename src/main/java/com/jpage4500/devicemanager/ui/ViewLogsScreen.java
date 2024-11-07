package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.data.LogFilterEntry;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.table.utils.LogFilterRenderer;
import com.jpage4500.devicemanager.table.utils.LogsCellRenderer;
import com.jpage4500.devicemanager.table.utils.LogsRowSorter;
import com.jpage4500.devicemanager.table.utils.TableColumnAdjuster;
import com.jpage4500.devicemanager.ui.dialog.AddFilterDialog;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * create and manage device view
 */
public class ViewLogsScreen extends BaseScreen implements DeviceManager.DeviceLogListener {
    private static final Logger log = LoggerFactory.getLogger(ViewLogsScreen.class);

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
    private JList<LogFilter> filterList;

    private LogsRowSorter sorter;
    private MessageViewScreen viewScreen;

    public JButton logButton;
    public boolean isLoggedPaused; // true when user clicks on 'stop logging'
    public JButton quickViewButton;
    public boolean isQuickViewEnabled; // true when user clicks on 'quick view'

    public ViewLogsScreen(DeviceScreen deviceScreen, Device device) {
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
        filterField = new HintTextField(HINT_FILTER, this::doFilter);
        leftPanel.add(filterField, BorderLayout.NORTH);

        // -- filter list --
        filterList = new JList<>();
        setupFilterList();
        leftPanel.add(filterList, BorderLayout.CENTER);

        // -- add filter button --
        JButton addFilterButton = new JButton("Add Filter");
        addFilterButton.setIcon(UiUtils.getImageIcon("icon_add.png", UiUtils.IMG_SIZE_ICON));
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

            UiUtils.addPopupMenuItem(popupMenu, "Copy Line", actionEvent -> handleCopyClicked());
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

        searchField.setupSearch(table);
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
        deviceScreen.setDeviceBusy(device, false);
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
            deviceScreen.setDeviceBusy(device, true);
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

        quickViewButton = createSmallToolbarButton(toolbar, null, null, "", actionEvent -> toggleQuickViewButton());
        updateQuickViewButton();

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
        ImageIcon icon = UiUtils.getImageIcon(imageName, UiUtils.IMG_SIZE_ICON);
        logButton.setIcon(icon);
        logButton.setText(isLoggedPaused ? "Start" : "Stop");
    }

    private void toggleQuickViewButton() {
        isQuickViewEnabled = !isQuickViewEnabled;
        updateQuickViewButton();

        List<String> hiddenColList = new ArrayList<>();
        if (isQuickViewEnabled) {
            hiddenColList.add(LogsTableModel.Columns.DATE.name());
            hiddenColList.add(LogsTableModel.Columns.APP.name());
            hiddenColList.add(LogsTableModel.Columns.TID.name());
            hiddenColList.add(LogsTableModel.Columns.PID.name());
        }
        model.setHiddenColumns(hiddenColList);

        // use some default column sizes
        table.setPreferredColWidth(LogsTableModel.Columns.LEVEL.toString(), 28);
        table.setPreferredColWidth(LogsTableModel.Columns.PID.toString(), 60);
        table.setPreferredColWidth(LogsTableModel.Columns.TID.toString(), 60);
        table.setPreferredColWidth(LogsTableModel.Columns.DATE.toString(), 159);
        table.setPreferredColWidth(LogsTableModel.Columns.APP.toString(), 150);
        table.setPreferredColWidth(LogsTableModel.Columns.TAG.toString(), 200);
        table.setPreferredColWidth(LogsTableModel.Columns.MSG.toString(), 700);

        table.setMaxColWidth(LogsTableModel.Columns.LEVEL.toString(), 35);
        table.setMaxColWidth(LogsTableModel.Columns.PID.toString(), 100);
        table.setMaxColWidth(LogsTableModel.Columns.TID.toString(), 100);
    }

    private void updateQuickViewButton() {
        String imageName = isQuickViewEnabled ? "eye_closed.png" : "eye_open.png";
        ImageIcon icon = UiUtils.getImageIcon(imageName, UiUtils.IMG_SIZE_ICON);
        quickViewButton.setIcon(icon);
        quickViewButton.setText(isQuickViewEnabled ? "Restore" : "Hide");
        quickViewButton.setToolTipText(isQuickViewEnabled ? "Restore Distraction Free Mode" : "Enter Distraction Free Mode");
    }

    private void doSearch(String text) {
        if (TextUtils.isEmpty(text)) {
            model.setSearchText(null);
        } else {
            model.setSearchText(text);
        }
        refreshUi();
    }

    private void setupFilterList() {
        populateFilters();
        filterList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        filterList.setCellRenderer(new LogFilterRenderer());
        filterList.addListSelectionListener(e -> handleFilterSelected());
        filterList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // single click
                if (SwingUtilities.isRightMouseButton(e)) {
                    // select item
                    Point point = e.getPoint();
                    int i = filterList.locationToIndex(point);
                    if (i < 0) return;
                    filterList.setSelectedIndex(i);

                    LogFilter selectedFilter = filterList.getSelectedValue();
                    if (selectedFilter == null || selectedFilter.isSystemFilter) return;

                    JPopupMenu popupMenu = new JPopupMenu();
                    UiUtils.addPopupMenuItem(popupMenu, "Edit Filter", actionEvent -> handleEditFilterClicked(selectedFilter));
                    UiUtils.addPopupMenuItem(popupMenu, "Duplicate Filter", actionEvent -> handleCopyFilterClicked(selectedFilter));
                    UiUtils.addPopupMenuItem(popupMenu, "Delete Filter", actionEvent -> handleDeleteFilterClicked(selectedFilter));
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                } else if (e.getClickCount() >= 2) {
                    LogFilter selectedFilter = filterList.getSelectedValue();
                    if (selectedFilter == null || selectedFilter.isSystemFilter) return;
                    handleEditFilterClicked(selectedFilter);
                }
            }
        });
    }

    private void populateFilters() {
        List<LogFilter> selectedList = filterList.getSelectedValuesList();

        // -- system filters --
        List<LogFilter> systemFilterList = getSystemFilters();
        List<LogFilter> logFilterList = new ArrayList<>(systemFilterList);

        // -- user filters --
        List<LogFilter> userFilterList = getUserFilters();
        // sort A-Z (name)
        userFilterList.sort((lhs, rhs) -> TextUtils.compareToIgnoreCase(lhs.name, rhs.name));
        logFilterList.addAll(userFilterList);
        // replace all filters
        filterList.setListData(logFilterList.toArray(new LogFilter[0]));

        // re-select previously selected filters
        if (!selectedList.isEmpty() && !logFilterList.isEmpty()) {
            List<Integer> selectedIndexList = new ArrayList<>();
            for (int i = 0; i < logFilterList.size(); i++) {
                LogFilter filter = logFilterList.get(i);
                for (LogFilter prevSelectedFilter : selectedList) {
                    if (TextUtils.equals(prevSelectedFilter.name, filter.name)) {
                        selectedIndexList.add(i);
                        break;
                    }
                }
            }
            if (!selectedIndexList.isEmpty()) {
                int[] indexArr = selectedIndexList.stream()
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .toArray();
                log.trace("populateFilters: re-select:{}", GsonHelper.toJson(indexArr));
                filterList.setSelectedIndices(indexArr);
            }
        }
    }

    public static List<LogFilter> getSystemFilters() {
        List<LogFilter> systemList = new ArrayList<>();
        systemList.add(createFilter("All Messages", null));
        systemList.add(createFilter("Log Level Debug+", "level:D+"));
        systemList.add(createFilter("Log Level Info+", "level:I+"));
        systemList.add(createFilter("Log Level Warn+", "level:W+"));
        systemList.add(createFilter("Log Level Error+", "level:E"));
        systemList.add(createFilter(null, null));
        return systemList;
    }

    private void selectFilter(LogFilter filter) {
        ListModel<LogFilter> listModel = filterList.getModel();
        for (int i = 0; i < listModel.getSize(); i++) {
            LogFilter value = listModel.getElementAt(i);
            if (TextUtils.equals(value.name, filter.name)) {
                filterList.setSelectedIndex(i);
                break;
            }
        }
    }

    /**
     * remove filter by name from filter list
     *
     * @param userFilterList - may be null
     * @param filter         - filter to remove
     */
    private void removeFilter(List<LogFilter> userFilterList, LogFilter filter) {
        if (userFilterList == null) userFilterList = getUserFilters();
        for (Iterator<LogFilter> iterator = userFilterList.iterator(); iterator.hasNext(); ) {
            LogFilter userFilter = iterator.next();
            if (TextUtils.equals(userFilter.name, filter.name)) {
                log.trace("removeFilter: REMOVE: {}", filter);
                iterator.remove();
                break;
            }
        }
    }

    public static List<LogFilter> getUserFilters() {
        String filterStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_MESSAGE_FILTERS);
        return GsonHelper.stringToList(filterStr, LogFilter.class);
    }

    private void handleAddFilterClicked(ActionEvent actionEvent) {
        LogFilter filter = AddFilterDialog.showAddFilterDialog(this, null);
        if (filter != null) {
            addFilter(null, filter);
            populateFilters();
            selectFilter(filter);
        }
    }

    public static void addFilter(List<LogFilter> userFilterList, LogFilter filter) {
        if (userFilterList == null) userFilterList = getUserFilters();
        userFilterList.add(filter);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_MESSAGE_FILTERS, GsonHelper.toJson(userFilterList));
    }

    private void handleFilterSelected() {
        doFilter(filterField.getCleanText());
    }

    private void handleCopyFilterClicked(LogFilter selectedFilter) {
        LogFilter copy = new LogFilter(selectedFilter);
        addFilter(null, copy);
        populateFilters();
        selectFilter(copy);
        handleEditFilterClicked(copy);
    }

    private void handleEditFilterClicked(LogFilter selectedFilter) {
        LogFilter filter = AddFilterDialog.showAddFilterDialog(this, selectedFilter);
        if (filter != null) {
            List<LogFilter> userFilterList = getUserFilters();
            removeFilter(userFilterList, selectedFilter);
            addFilter(userFilterList, filter);
            populateFilters();
            selectFilter(filter);
        }
    }

    private void handleDeleteFilterClicked(LogFilter selectedFilter) {
        String msg = String.format("Delete Filter \"%s\"?", selectedFilter.name);
        boolean isYes = DialogHelper.showConfirmDialog(this, "Delete Filter", msg);
        if (isYes) {
            List<LogFilter> userFilterList = getUserFilters();
            for (Iterator<LogFilter> iterator = userFilterList.iterator(); iterator.hasNext(); ) {
                LogFilter userFilter = iterator.next();
                if (TextUtils.equals(userFilter.name, selectedFilter.name)) {
                    iterator.remove();
                    break;
                }
            }
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_MESSAGE_FILTERS, GsonHelper.toJson(userFilterList));
            populateFilters();
        }
    }

    private static LogFilter createFilter(String label, String filter) {
        LogFilter item = LogFilter.parse(filter);
        item.name = label;
        item.isSystemFilter = true;
        return item;
    }

    private void doFilter(String text) {
        List<LogFilter> list = new ArrayList<>();

        // add currently selected filter(s)
        List<LogFilter> selectedList = filterList.getSelectedValuesList();
        StringBuilder sb = new StringBuilder();
        for (LogFilter item : selectedList) {
            if (item.filterList != null) {
                list.add(item);
                for (LogFilterEntry expression : item.filterList) {
                    if (!sb.isEmpty()) sb.append(" && ");
                    sb.append(expression);
                }
            }
        }

        // add custom text filter
        if (TextUtils.notEmpty(text)) {
            LogFilter searchFilter;
            if (TextUtils.indexOf(text, ':') >= 0) {
                searchFilter = LogFilter.parse(text);
            } else {
                searchFilter = LogFilter.parse("*:*" + text + "*");
            }
            //log.trace("filterDevices: {}", searchFilter);
            list.add(searchFilter);
            if (!sb.isEmpty()) sb.append(" && ");
            sb.append("\"" + text + "\"");
        }

        sorter.setFilter(list.toArray(new LogFilter[0]));

        statusBar.setCenterLabel(sb.toString());
        model.fireTableDataChanged();
        refreshUi();
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
