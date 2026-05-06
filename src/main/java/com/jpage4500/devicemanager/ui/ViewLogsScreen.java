package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.*;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.table.utils.LogFilterRenderer;
import com.jpage4500.devicemanager.table.utils.LogsCellRenderer;
import com.jpage4500.devicemanager.table.utils.LogsRowSorter;
import com.jpage4500.devicemanager.table.utils.TableColumnAdjuster;
import com.jpage4500.devicemanager.ui.dialog.AddFilterDialog;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.MessageTooltipPanel;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * create and manage device view
 */
public class ViewLogsScreen extends BaseScreen implements DeviceManager.DeviceLogListener {
    private static final Logger log = LoggerFactory.getLogger(ViewLogsScreen.class);

    private static final String HINT_FILTER = "Filter...";
    private static final String HINT_SEARCH = "Search...";

    public CustomTable table;
    public LogsTableModel model;

    public StatusBar statusBar;
    public JToolBar toolbar;
    private JCheckBox autoScrollCheckBox;
    private JCheckBox showTooltipCheckBox;
    private HintTextField searchField;

    // filter logs
    private HintTextField filterField;
    private JList<LogFilter> filterList;

    private LogsRowSorter sorter;
    private MessageViewScreen viewScreen;

    // Custom tooltip for message column
    private MessageTooltipPanel tooltip;
    private int tooltipRow = -1;
    private int tooltipCol = -1;
    private int lastScrollPosition = -1;

    public JButton logButton;
    public boolean isLoggedPaused; // true when user clicks on 'stop logging'

    private JList<Device> connectedDevicesList;
    private JPanel connectedDevicesContainer;
    private CardLayout connectedDevicesCards;
    private boolean suppressDeviceSelection;

    private JSplitPane mainSplit;       // filters/devices | logs table
    private JSplitPane leftSplit;       // filters / devices (headless only)

    private static final String CARD_LIST = "list";
    private static final String CARD_EMPTY = "empty";

    private static final Comparator<Device> CONNECTED_ORDER =
            Comparator.<Device, Boolean>comparing(d -> !d.isOnline)
                    .thenComparing(d -> {
                        String name = d.getDisplayName();
                        return name != null ? name : "";
                    }, String.CASE_INSENSITIVE_ORDER);

    public ViewLogsScreen(App app, Device device) {
        super(app, device, "logs-" + (device == null ? "headless" : device.serial), 1100, 800);

        initalizeUi();
        updateDevice(device);
    }

    public void updateDevice(Device device) {
        this.device = device;
        if (device == null) {
            setTitle("No Device");
            return;
        }
        if (device.isOnline) {
            setTitle(device.getDisplayName());
            startLogging();
        } else {
            setTitle("[OFFLINE] " + device.getDisplayName());
            stopLogging();
            model.setProcessMap(null);
        }
    }

    public boolean isShowingDevice(Device device) {
        return device != null && this.device != null
                && TextUtils.equals(device.serial, this.device.serial);
    }

    public String getCurrentSerial() {
        return device != null ? device.serial : null;
    }

    /**
     * Headless-mode entry point: point this screen at the given device. If the device is in the
     * embedded picker, select it there (the list listener will call updateDevice); otherwise call
     * updateDevice directly.
     */
    public void selectDevice(Device target) {
        if (target == null) return;
        if (isShowingDevice(target)) return;

        if (app.isHeadlessMode() && connectedDevicesList != null) {
            ListModel<Device> listModel = connectedDevicesList.getModel();
            for (int i = 0; i < listModel.getSize(); i++) {
                Device d = listModel.getElementAt(i);
                if (TextUtils.equals(d.serial, target.serial)) {
                    connectedDevicesList.setSelectedIndex(i);
                    return;
                }
            }
        }
        updateDevice(target);
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
        JScrollPane filterScroll = new JScrollPane(filterList);
        setupFilterList();

        // -- add filter button --
        JButton addFilterButton = new JButton("Add Filter");
        addFilterButton.setIcon(UiUtils.getImageIcon(Icons.ADD, UiUtils.IMG_SIZE_ICON));
        addFilterButton.addActionListener(this::handleAddFilterClicked);

        if (app.isHeadlessMode()) {
            // filters panel: list + Add Filter button
            JPanel filtersPanel = new JPanel(new BorderLayout());
            filtersPanel.add(filterScroll, BorderLayout.CENTER);
            filtersPanel.add(addFilterButton, BorderLayout.SOUTH);

            // devices panel: label + list
            JPanel devicesPanel = new JPanel(new BorderLayout());
            JLabel devicesLabel = new JLabel("Devices");
            devicesLabel.setFont(devicesLabel.getFont().deriveFont(Font.BOLD));
            devicesLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
            devicesPanel.add(devicesLabel, BorderLayout.NORTH);

            setupConnectedDevicesList();
            devicesPanel.add(connectedDevicesContainer, BorderLayout.CENTER);

            leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filtersPanel, devicesPanel);
            leftSplit.setResizeWeight(0.7);
            leftPanel.add(leftSplit, BorderLayout.CENTER);
        } else {
            leftPanel.add(filterScroll, BorderLayout.CENTER);
            leftPanel.add(addFilterButton, BorderLayout.SOUTH);
        }

