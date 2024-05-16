package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.RemoteUpFolder;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.ExploreTableModel;
import com.jpage4500.devicemanager.table.utils.ExplorerCellRenderer;
import com.jpage4500.devicemanager.table.utils.ExplorerRowComparator;
import com.jpage4500.devicemanager.table.utils.ExplorerRowFilter;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.EmptyView;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.MyDragDropListener;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.vidstige.jadb.RemoteFile;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public class ExploreView extends BaseFrame {
    private static final Logger log = LoggerFactory.getLogger(ExploreView.class);

    public static final String PREF_DOWNLOAD_FOLDER = "PREF_DOWNLOAD_FOLDER";
    public static final String PREF_GO_TO_FOLDER_LIST = "PREF_GO_TO_FOLDER_LIST";
    private static final String HINT_FILTER_DEVICES = "Filter files...";
    public static final int MAX_PATH_SAVE = 10;

    private final DeviceView deviceView;

    public CustomTable table;
    public ExploreTableModel model;
    public TableRowSorter<TableModel> rowSorter;
    private ExplorerRowFilter rowFilter;

    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;

    public int selectedColumn = -1;

    private final Device device;

    private String selectedPath = "/sdcard";
    private List<String> prevPathList = new ArrayList<>();
    private String errorMessage;

    public ExploreView(DeviceView deviceView, Device device) {
        super("browse");
        this.deviceView = deviceView;
        this.device = device;
        initalizeUi();
        setTitle("Browse " + device.getDisplayName());

        refreshFiles();
    }

    protected void initalizeUi() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        setupToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // -- table --
        table = new CustomTable("browse");
        setupTable();
        JScrollPane scrollPane = new JScrollPane(table);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // -- statusbar --
        statusBar = new StatusBar();
        statusBar.setLeftLabelListener(this::handleGoToFolder);
        mainPanel.add(statusBar, BorderLayout.SOUTH);
        setContentPane(mainPanel);

        setupMenuBar();
        setupPopupMenu();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                table.persist();
            }
        });

        //setVisible(true);

        JRootPane rootPane = SwingUtilities.getRootPane(table);
        emptyView = new EmptyView("No Files");
        rootPane.setGlassPane(emptyView);
        emptyView.setOpaque(false);
        emptyView.setVisible(true);

        table.requestFocus();
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSING -> table.persist();
            case ACTIVATED -> {
                //refreshFiles();
            }
        }
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> {
            setVisible(false);
            dispose();
        });

        // [CMD + 1] = show devices
        createCmdAction(windowMenu, "Show Devices", KeyEvent.VK_1, e -> {
            deviceView.toFront();
        });

        // [CMD + 3] = show logs
        createCmdAction(windowMenu, "View Logs", KeyEvent.VK_3, e -> {
            deviceView.handleLogsCommand();
        });

        JMenu fileMenu = new JMenu("Files");

        // [CMD + BACKSPACE] = delete files
        createCmdAction(fileMenu, "Delete", KeyEvent.VK_BACK_SPACE, e -> {
            handleDelete();
        });

        // [CMD + G] = go to folder
        createCmdAction(fileMenu, "Go to folder..", KeyEvent.VK_G, e -> {
            handleGoToFolder();
        });

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(fileMenu);
        setJMenuBar(menubar);
    }

    private void setupTable() {
        table.setShowTooltips(true);
        model = new ExploreTableModel();
        table.setModel(model);
        // render folder column as images with custom renderer
        table.setDefaultRenderer(RemoteFile.class, new ExplorerCellRenderer());
        //table.getTableHeader().setDefaultRenderer(new TableHeaderRenderer());

        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(enter, "Enter");
        table.getActionMap().put("Enter", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleFileClicked();
            }
        });

        rowSorter = new TableRowSorter<>(table.getModel());
        for (int i = 0; i < model.getColumnCount(); i++) {
            rowSorter.setComparator(i, new ExplorerRowComparator(ExploreTableModel.Columns.values()[i]));
        }
        table.setRowSorter(rowSorter);

        // default to order by name
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
        rowSorter.setSortKeys(sortKeys);
        rowFilter = new ExplorerRowFilter();
        rowSorter.setRowFilter(rowFilter);

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
                    handleFileClicked();
                }
            }
        });

        setupPopupMenu();

        table.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            if (!listSelectionEvent.getValueIsAdjusting()) {
                refreshUi();
            }
        });

        // support drag and drop of files
        MyDragDropListener dragDropListener = new MyDragDropListener(table, false, this::handleFilesDropped);
        new DropTarget(table, dragDropListener);
    }

    private void handleFileClicked() {
        List<RemoteFile> selectedFiles = getSelectedFiles();
        log.debug("handleFileClicked: SELECTED FILES: " + GsonHelper.toJson(selectedFiles));
        if (selectedFiles.isEmpty()) return;
        RemoteFile selectedFile = selectedFiles.get(0);

        if (selectedFile.isDirectory() || selectedFile.isSymbolicLink()) {
            if (TextUtils.equalsIgnoreCase(selectedFile.getName(), "..")) {
                String prevPath = selectedPath;
                int pos = selectedPath.lastIndexOf('/');
                if (pos > 0) {
                    setPath(selectedPath.substring(0, pos));
                } else if (pos == 0) {
                    // root folder
                    setPath("");
                }
                log.debug("mouseClicked: UP: {} -> {}", prevPath, selectedPath);
            } else {
                // append selected folder to current path
                setPath(selectedPath + "/" + selectedFile.getName());
            }
            refreshFiles();
        } else {
            handleDownload();
        }
    }

    private void setPath(String path) {
        prevPathList.add(path);
        if (prevPathList.size() > MAX_PATH_SAVE) {
            prevPathList.remove(0);
        }

        selectedPath = path;
    }

    private void refreshFiles() {
        if (device == null) return;
        DeviceManager.getInstance().listFiles(device, selectedPath, fileList -> {
            SwingUtilities.invokeLater(() -> {
                if (fileList == null) {
                    log.debug("refreshFiles: NO FILES");
                    errorMessage = "permission denied - " + selectedPath;
                    // remove bad path
                    prevPathList.remove(prevPathList.size() - 1);
                    if (!prevPathList.isEmpty()) {
                        // reset good path
                        selectedPath = prevPathList.get(prevPathList.size() - 1);
                    } else {
                        selectedPath = "";
                    }
                    log.trace("refreshFiles: selectedPath={}", selectedPath);
                } else {
                    errorMessage = null;
                    // TODO: add ".." to top of list
                    log.debug("refreshFiles: GOT:{}, selectedPath={}", fileList.size(), selectedPath);
                    if (!TextUtils.isEmpty(selectedPath)) {
                        fileList.add(0, new RemoteUpFolder());
                    }
                    model.setFileList(fileList);
                }
                refreshUi();
            });
        });
    }

    private void refreshUi() {
        // file path
        statusBar.setLeftLabel(selectedPath);

        statusBar.setCenterLabel(errorMessage);

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

        JMenuItem downloadItem = new JMenuItem("Download");
        downloadItem.addActionListener(actionEvent -> handleDownload());
        popupMenu.add(downloadItem);

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(actionEvent -> handleDelete());
        popupMenu.add(deleteItem);

        JMenuItem copyNameItem = new JMenuItem("Copy Name");
        copyNameItem.addActionListener(actionEvent -> handleCopyName());
        popupMenu.add(copyNameItem);

        JMenuItem copyPathItem = new JMenuItem("Copy Path");
        copyPathItem.addActionListener(actionEvent -> handleCopyPath());
        popupMenu.add(copyPathItem);

        table.setComponentPopupMenu(popupMenu);
    }

    private String getDeviceField(RemoteFile deviceFile, ExploreTableModel.Columns column) {
        String val;
        switch (column) {
            case NAME:
                val = deviceFile.getName();
                break;
            case SIZE:
                val = String.valueOf(deviceFile.getSize());
                break;
            case DATE:
                val = String.valueOf(deviceFile.getLastModified());
                break;
            default:
                val = column.name();
                break;
        }
        if (TextUtils.isEmpty(val)) return "";
        else return val;
    }

    private void handleFilesDropped(List<File> fileList) {
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
        msg += " to " + selectedPath;
        msg += "?";

        // prompt to install/copy
        // NOTE: using JDialog.setAlwaysOnTap to bring app to foreground on drag and drop operations
        final JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);
        int rc = JOptionPane.showConfirmDialog(dialog, msg, title, JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (File file : fileList) {
            String filename = file.getName();
            if (filename.endsWith(".apk")) {
                DeviceManager.getInstance().installApp(device, file, null);
            } else {
                DeviceManager.getInstance().copyFile(device, file, selectedPath + "/", null);
            }
        }
    }

    private void setupToolbar() {
        toolbar.setRollover(true);

        createToolbarButton(toolbar, "icon_open_folder.png", "Go To..", "Open Folder", actionEvent -> handleGoToFolder());
        createToolbarButton(toolbar, "icon_download.png", "Download", "Download Files", actionEvent -> handleDownload());
        toolbar.addSeparator();
        createToolbarButton(toolbar, "icon_delete.png", "Delete", "Delete Files", actionEvent -> handleDelete());

        toolbar.add(Box.createHorizontalGlue());

        HintTextField textField = new HintTextField(HINT_FILTER_DEVICES, this::filterDevices);
        textField.setPreferredSize(new Dimension(150, 40));
        textField.setMinimumSize(new Dimension(10, 40));
        textField.setMaximumSize(new Dimension(200, 40));
        toolbar.add(textField);

        createToolbarButton(toolbar, "icon_refresh.png", "Refresh", "Refresh Files", actionEvent -> refreshFiles());
    }

    private void handleDownload() {
        List<RemoteFile> selectedFileList = getSelectedFiles();
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        boolean isSingleFile = selectedFileList.size() == 1;
        String msg = isSingleFile ?
                selectedFileList.get(0).getName() :
                selectedFileList.size() + " files(s)";

        // prompt to install/copy
        int rc = JOptionPane.showConfirmDialog(this,
                "Download " + msg + "?",
                "Download?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        Preferences preferences = Preferences.userRoot();
        String downloadFolder = preferences.get(ExploreView.PREF_DOWNLOAD_FOLDER, "~/Downloads");
        for (RemoteFile file : selectedFileList) {
            File downloadFile = new File(downloadFolder, file.getName());
            DeviceManager.getInstance().downloadFile(device, file, downloadFile, isSuccess -> {
                if (isSuccess && isSingleFile) {
                    if (downloadFile.exists()) {
                        int openRc = JOptionPane.showConfirmDialog(this,
                                "Open " + downloadFile.getName() + "?",
                                "Open File?", JOptionPane.YES_NO_OPTION);
                        if (openRc != JOptionPane.YES_OPTION) return;
                        try {
                            Desktop.getDesktop().open(downloadFile);
                        } catch (IOException e) {
                            log.error("handleDownload " + e.getMessage());
                        }
                    }
                }
            });
        }
    }

    private void handleDelete() {
        List<RemoteFile> selectedFileList = getSelectedFiles();
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Iterator<RemoteFile> iterator = selectedFileList.iterator(); iterator.hasNext(); ) {
            RemoteFile file = iterator.next();
            if (sb.length() > 0) sb.append('\n');
            sb.append(file.getName());
        }

        int rc = JOptionPane.showConfirmDialog(this,
                "Delete " + selectedFileList.size() + " files(s)?\n\n" + sb,
                "Delete Files?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (RemoteFile file : selectedFileList) {
            DeviceManager.getInstance().deleteFile(device, file, isSuccess -> {
                refreshFiles();
            });
        }
    }

    private void handleGoToFolder() {
        Preferences preferences = Preferences.userRoot();
        String folders = preferences.get(PREF_GO_TO_FOLDER_LIST, null);
        List<String> customList = GsonHelper.stringToList(folders, String.class);

        JComboBox comboBox = new JComboBox(customList.toArray(new String[]{}));
        comboBox.setEditable(true);
        int rc = JOptionPane.showOptionDialog(this, comboBox, "Go to folder", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return;
        String selectedItem = comboBox.getSelectedItem().toString();
        if (TextUtils.isEmpty(selectedItem)) return;
        // remove from list
        customList.remove(selectedItem);
        // add to top of list
        customList.add(0, selectedItem);
        // only save last 10 entries
        if (customList.size() > 10) {
            customList = customList.subList(0, 10);
        }
        preferences.put(PREF_GO_TO_FOLDER_LIST, GsonHelper.toJson(customList));
        log.debug("handleGoToFolder: {}", selectedItem);
        setPath(selectedItem);
        refreshFiles();
    }

    private void handleCopyPath() {
        List<RemoteFile> selectedFileList = getSelectedFiles();
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Iterator<RemoteFile> iterator = selectedFileList.iterator(); iterator.hasNext(); ) {
            RemoteFile file = iterator.next();
            if (sb.length() > 0) sb.append('\n');
            sb.append(selectedPath + "/" + file.getName());
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private void handleCopyName() {
        List<RemoteFile> selectedFileList = getSelectedFiles();
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Iterator<RemoteFile> iterator = selectedFileList.iterator(); iterator.hasNext(); ) {
            RemoteFile file = iterator.next();
            if (sb.length() > 0) sb.append('\n');
            sb.append(file.getName());
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private List<RemoteFile> getSelectedFiles() {
        List<RemoteFile> selectedDeviceList = new ArrayList<>();
        int[] selectedRows = table.getSelectedRows();
        for (int selectedRow : selectedRows) {
            // convert view row to data row (in case user changed sort order)
            int dataRow = table.convertRowIndexToModel(selectedRow);
            RemoteFile deviceFile = model.getDeviceFileAtRow(dataRow);
            if (deviceFile != null) selectedDeviceList.add(deviceFile);
        }
        return selectedDeviceList;
    }

    private void showSelectDevicesDialog() {
        JOptionPane.showConfirmDialog(this, "Select 1 or more files to use this feature", "No files selected", JOptionPane.DEFAULT_OPTION);
    }

    private void filterDevices(String text) {
        if (text.length() > 0 && !TextUtils.equals(text, HINT_FILTER_DEVICES)) {
            rowFilter.searchFor = text;
            rowSorter.setRowFilter(rowFilter);
            //rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        } else {
            rowSorter.setRowFilter(null);
        }
    }

}
