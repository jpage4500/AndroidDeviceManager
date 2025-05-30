package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.data.SaveLogEntry;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.SaveLogsTableModel;
import com.jpage4500.devicemanager.table.utils.SaveLogsCellRenderer;
import com.jpage4500.devicemanager.ui.dialog.AddFilterDialog;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * screen to capture device logs for 1 or more devices
 */
public class SaveLogsScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(SaveLogsScreen.class);

    private final DeviceScreen deviceScreen;

    private boolean isRecording;
    private final Icon iconStartRecording;
    private final Icon iconStopRecording;
    private final Icon filterSetIcon;
    private final Icon noFilterIcon;
    private File lastLogsFolder;

    public StatusBar statusBar;
    public JToolBar toolbar;

    public CustomTable table;
    public SaveLogsTableModel model;

    private LogFilter logFilter;

    private JButton logButton;
    private JButton deleteButton;
    private JButton filterButton;

    public SaveLogsScreen(DeviceScreen deviceScreen) {
        super("savelogs", 450, 230);
        //setAlwaysOnTop(true);
        setTitle("Save Device Logs");
        this.deviceScreen = deviceScreen;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        BufferedImage startImg = UiUtils.getImage("icon_play.png", UiUtils.IMG_SIZE_TOOLBAR, UiUtils.IMG_SIZE_TOOLBAR);
        BufferedImage greenStartImg = UiUtils.replaceColor(startImg, Colors.COLOR_START_RECORDING);
        iconStartRecording = new ImageIcon(greenStartImg);

        BufferedImage stopImg = UiUtils.getImage("icon_stop.png", UiUtils.IMG_SIZE_TOOLBAR, UiUtils.IMG_SIZE_TOOLBAR);
        BufferedImage redStopImg = UiUtils.replaceColor(stopImg, Colors.COLOR_STOP_RECORDING);
        iconStopRecording = new ImageIcon(redStopImg);

        filterSetIcon = UiUtils.getImageIcon("icon_filter.png", UiUtils.IMG_SIZE_TOOLBAR);
        noFilterIcon = UiUtils.getImageIcon("clear_filter.png", UiUtils.IMG_SIZE_TOOLBAR);

        initalizeUi();
    }

    public void setDeviceList(List<Device> deviceList) {
        if (isRecording) {
            // must stop recording before adding/removing devices
            log.error("setDeviceList: RECORDING");
            DialogHelper.showDialog(this, "Already Saving Logs", "To change devices first stop current log recording");
            return;
        }
        List<SaveLogEntry> entryList = new ArrayList<>();
        for (Device device : deviceList) {
            SaveLogEntry entry = new SaveLogEntry();
            entry.device = device;
            entryList.add(entry);
        }
        model.setEntryList(entryList);
        refreshUi();
    }

    private void stopLogging() {
        if (!isRecording) return;
        List<SaveLogEntry> entryList = model.getEntryList();
        for (SaveLogEntry entry : entryList) {
            deviceScreen.setDeviceBusy(entry.device, false);
            DeviceManager.getInstance().stopLogging(entry.device);
        }
        isRecording = false;
        model.fireTableDataChanged();
        refreshUi();
    }

    private void startLogging() {
        if (isRecording) return;
        lastLogsFolder = createLogsFolder();
        List<SaveLogEntry> entryList = model.getEntryList();
        log.trace("startLogging: filter:{}", logFilter);
        for (SaveLogEntry entry : entryList) {
            entry.numLines = 0;
            entry.size = 0;
            entry.saveFile = new File(lastLogsFolder, entry.device.serial + ".txt");

            deviceScreen.setDeviceBusy(entry.device, true);
            DeviceManager.getInstance().startLogging(entry.device, new DeviceManager.DeviceLogListener() {
                @Override
                public void handleLogEntries(List<LogEntry> logEntryList) {
                    if (!isRecording) return;

                    StringBuilder sb = new StringBuilder();
                    int numLines = 0;
                    for (LogEntry entry : logEntryList) {
                        if (logFilter != null) {
                            if (!logFilter.isMatch(entry)) return;
                        }

                        sb.append(entry.date);
                        sb.append('\t');
                        sb.append(entry.tid);
                        sb.append('\t');
                        sb.append(entry.level);
                        sb.append('\t');
                        sb.append(entry.tag);
                        sb.append('\t');
                        sb.append(entry.message);
                        sb.append('\n');

                        numLines++;
                    }
                    FileUtils.writeToFile(entry.saveFile, true, sb.toString());
                    long size = entry.saveFile.length();

                    if (numLines > 0) {
                        int finalNumLines = numLines;
                        SwingUtilities.invokeLater(() -> {
                            // only update entry on UI thread
                            entry.numLines += finalNumLines;
                            entry.size = size;
                            model.notifyEntryUpdated(entry);
                            refreshUi();
                        });
                    }
                }

                @Override
                public void handleProcessMap(Map<String, String> processMap) {
                    // do nothing
                }
            });
        }
        isRecording = true;
        model.fireTableDataChanged();
        refreshUi();
    }

    private File createLogsFolder() {
        // create folder to save logs to
        // 20211215-1441PM-1.png
        File logsFolder = getLogsFolder();
        String name = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File nowFolder = new File(logsFolder, name);
        try {
            boolean isOk = nowFolder.mkdirs();
            if (!isOk) log.error("startLogging: mkdirs FAILED: {}", nowFolder.getAbsolutePath());
            else return nowFolder;
        } catch (Exception e) {
            log.error("startLogging: Exception: {}, {}", nowFolder.getAbsolutePath(), e.getMessage());
        }
        return null;
    }

    private File getLogsFolder() {
        String downloadFolder = Utils.getDownloadFolder();
        return new File(downloadFolder, "logs");
    }

    private void refreshUi() {
        logButton.setIcon(isRecording ? iconStopRecording : iconStartRecording);
        logButton.setText(isRecording ? "Stop Logging" : "Start Logging");

        deleteButton.setEnabled(!isRecording && lastLogsFolder != null);

        filterButton.setIcon(logFilter != null ? filterSetIcon : noFilterIcon);
        filterButton.setEnabled(!isRecording);
    }

    private void initalizeUi() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        setupToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // -- table --
        table = new CustomTable("savelogs");
        setupTable();
        mainPanel.add(table.getScrollPane(), BorderLayout.CENTER);

        setupMenuBar();

        setContentPane(mainPanel);

        // start focus on table (away from filters)
        table.addAncestorListener(new RequestFocusListener());

        refreshUi();
    }

    private void setupTable() {
        model = new SaveLogsTableModel();
        table.setModel(model);
        table.setDefaultRenderer(SaveLogEntry.class, new SaveLogsCellRenderer());
        //table.getTableHeader().setDefaultRenderer(new TableHeaderRenderer());
        table.setEmptyText("No Devices!");

        // restore user-defined column sizes
        if (!table.restoreTable()) {
            // default column sizes
            table.setPreferredColWidth(SaveLogsTableModel.Columns.SIZE.toString(), 80);
        }

        table.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            if (!listSelectionEvent.getValueIsAdjusting()) {
                refreshUi();
            }
        });

        table.setDoubleClickListener((row, column, e) -> {
            SaveLogEntry entry = (SaveLogEntry) model.getValueAt(row, column);
            if (entry == null) return;
            Utils.openFile(entry.saveFile);
        });

        table.setPopupMenuListener((row, column) -> {
            if (row == -1) return null;
            JPopupMenu popupMenu = new JPopupMenu();
            UiUtils.addPopupMenuItem(popupMenu, "View Log", actionEvent -> {
                List<SaveLogEntry> entryList = model.getEntryList();
                int[] selectedRows = table.getSelectedRows();
                for (int selectedRow : selectedRows) {
                    int dataRow = table.convertRowIndexToModel(selectedRow);
                    SaveLogEntry entry = entryList.get(dataRow);
                    Utils.openFile(entry.saveFile);
                }
            });
            UiUtils.addPopupMenuItem(popupMenu, "Open Log Folder", actionEvent -> {
                List<SaveLogEntry> entryList = model.getEntryList();
                int[] selectedRows = table.getSelectedRows();
                for (int selectedRow : selectedRows) {
                    int dataRow = table.convertRowIndexToModel(selectedRow);
                    SaveLogEntry entry = entryList.get(dataRow);
                    File folder = entry.saveFile;
                    if (folder == null) {
                        folder = getLogsFolder();
                    }
                    Utils.openFolder(folder);
                    // no need to open more than 1 folder
                    break;
                }
            });

            return popupMenu;
        });

        table.setTooltipListener((row, col) -> table.getTextIfTruncated(row, col));
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdMenuItem(windowMenu, "Close Window", KeyEvent.VK_W, e -> closeWindow());

        // [CMD + 1] = show devices
        createCmdMenuItem(windowMenu, "Show Devices", KeyEvent.VK_1, e -> deviceScreen.toFront());

        // [CMD + 3] = show logs
        createCmdMenuItem(windowMenu, "View Logs", KeyEvent.VK_3, e -> deviceScreen.handleViewLogsCommand(null));

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    private void setupToolbar() {
        toolbar.setRollover(true);

        // start/stop button
        logButton = createToolbarButton(toolbar, null, null, "Start Logging", actionEvent -> toggleLoggingButton());
        toolbar.addSeparator();

        // browse
        createToolbarButton(toolbar, "icon_browse.png", "Logs", "Open Logs Folder", actionEvent -> openLogsFolder());
        // delete
        deleteButton = createToolbarButton(toolbar, "icon_trash.png", "Delete", "Delete Logs", actionEvent -> deleteLogs());

        toolbar.addSeparator();

        // right-align filters
        toolbar.add(Box.createHorizontalGlue());

        // filter
        filterButton = createToolbarButton(toolbar, "clear_filter.png", "Filter", "Set Filter", null);
        UiUtils.addRightClickListener(filterButton, e -> {
            JPopupMenu popupMenu = new JPopupMenu();

            if (SaveLogsScreen.this.logFilter != null) {
                JMenuItem item = new JMenuItem("Clear Filter", UiUtils.getImageIcon("clear_filter.png", UiUtils.IMG_SIZE_SMALL));
                item.addActionListener(e2 -> handleFilterClicked(null));
                popupMenu.add(item);
            }

            List<LogFilter> systemList = ViewLogsScreen.getSystemFilters();
            List<LogFilter> filterList = ViewLogsScreen.getUserFilters();
            systemList.addAll(filterList);
            for (LogFilter filter : systemList) {
                if (filter.filterList == null || filter.filterList.isEmpty()) continue;
                JMenuItem item = new JMenuItem(filter.name, UiUtils.getImageIcon("icon_filter.png", UiUtils.IMG_SIZE_SMALL));
                item.addActionListener(e2 -> handleFilterClicked(filter));
                popupMenu.add(item);
            }

            JMenuItem item = new JMenuItem("Add Filter", UiUtils.getImageIcon("icon_add.png", UiUtils.IMG_SIZE_SMALL));
            item.addActionListener(e2 -> handleAddFilterClicked());
            popupMenu.add(item);

            popupMenu.show(e.getComponent(), e.getX(), e.getY());
        });
    }

    private void handleAddFilterClicked() {
        LogFilter filter = AddFilterDialog.showAddFilterDialog(this, null);
        if (filter != null) {
            ViewLogsScreen.addFilter(null, filter);
            logFilter = filter;
            refreshUi();
        }
    }

    private void handleFilterClicked(LogFilter filter) {
        this.logFilter = filter;
        if (filter != null) {
            filterButton.setToolTipText("Filter: " + filter);
        } else {
            filterButton.setToolTipText("Click to set filter");
        }
        refreshUi();
    }

    private void openLogsFolder() {
        File logsFolder = getLogsFolder();
        Utils.openFolder(logsFolder);
    }

    private void deleteLogs() {
        if (lastLogsFolder == null) return;
        String msg = String.format("Delete last log folder: %s?", lastLogsFolder.getAbsolutePath());
        boolean isDelete = DialogHelper.showOptionDialog(this, "Delete Logs", msg, new String[]{"Yes", "No"});
        if (isDelete) {
            log.trace("deleteLogs: {}", lastLogsFolder.getAbsolutePath());
            FileUtils.deleteFolder(lastLogsFolder);
            lastLogsFolder = null;
            deleteButton.setEnabled(false);
        }
    }

    private void toggleLoggingButton() {
        if (isRecording) stopLogging();
        else startLogging();
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        if (state == WindowState.CLOSED) {
            closeWindow();
        }
    }

    private void closeWindow() {
        log.trace("closeWindow");
        stopLogging();
        saveFrameSize();
        deviceScreen.handleSaveLogsClosed();
        dispose();
    }

    public void updateDeviceState() {
        model.fireTableDataChanged();
    }
}