        JPanel rightPanel = new JPanel(new BorderLayout());

        // -- table --
        table = new CustomTable("logs");
        setupTable();
        rightPanel.add(table.getScrollPane(), BorderLayout.CENTER);

        // statusbar
        statusBar = new StatusBar();
        setupStatusBar();
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setLeftComponent(leftPanel);
        mainSplit.setRightComponent(rightPanel);
        mainPanel.add(mainSplit, BorderLayout.CENTER);

        setupMenuBar();

        setContentPane(mainPanel);
        setVisible(true);
        table.requestFocus();
        autoScrollCheckBox.setSelected(true);

        restoreSelectedFilters();
        restoreDividerLocations();
    }

    private void restoreDividerLocations() {
        int mainDivider = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LOGS_DIVIDER_MAIN, -1);
        if (mainDivider > 0) mainSplit.setDividerLocation(mainDivider);

        if (leftSplit != null) {
            int leftDivider = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LOGS_DIVIDER_LEFT, -1);
            if (leftDivider > 0) leftSplit.setDividerLocation(leftDivider);
        }
    }

    private void saveDividerLocations() {
        if (mainSplit != null) {
            PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LOGS_DIVIDER_MAIN, mainSplit.getDividerLocation());
        }
        if (leftSplit != null) {
            PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LOGS_DIVIDER_LEFT, leftSplit.getDividerLocation());
        }
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSING -> {
                closeWindow();
            }
            case ACTIVATED -> {
                // start logging if user didn't stop
                if (!isLoggedPaused && device != null) {
                    startLogging();
                }
            }
            case DEACTIVATED -> {
                // Hide tooltip when window loses focus
                hideTooltip();
            }
            default -> {
                // Handle other states (OPENED, CLOSING, etc.)
            }
        }
    }

    private void setupStatusBar() {
        // Create a panel to hold both checkboxes
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        checkboxPanel.setOpaque(false);

        // Show Tooltip checkbox
        showTooltipCheckBox = new JCheckBox("Tooltip");
        showTooltipCheckBox.setToolTipText("Show tooltip when hovering over long messages");
        showTooltipCheckBox.setBorder(new EmptyBorder(0, 10, 0, 10));
        showTooltipCheckBox.setSelected(true);
        showTooltipCheckBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!showTooltipCheckBox.isSelected()) {
                    hideTooltip();
                }
            }
        });
        checkboxPanel.add(showTooltipCheckBox);

        // Auto Scroll checkbox
        autoScrollCheckBox = new JCheckBox("Auto Scroll");
        autoScrollCheckBox.setToolTipText("Check to automatically scroll to latest messages");
        autoScrollCheckBox.setBorder(new EmptyBorder(0, 10, 0, 10));
        autoScrollCheckBox.setHorizontalAlignment(SwingConstants.TRAILING);
        autoScrollCheckBox.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scrollToFollow();
            }
        });
        checkboxPanel.add(autoScrollCheckBox);

        statusBar.setRightComponent(checkboxPanel);
    }

    private void setupMenuBar() {
        JMenu windowMenu = buildWindowMenu();

        // -----------------------------------------------------------
        // -----------------------------------------------------------

        JMenu editMenu = new JMenu("Edit");

        createMenuItem(editMenu, "Select Font", null, e -> showFontSelection());

        // [CMD + +] = increase font size
        createCmdMenuItem(editMenu, "Increase Font Size", KeyEvent.VK_EQUALS, e -> increaseFontSize());

        // [CMD + -] = increase font size
        createCmdMenuItem(editMenu, "Decrease Font Size", KeyEvent.VK_MINUS, e -> decreaseFontSize());

        // [CMD + F] = focus search field
        createCmdMenuItem(editMenu, "Search for...", KeyEvent.VK_F, e -> searchField.requestFocus());

        // [CMD + G] = find next
        createCmdMenuItem(editMenu, "Find Next", KeyEvent.VK_G, e -> findNext(true));

        // [SHIFT + CMD + G] = find previous
        KeyStroke findPrevKey = KeyStroke.getKeyStroke("shift meta G");
        createMenuItem(editMenu, "Find Previous", findPrevKey, e -> findNext(false));

        // -----------------------------------------------------------
        // -----------------------------------------------------------
        JMenu logsMenu = new JMenu("Logs");

        // [CMD + S] = save logs
        createCmdMenuItem(logsMenu, "Save Logs", KeyEvent.VK_S, e -> handleSaveLogsClicked());

        // [CMD + ENTER] = toggle auto scroll
        createCmdMenuItem(logsMenu, "Auto Scroll", KeyEvent.VK_ENTER, e -> {
            autoScrollCheckBox.setSelected(!autoScrollCheckBox.isSelected());
            scrollToFollow();
        });

        // [CMD + K] = clear logs
        createCmdMenuItem(logsMenu, "Clear logs", KeyEvent.VK_K, e -> model.clearLogs());

        // [CMD + V] = view logs
        createCmdMenuItem(logsMenu, "View selected", KeyEvent.VK_V, e -> handleViewLogsClicked());

        // [CMD + E] = edit logs
        createCmdMenuItem(logsMenu, "Edit selected", KeyEvent.VK_E, e -> handleEditLogsClicked());

        // [CMD + KEY_UP] = scroll to top
        createCmdMenuItem(logsMenu, "Scroll to top", KeyEvent.VK_UP, e -> {
            autoScrollCheckBox.setSelected(false);
            table.scrollToTop();
        });

        // [CMD + KEY_DOWN] = scroll to bottom
        createCmdMenuItem(logsMenu, "Scroll to bottom", KeyEvent.VK_DOWN, e -> table.scrollToBottom());

        // [OPTION + KEY_UP] = page up
        KeyStroke optionUpKey = KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK);
        createMenuItem(logsMenu, "Page Up", optionUpKey, e -> table.pageUp());

        // [OPTION + KE_DOWN] = page down
        KeyStroke optionDownKey = KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
        createMenuItem(logsMenu, "Page Down", optionDownKey, e -> table.pageDown());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(editMenu);
        menubar.add(logsMenu);
        setJMenuBar(menubar);
    }

    private void findNext(boolean isForward) {
        int visibleRows = table.getRowCount();
        if (visibleRows == 0) return;
        String searchFor = searchField.getCleanText();
        if (TextUtils.isEmpty(searchFor)) return;

        int startIndex = table.getSelectedRow();

        if (startIndex >= 0) {
            // start searching from selected row
            startIndex += isForward ? 1 : -1;
            if (startIndex > visibleRows - 1) startIndex = 0;
            else if (startIndex < 0) startIndex = visibleRows - 1;
        } else {
            // start from beginning or end of table
            startIndex = isForward ? 0 : visibleRows - 1;
        }

        LogFilter filter = LogFilter.parse("*:*" + searchFor + "*");

        for (int i = 0; i < visibleRows; i++) {
            // convert viewable row into model row to get LogEntry
            int modelRow = sorter.convertRowIndexToModel(startIndex);
            LogEntry logEntry = (LogEntry) model.getValueAt(modelRow, 0);
            if (filter.isMatch(logEntry)) {
                log.trace("findNext: MATCH! row:{}, index:{}", modelRow, startIndex);
                table.changeSelection(startIndex, 0, false, false);

                JScrollPane scrollPane = table.getScrollPane();
                Rectangle cellRect = table.getCellRect(startIndex, 0, true);
                Rectangle scrollPaneRect = scrollPane.getViewport().getViewRect();
                if (!scrollPaneRect.contains(cellRect)) {
                    table.scrollRectToVisible(new Rectangle(cellRect.x, cellRect.y, (int) scrollPaneRect.getWidth(), (int) scrollPaneRect.getHeight()));
                }
                break;
            }

            startIndex += isForward ? 1 : -1;
            // if we reached the end/beginning, start over from top/bottom
            if (isForward && startIndex >= visibleRows) {
                startIndex = 0;
                Toolkit.getDefaultToolkit().beep();
            } else if (!isForward && startIndex < 0) {
                startIndex = visibleRows - 1;
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    public void increaseFontSize() {
        int fontOffset = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, 0);
        fontOffset++;
        if (fontOffset > 10) fontOffset = 10;
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, fontOffset);
        notifyFontChanged();
    }

    private void notifyFontChanged() {
        LogsCellRenderer cellRenderer = (LogsCellRenderer) table.getDefaultRenderer(LogEntry.class);
        cellRenderer.notifyFontChanged();
        applyRowHeight(cellRenderer.getFont());
        model.fireTableDataChanged();
    }

    private void applyRowHeight(Font font) {
        FontMetrics fm = table.getFontMetrics(font);
        // tight row height: ascent + descent + small fixed padding (no leading)
        int rowHeight = fm.getAscent() + fm.getDescent() + 2;
        table.setRowHeight(rowHeight);
    }

    public void decreaseFontSize() {
        int fontOffset = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, 0);
        fontOffset--;
        if (fontOffset < -10) fontOffset = -10;
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, fontOffset);
        notifyFontChanged();
    }

    private void showFontSelection() {
        JFontChooser fontChooser = new JFontChooser();
        LogsCellRenderer cellRenderer = (LogsCellRenderer) table.getDefaultRenderer(LogEntry.class);
        fontChooser.setSelectedFont(cellRenderer.getFont());
        int rc = fontChooser.showDialog(this);
        if (rc != JOptionPane.YES_OPTION) return;
        Font font = fontChooser.getSelectedFont();
        log.trace("showFontSelection: font:{}", font);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_LOGS_FONT_NAME, font.getName());
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LOGS_FONT_STYLE, font.getStyle());
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LOGS_FONT_SIZE, font.getSize());
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_FONT_SIZE_OFFSET, 0);
        notifyFontChanged();
    }

    @Override
    public void closeWindow() {
        String name = device != null ? device.getDisplayName() : "no device";
        log.trace("closeWindow: {}", name);
        // save last filter
        String filterText = filterField.getCleanText();
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_LOGS_CUSTOM_FILTER, filterText.trim());

        // save last selected filters
        List<LogFilter> selectedList = filterList.getSelectedValuesList();
        List<String> selectedFilterList = new ArrayList<>();
        for (LogFilter filter : selectedList) selectedFilterList.add(filter.name);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_LOGS_SELECTED_FILTERS, GsonHelper.toJson(selectedFilterList));

        // persist column widths/order
        table.saveTable();

        saveDividerLocations();

        stopLogging();
        app.onLogsClosed(device != null ? device.serial : null);
        dispose();
    }

    @Override
    public void toggleToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    private void setupTable() {
        model = new LogsTableModel();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setModel(model);
        LogsCellRenderer cellRenderer = new LogsCellRenderer();
        table.setDefaultRenderer(LogEntry.class, cellRenderer);
        applyRowHeight(cellRenderer.getFont());

        // restore user-defined column sizes
        if (!table.restoreTable()) {
            // use some default column sizes
            table.setPreferredColWidth(LogsTableModel.Columns.LEVEL.toString(), 28);
            table.setPreferredColWidth(LogsTableModel.Columns.TID.toString(), 60);
            table.setPreferredColWidth(LogsTableModel.Columns.DATE.toString(), 159);
            table.setPreferredColWidth(LogsTableModel.Columns.APP.toString(), 150);
            table.setPreferredColWidth(LogsTableModel.Columns.TAG.toString(), 200);
            table.setPreferredColWidth(LogsTableModel.Columns.MSG.toString(), 700);
        }

        table.setMaxColWidth(LogsTableModel.Columns.LEVEL.toString(), 35);
        table.setMaxColWidth(LogsTableModel.Columns.TID.toString(), 100);

        // ENTER -> view message
        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, "Enter");
        table.getActionMap().put("Enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleViewLogsClicked();
            }
        });

        // CMD+SHIFT+V -> view message
        KeyStroke view = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK);
        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(view, "View");
        table.getActionMap().put("View", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleViewLogsClicked();
            }
        });

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

                boolean autoResize = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_LOGS_AUTO_RESIZE, true);
                String resizeDesc = autoResize ? "ON" : "OFF";
                UiUtils.addPopupMenuItem(popupMenu, "Auto Resize: " + resizeDesc, actionEvent -> {
                    boolean update = !autoResize;
                    PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_LOGS_AUTO_RESIZE, update);
                    int flag = update ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF;
                    table.setAutoResizeMode(flag);
                });
                return popupMenu;
            }

            int selectedRows = table.getSelectedRowCount();
            if (selectedRows == 1) {
                LogsTableModel.Columns columnType = model.getColumnType(column);
                switch (columnType) {
                    case APP:
                    case TID:
                    case LEVEL:
                    case TAG:
                        // filter by value
                        String text = model.getTextValue(row, column);
                        UiUtils.addPopupMenuItem(popupMenu, "Add Filter", actionEvent -> handleQuickAddFilter(columnType, text));
                        break;
                    default:
                        // Other columns don't support filtering
                        break;
                }
            }

            UiUtils.addPopupMenuItem(popupMenu, "Copy Line", actionEvent -> handleCopyClicked());
            UiUtils.addPopupMenuItem(popupMenu, "Copy Message", actionEvent -> handleCopyMessageClicked());
            UiUtils.addPopupMenuItem(popupMenu, "View Message", actionEvent -> handleViewLogsClicked());

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
            if (wheelRotation == -1 && autoScrollCheckBox.isSelected()) {
                // scrolling UP - disable auto-scroll
                // only if user scrolls past last few lines
                int lastVisibleRow = getLastVisibleRow();
                if (lastVisibleRow > 0) {
                    autoScrollCheckBox.setSelected(false);
                }
            } else if (wheelRotation == 1 && !autoScrollCheckBox.isSelected()) {
                // scrolling DOWN
                int lastVisibleRow = getLastVisibleRow();
                if (lastVisibleRow == -1) {
                    autoScrollCheckBox.setSelected(true);
                    scrollToFollow();
                }
            }
        });

//
//        final List<Integer> selectedList = new ArrayList<>();
//        table.getSelectionModel().addListSelectionListener(e -> {
//            int[] selectedRows = table.getSelectedRows();
//            selectedList.clear();
//            for (int index : selectedRows) {
//                selectedList.add(index);
//            }
//            log.trace("setupTable: selected: {}", selectedList.size());
//        });
//
//        table.getModel().addTableModelListener(e -> {
//            if (selectedList.isEmpty()) return;
//            List<Integer> copyList = new ArrayList<>(selectedList);
//            SwingUtilities.invokeLater(() -> {
//                //ListSelectionModel model = table.getSelectionModel();
//                log.trace("setupTable: select:{}", GsonHelper.toJson(copyList));
//                table.clearSelection();
//                for (Integer index : copyList) {
//                    table.changeSelection(index, 0, false, false);
//                }
//            });
//        });

        searchField.setupSearch(table);
        searchField.setupSearch(filterList);

        // Setup custom tooltip for MSG column
        tooltip = new MessageTooltipPanel(this);

        // Add mouse motion listener to track hover
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                handleMouseMovedForTooltip(e);
            }
        });

        // Hide tooltip when mouse exits table
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hideTooltip();
            }
        });

        // Hide tooltip when viewport position actually changes (not just on adjustment events)
        JScrollBar verticalScrollBar = table.getScrollPane().getVerticalScrollBar();
        verticalScrollBar.addAdjustmentListener(e -> {
            // Only hide if the value actually changed (not just during drag)
            if (!e.getValueIsAdjusting()) {
                int currentPosition = e.getValue();
                if (currentPosition != lastScrollPosition) {
                    lastScrollPosition = currentPosition;
                    hideTooltip();
                }
            }
        });
    }

    private void handleMouseMovedForTooltip(java.awt.event.MouseEvent e) {
        // Check if tooltip is enabled
        if (!showTooltipCheckBox.isSelected()) {
            return;
        }

        Point p = e.getPoint();
        int row = table.rowAtPoint(p);
        int col = table.columnAtPoint(p);

        if (row < 0 || col < 0) {
            hideTooltip();
            return;
        }

        // Check if same cell as before
        if (row == tooltipRow && col == tooltipCol) return;

        // Hide previous tooltip
        hideTooltip();

        // Convert to model coordinates
        int modelCol = table.convertColumnIndexToModel(col);

        // Only show tooltip for message column
        LogsTableModel.Columns columnType = model.getColumnType(modelCol);
        if (columnType != LogsTableModel.Columns.MSG) {
            return;
        }

        // Check if content is truncated
        String text = table.getTextIfTruncated(row, col);
        if (TextUtils.isEmpty(text)) return;
        tooltipRow = row;
        tooltipCol = col;

        // Get table bounds for positioning
        JScrollPane scrollPane = table.getScrollPane();
        Rectangle viewportBounds = scrollPane.getViewport().getViewRect();
        Point viewportLocation = scrollPane.getViewport().getLocationOnScreen();

        // Get mouse position on screen
        Point mouseOnScreen = e.getLocationOnScreen();

        int x = viewportLocation.x;
        int width = viewportBounds.width;
        int bottomY = viewportLocation.y + viewportBounds.height;
        int topY = viewportLocation.y;

        tooltip.showTooltip(text, x, topY, bottomY, mouseOnScreen.y, width);
    }

    private void hideTooltip() {
        if (tooltip != null) {
            tooltip.hideTooltip();
        }
        tooltipRow = -1;
        tooltipCol = -1;
    }

    private void handleCopyMessageClicked() {
        log.trace("handleCopyMessageClicked: ");
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
            sb.append(logEntry.toString());
        }
        if (sb.isEmpty()) return;

        StringSelection stringSelection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private void handleViewLogsClicked() {
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
        if (viewScreen == null) viewScreen = new MessageViewScreen(app);
        viewScreen.setLogEntry(logEntry);
        viewScreen.setVisible(true);
    }

    private void handleEditLogsClicked() {
        List<LogEntry> logEntryList = getSelectedLogEntries();
        if (logEntryList.isEmpty()) return;

        if (viewScreen == null) viewScreen = new MessageViewScreen(app);
        viewScreen.setLogEntry(logEntryList.toArray(new LogEntry[0]));

        viewScreen.editMessage();
        viewScreen.setVisible(false);
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
        if (device == null) return;
        app.setDeviceBusy(device, false);
        DeviceManager.getInstance().stopLogging(device);
    }

    private void startLogging() {
        if (device == null) return;
        if (device.isOnline && !DeviceManager.getInstance().isLogging(device)) {
            app.setDeviceBusy(device, true);
            // get last log entry and start from there
            String lastLogTime = model.getLastLogTime();
            DeviceManager.getInstance().startLogging(device, lastLogTime, null, this);
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

        createSmallToolbarButton(toolbar, Icons.TRASH, "Clear", "Clear Logs", actionEvent -> clearLogs());
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
        Icons iconEnum = isLoggedPaused ? Icons.PLAY : Icons.STOP;
        ImageIcon icon = UiUtils.getImageIcon(iconEnum, UiUtils.IMG_SIZE_ICON);
        logButton.setIcon(icon);
        logButton.setText(isLoggedPaused ? "Start" : "Stop");
    }

    private void doSearch(String text) {
        if (TextUtils.isEmpty(text)) {
            model.setSearchText(null);
        } else {
            model.setSearchText(text);
        }
        refreshUi();
    }

    private void setupConnectedDevicesList() {
        connectedDevicesList = new JList<>();
        connectedDevicesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        connectedDevicesList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Device d) {
                    setText(d.getDisplayName());
                    Icons icn = d.getDeviceIcon();
                    Color color = d.getDeviceColor();
                    ImageIcon imageIcon = UiUtils.getImageIcon(icn, UiUtils.IMG_SIZE_ICON, UiUtils.IMG_SIZE_ICON, color);
                    setIcon(imageIcon);
                }
                return this;
            }
        });
        connectedDevicesList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            if (suppressDeviceSelection) return;
            Device picked = connectedDevicesList.getSelectedValue();
            if (picked == null) return;
            String currentSerial = device != null ? device.serial : null;
            if (TextUtils.equals(picked.serial, currentSerial)) return;
            // user switched device: stop the previous one and clear log buffer
            if (device != null) {
                DeviceManager.getInstance().stopLogging(device);
                model.setProcessMap(null);
            }
            model.clearLogs();
            updateDevice(picked);
        });

        connectedDevicesCards = new CardLayout();
        connectedDevicesContainer = new JPanel(connectedDevicesCards);

        JScrollPane scrollPane = new JScrollPane(connectedDevicesList);
        connectedDevicesContainer.add(scrollPane, CARD_LIST);

        JLabel emptyLabel = new JLabel("No Devices", SwingConstants.CENTER);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC));
        emptyLabel.setForeground(Color.GRAY);
        connectedDevicesContainer.add(emptyLabel, CARD_EMPTY);

        connectedDevicesCards.show(connectedDevicesContainer, CARD_EMPTY);
    }

    public void setConnectedDevices(List<Device> devices) {
        if (!app.isHeadlessMode() || connectedDevicesList == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setConnectedDevices(devices));
            return;
        }

        List<Device> sorted = devices != null ? new ArrayList<>(devices) : new ArrayList<>();

        // if the active device was disconnected, keep it in the list as offline (still selected)
        String currentSerial = device != null ? device.serial : null;
        if (currentSerial != null) {
            boolean found = false;
            for (Device d : sorted) {
                if (TextUtils.equals(d.serial, currentSerial)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                device.isOnline = false;
                sorted.add(device);
            }
        }

        if (sorted.isEmpty()) {
            suppressDeviceSelection = true;
            connectedDevicesList.setListData(new Device[0]);
            suppressDeviceSelection = false;
            connectedDevicesCards.show(connectedDevicesContainer, CARD_EMPTY);
            return;
        }

        sorted.sort(CONNECTED_ORDER);

        suppressDeviceSelection = true;
        connectedDevicesList.setListData(sorted.toArray(new Device[0]));
        connectedDevicesCards.show(connectedDevicesContainer, CARD_LIST);

        int targetIndex = -1;
        if (currentSerial != null) {
            for (int i = 0; i < sorted.size(); i++) {
                if (TextUtils.equals(sorted.get(i).serial, currentSerial)) {
                    targetIndex = i;
                    break;
                }
            }
        }

        if (targetIndex >= 0) {
            // re-select the screen's current device (may be in offline section now)
            connectedDevicesList.setSelectedIndex(targetIndex);
            suppressDeviceSelection = false;
        } else {
            // first populate, no selection yet — auto-select the top device
            connectedDevicesList.setSelectedIndex(0);
            suppressDeviceSelection = false;
            updateDevice(sorted.get(0));
        }
    }

    private void setupFilterList() {
        populateFilters();
        filterList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        filterList.setCellRenderer(new LogFilterRenderer());
        filterList.addListSelectionListener(e -> handleFilterSelected());
        UiUtils.addClickListener(filterList, e -> {
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
        });
    }

    private void restoreSelectedFilters() {
        // select last used filter(s)
        String recentFilterStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_LOGS_SELECTED_FILTERS);
        List<String> recentFilterList = GsonHelper.stringToList(recentFilterStr, String.class);
        ListModel<LogFilter> filterListModel = filterList.getModel();
        List<Integer> selectedIndexList = new ArrayList<>();
        for (int i = 0; i < filterListModel.getSize(); i++) {
            LogFilter filter = filterListModel.getElementAt(i);
            if (recentFilterList.contains(filter.name)) {
                selectedIndexList.add(i);
            }
        }
        if (!selectedIndexList.isEmpty()) {
            int[] indexArr = selectedIndexList.stream()
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .toArray();
            log.trace("setupFilterList: re-select:{}", GsonHelper.toJson(indexArr));
            filterList.setSelectedIndices(indexArr);
        }

        // restore previous custom filter
        String recentFilterText = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_LOGS_CUSTOM_FILTER);
        if (TextUtils.notEmpty(recentFilterText)) {
            filterField.setText(recentFilterText);
        }

    }

    private void populateFilters() {
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
        if (sorter == null) return;
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
            // capture selected rows
//            int[] selectedRows = table.getSelectedRows();
//            log.trace("handleLogEntries: selected rows: {}", GsonHelper.toJson(selectedRows));

            model.addLogEntry(logList);

            // restore selected rows
//            table.clearSelection();
//            int rowCount = table.getRowCount();
//            for (int row : selectedRows) {
//                if (row < rowCount) {
//                    table.addRowSelectionInterval(row, row);
//                }
//            }

            scrollToFollow();
            refreshUi();
        });
    }

    @Override
    public void handleProcessMap(Map<String, String> processMap) {
        SwingUtilities.invokeLater(() -> model.setProcessMap(processMap));
    }

    @Override
    public void handleError(String error) {
        log.error("handleError: {}", error);
    }

    private void handleSaveLogsClicked() {
        // ask user what to save
        List<String> optionList = new ArrayList<>();
        int numSelected = table.getSelectedRowCount();
        if (numSelected > 0) {
            optionList.add("Selected Lines (" + numSelected + ")");
        }
        List<LogFilter> selectedFilters = filterList.getSelectedValuesList();
        if (!selectedFilters.isEmpty()) {
            optionList.add("Visible Lines");
        }
        optionList.add("Entire Log Buffer");

        List<LogEntry> logEntriesToSave = null;
        // only show dialog if more than one option
        if (optionList.size() > 1) {
            int choice = DialogHelper.showOptionDialog(this, "What would you like to save?", "Save Logs", optionList);
            if (choice < 0 || choice >= optionList.size()) return;
            String selectedValue = optionList.get(choice);
            if (TextUtils.startsWith(selectedValue, "Selected")) {
                logEntriesToSave = getSelectedLogEntries();
            } else if (TextUtils.startsWith(selectedValue, "Visible")) {
                logEntriesToSave = getVisibleLogEntries();
            }
        }
        if (logEntriesToSave == null) {
            // default: save entire log buffer
            logEntriesToSave = getAllLogEntries();
        }
        if (logEntriesToSave.isEmpty()) {
            DialogHelper.showDialog(this, "Nothing to save", "No log entries to save");
            return;
        }

        saveLogs(logEntriesToSave);
    }

    private List<LogEntry> getVisibleLogEntries() {
        List<LogEntry> logEntries = new ArrayList<>();
        int rowCount = table.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            int modelRow = table.convertRowIndexToModel(i);
            LogEntry logEntry = (LogEntry) model.getValueAt(modelRow, 0);
            if (logEntry != null) {
                logEntries.add(logEntry);
            }
        }
        return logEntries;
    }

    private List<LogEntry> getAllLogEntries() {
        List<LogEntry> logEntries = new ArrayList<>();
        int rowCount = model.getRowCount();
        for (int i = 0; i < rowCount; i++) {
            LogEntry logEntry = (LogEntry) model.getValueAt(i, 0);
            if (logEntry != null) {
                logEntries.add(logEntry);
            }
        }
        return logEntries;
    }

    private void saveLogs(List<LogEntry> logEntries) {
        // generate default filename with current date
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String defaultFileName = "log-" + dateFormat.format(new Date()) + ".txt";

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Logs");
        fileChooser.setCurrentDirectory(new File(Utils.getDownloadFolder()));
        fileChooser.setSelectedFile(new File(defaultFileName));

        int result = fileChooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;
        File file = fileChooser.getSelectedFile();

        // check if file exists and confirm overwrite
        if (file.exists()) {
            if (!DialogHelper.showConfirmDialog(this, "File Exists", "File already exists. Do you want to overwrite it?"))
                return;
        }

        // write logs to file
        try (FileWriter writer = new FileWriter(file)) {
            for (LogEntry logEntry : logEntries) {
                writer.write(logEntry.toString());
                writer.write(System.lineSeparator());
            }
            DialogHelper.showDialog(this, "Logs Saved", "Successfully saved " + logEntries.size() + " log entries to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            log.error("saveLogs: IOException: {}", e.getMessage());
            DialogHelper.showDialog(this, "Save Error", "Error saving logs: " + e.getMessage());
        }
    }

}
