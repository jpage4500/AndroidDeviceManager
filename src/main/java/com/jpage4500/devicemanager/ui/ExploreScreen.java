package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.ExploreTableModel;
import com.jpage4500.devicemanager.table.utils.ExplorerCellRenderer;
import com.jpage4500.devicemanager.table.utils.ExplorerRowComparator;
import com.jpage4500.devicemanager.table.utils.ExplorerRowFilter;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * create and manage device view
 */
public class ExploreScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(ExploreScreen.class);

    private static final String HINT_FILTER_DEVICES = "Filter files...";
    public static final int MAX_PATH_SAVE = 10;

    private final DeviceScreen deviceScreen;

    public CustomTable table;
    public ExploreTableModel model;
    public TableRowSorter<TableModel> rowSorter;
    private ExplorerRowFilter rowFilter;

    public JToolBar toolbar;

    private final Device device;
    private boolean wasOffline = true;

    private String selectedPath = "/sdcard";
    private List<String> prevPathList = new ArrayList<>();
    private String errorMessage;

    private HintTextField filterTextField;
    private JButton rootButton;
    private boolean useRoot;

    // status bar items
    private HoverLabel pathLabel;       // current path
    private JLabel errorLabel;
    private JLabel countLabel;          // total files / # selected

    public ExploreScreen(DeviceScreen deviceScreen, Device device) {
        super("browse-" + device.serial, 500, 500);
        this.deviceScreen = deviceScreen;
        this.device = device;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        initalizeUi();
        updateDeviceState();
    }

    public void updateDeviceState() {
        if (device.isOnline) {
            setTitle("Browse [" + device.getDisplayName() + "]");
            if (wasOffline) {
                refreshFiles();
                wasOffline = false;
            }
        } else {
            setTitle("OFFLINE [" + device.getDisplayName() + "]");
            wasOffline = true;
        }
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
        mainPanel.add(table.getScrollPane(), BorderLayout.CENTER);

        // -- statusbar --
        setupStatusBar(mainPanel);

        setContentPane(mainPanel);

        setupMenuBar();
        setupPopupMenu();

        table.requestFocus();
    }

    private void setupStatusBar(JPanel mainPanel) {
        JPanel statusBar = new JPanel(new BorderLayout());
        UiUtils.setEmptyBorder(statusBar, 0, 0);

        // bookmark
        ImageIcon icon = UiUtils.getImageIcon("icon_bookmark.png", 15);
        pathLabel = new HoverLabel(selectedPath, icon);
        UiUtils.addClickListener(pathLabel, this::showFavoritePopup);
        statusBar.add(pathLabel, BorderLayout.WEST);

        // empty space
        errorLabel = new JLabel();
        UiUtils.setEmptyBorder(errorLabel);
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusBar.add(errorLabel, BorderLayout.CENTER);

        countLabel = new JLabel();
        UiUtils.setEmptyBorder(countLabel);
        statusBar.add(countLabel, BorderLayout.EAST);

        mainPanel.add(statusBar, BorderLayout.SOUTH);
    }

    private void showFavoritePopup(MouseEvent e) {
        JPopupMenu popupMenu = new JPopupMenu();
        List<String> pathList = getFavoritePathList();
        // add favorites
        for (String path : pathList) {
            if (TextUtils.equals(path, selectedPath)) continue;
            String fav = TextUtils.truncateStart(path, 25);
            JMenuItem item = new JMenuItem(fav, UiUtils.getImageIcon("icon_open_folder.png", 15));
            item.addActionListener(actionEvent -> showFolder(path));
            UiUtils.setEmptyBorder(item);
            popupMenu.add(item);
        }
        popupMenu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (SwingUtilities.isRightMouseButton(mouseEvent)) {
                    log.debug("mouseClicked: LEFT");
                } else {
                    log.debug("mouseClicked: RIGHT-CLICK");
                }
            }
        });
        popupMenu.addSeparator();
        if (!pathList.contains(selectedPath)) {
            // add current item
            String path = TextUtils.truncateStart(selectedPath, 25);
            ImageIcon favIcon = UiUtils.getImageIcon("icon_star.png", 15);
            JMenuItem currentItem = new JMenuItem("Bookmark [" + path + "]", favIcon);
            currentItem.addActionListener(actionEvent -> bookmarkPath(selectedPath));
            UiUtils.setEmptyBorder(currentItem);
            popupMenu.add(currentItem);
        } else {
            // remove current item
            JMenuItem currentItem = new JMenuItem("Remove Bookmark", UiUtils.getImageIcon("icon_trash.png", 15));
            currentItem.addActionListener(actionEvent -> removeBookmark(selectedPath));
            UiUtils.setEmptyBorder(currentItem);
            popupMenu.add(currentItem);
        }
        popupMenu.addSeparator();
        // go to folder
        JMenuItem goToItem = new JMenuItem("Go to folder...", UiUtils.getImageIcon("icon_edit.png", 15));
        goToItem.addActionListener(actionEvent -> handleGoToFolder());
        UiUtils.setEmptyBorder(goToItem);
        popupMenu.add(goToItem);

        popupMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void removeBookmark(String selectedPath) {
        List<String> pathList = getFavoritePathList();
        pathList.remove(selectedPath);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_GO_TO_FOLDER_LIST, GsonHelper.toJson(pathList));
    }

    private List<String> getFavoritePathList() {
        String folders = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_GO_TO_FOLDER_LIST);
        return GsonHelper.stringToList(folders, String.class);
    }

    private void bookmarkPath(String path) {
        List<String> pathList = getFavoritePathList();
        if (pathList.contains(path)) return;
        pathList.add(0, path);
        if (pathList.size() > 10) pathList.subList(10, pathList.size()).clear();
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_GO_TO_FOLDER_LIST, GsonHelper.toJson(pathList));
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        if (state == WindowState.CLOSED) {
            closeWindow();
        }
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

        // [CMD + 3] = show logs
        createCmdAction(windowMenu, DeviceScreen.SHOW_LOG_VIEWER, KeyEvent.VK_3, e -> deviceScreen.handleLogsCommand(null));

        // [CMD + T] = hide toolbar
        createCmdAction(windowMenu, "Hide Toolbar", KeyEvent.VK_T, e -> hideToolbar());

        JMenu fileMenu = new JMenu("Files");

        // [CMD + BACKSPACE] = delete files
        createCmdAction(fileMenu, "Delete", KeyEvent.VK_BACK_SPACE, e -> handleDelete());

        // [CMD + G] = go to folder
        createCmdAction(fileMenu, "Go to folder..", KeyEvent.VK_G, e -> handleGoToFolder());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(fileMenu);
        setJMenuBar(menubar);
    }

    private void closeWindow() {
        log.trace("closeWindow: {}", device.getDisplayName());
        saveFrameSize();
        table.saveTable();
        deviceScreen.handleBrowseClosed(device.serial);
        dispose();
    }

    private void hideToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    private void setupTable() {
        model = new ExploreTableModel();
        table.setModel(model);
        table.setDefaultRenderer(DeviceFile.class, new ExplorerCellRenderer());
        //table.getTableHeader().setDefaultRenderer(new TableHeaderRenderer());
        table.setEmptyText("No Files!");

        // restore user-defined column sizes
        if (!table.restoreTable()) {
            // default column sizes
            table.setPreferredColWidth(ExploreTableModel.Columns.SIZE.toString(), 80);
        }
        table.setMaxColWidth(ExploreTableModel.Columns.SIZE.toString(), 100);
        table.setMaxColWidth(ExploreTableModel.Columns.DATE.toString(), 167);

        // ENTER -> click on file
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

        setupPopupMenu();

        table.getSelectionModel().addListSelectionListener(listSelectionEvent -> {
            if (!listSelectionEvent.getValueIsAdjusting()) {
                refreshUi();
            }
        });

        table.setDoubleClickListener((row, column, e) -> handleFileClicked());

        table.setPopupMenuListener((row, column) -> {
            // TODO
            return null;
        });

        table.setTooltipListener((row, col) -> table.getTextIfTruncated(row, col));

        filterTextField.setupSearch(table);

        // support drag and drop of files IN TO explorer window
        new DropTarget(table.getScrollPane(), new FileDragAndDropListener(table.getScrollPane(), this::handleFilesDropped));
    }

    private void handleFileClicked() {
        if (!device.isOnline) {
            errorMessage = "device offline";
            refreshUi();
            return;
        }
        List<DeviceFile> selectedFiles = getSelectedFiles(true);
        if (log.isTraceEnabled()) log.trace("handleFileClicked: SELECTED FILES: " + GsonHelper.toJson(selectedFiles));
        if (selectedFiles.isEmpty()) return;
        DeviceFile selectedFile = selectedFiles.get(0);
        if (selectedFile.isDirectory) {
            if (selectedFile.isUpFolder()) {
                String prevPath = selectedPath;
                int pos = selectedPath.lastIndexOf('/');
                if (pos > 0) {
                    setPath(selectedPath.substring(0, pos));
                } else if (pos == 0) {
                    // root folder
                    setPath("");
                }
                log.debug("handleFileClicked: UP: {} -> {}", prevPath, selectedPath);
            } else {
                // append selected folder to current path
                if (TextUtils.equals(selectedPath, "/")) setPath(selectedFile.name);
                else setPath(selectedPath + "/" + selectedFile.name);
            }
            errorMessage = null;
            refreshFiles();
        } else {
            handleDownload();
        }
    }

    /**
     * set *next* path to list files
     *
     * @param path path or null to remove current path and revert to previously set one
     */
    private void setPath(String path) {
        if (path == null) {
            if (!prevPathList.isEmpty()) {
                // remove bad path
                prevPathList.remove(prevPathList.size() - 1);
            }
            // reset good path
            if (!prevPathList.isEmpty()) {
                path = prevPathList.get(prevPathList.size() - 1);
            } else {
                path = "";
            }
        }

        // selectedPath should never end with "/"
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 2);
        else if (TextUtils.isEmpty(path)) path = "/";

        // path should always start with "/"
        if (!path.startsWith("/")) path = "/" + path;

        prevPathList.add(path);
        if (prevPathList.size() > MAX_PATH_SAVE) {
            prevPathList.remove(0);
        }
        log.trace("setPath: {}", path);
        selectedPath = path;
    }

    private void refreshFiles() {
        if (!device.isOnline) return;
        DeviceManager.getInstance().listFiles(device, selectedPath, useRoot, (fileList, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                errorMessage = error;
                boolean doRefresh = false;
                if (useRoot && TextUtils.equals(error, DeviceManager.ERR_ROOT_NOT_AVAILABLE)) {
                    JOptionPane.showMessageDialog(this, "ROOT not available!");
                    toggleRoot();
                } else if (TextUtils.equals(error, DeviceManager.ERR_NOT_A_DIRECTORY)) {
                    if (prevPathList.isEmpty() && TextUtils.equals(selectedPath, "/sdcard")) {
                        // some devices don't allow browsing /sdcard (Samsung S10) --
                        doRefresh = true;
                    }
                }
                // revert to previous directory
                setPath(null);
                refreshUi();
                if (doRefresh) refreshFiles();
                return;
            }
            if (fileList == null) {
                log.debug("refreshFiles: NO FILES");
                errorMessage = "permission denied - " + selectedPath;
                setPath(null);
                log.trace("refreshFiles: selectedPath={}", selectedPath);
            } else {
                // clear out any previous set filter and error
                filterTextField.reset();
                errorMessage = null;
                // add ".." to top of list
                if (!TextUtils.isEmpty(selectedPath) && !selectedPath.equals("/")) {
                    DeviceFile upFile = new DeviceFile();
                    upFile.name = "..";
                    upFile.isDirectory = true;
                    fileList.add(0, upFile);
                }
                // TODO: backup selected file(s)
                model.setFileList(fileList);
                // TODO: re-select previously selected file(s)
                table.changeSelection(0, 0, true, false);
            }
            refreshUi();
        }));
    }

    private void refreshUi() {
        // file path
        String path = selectedPath.isEmpty() ? "/" : selectedPath;
        UiUtils.setText(pathLabel, path, 25);

        errorLabel.setText(errorMessage);

        // selected row(s)
        int selectedRowCount = table.getSelectedRowCount();
        int rowCount = table.getRowCount();
        if (selectedRowCount > 1) {
            countLabel.setText(selectedRowCount + " / " + rowCount);
        } else {
            countLabel.setText("total: " + rowCount);
        }
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

    private void handleFilesDropped(List<File> fileList) {
        if (!device.isOnline) return;

        FileUtils.FileStats stats = FileUtils.getFileStats(fileList);

        final JDialog dialog = new JDialog();
        dialog.setAlwaysOnTop(true);
        String title = "Copy File(s)";
        String msg = "Copy " + stats.numTotal + " file(s) to " + selectedPath + "?";
        int rc = JOptionPane.showConfirmDialog(dialog, msg, title, JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        deviceScreen.setDeviceBusy(device, true);
        DeviceManager deviceManager = DeviceManager.getInstance();
        deviceManager.copyFiles(device, fileList, selectedPath, (numCompleted, numTotal, msg1) -> {
            String status = String.format("%d/%d - %s", numCompleted, numTotal, msg1);
            errorLabel.setText(status);
        }, (isSuccess, error) -> {
            deviceScreen.setDeviceBusy(device, false);
            errorLabel.setText(error);
            refreshFiles();
        });
    }

    private void setupToolbar() {
        toolbar.setRollover(true);

        createToolbarButton(toolbar, "icon_open_folder.png", "Go To..", "Open Folder", actionEvent -> handleGoToFolder());
        createToolbarButton(toolbar, "icon_download.png", "Download", "Download Files", actionEvent -> handleDownload());
        toolbar.addSeparator();
        createToolbarButton(toolbar, "icon_folder_new.png", "New Folder", "New Folder", actionEvent -> handleNewFolder());
        createToolbarButton(toolbar, "icon_delete.png", "Delete", "Delete Files", actionEvent -> handleDelete());

        toolbar.add(Box.createHorizontalGlue());

        filterTextField = new HintTextField(HINT_FILTER_DEVICES, this::filterDevices);
        filterTextField.setPreferredSize(new Dimension(150, 40));
        filterTextField.setMinimumSize(new Dimension(10, 40));
        filterTextField.setMaximumSize(new Dimension(200, 40));
        toolbar.add(filterTextField);

        createToolbarButton(toolbar, "icon_refresh.png", "Refresh", "Refresh Files", actionEvent -> refreshFiles());

        // root toolbar button
        useRoot = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_USE_ROOT, false);
        rootButton = createToolbarButton(toolbar, "root.png", "Root", "Root Mode", actionEvent -> toggleRoot());
        refreshRootButton();
    }

    private void toggleRoot() {
        useRoot = !useRoot;
        PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_USE_ROOT, useRoot);
        refreshRootButton();
        refreshFiles();
    }

    private void refreshRootButton() {
        ImageIcon icon = UiUtils.getImageIcon(useRoot ? "root_enabled.png" : "root.png", 40);
        rootButton.setIcon(icon);
        rootButton.setToolTipText(useRoot ? "Disable root mode" : "Enable root mode");
    }

    private void handleNewFolder() {
        if (!device.isOnline) return;
        String result = (String) JOptionPane.showInputDialog(this,
                "Enter Folder Name",
                "New Folder",
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                null);
        if (TextUtils.isEmpty(result)) return;

        DeviceManager.getInstance().createFolder(device, selectedPath + "/" + result, (isSuccess, error) -> refreshFiles());
    }

    private void handleDownload() {
        if (!device.isOnline) return;
        List<DeviceFile> selectedFileList = getSelectedFiles(false);
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        boolean isSingleFile = selectedFileList.size() == 1;
        String msg = isSingleFile ?
                selectedFileList.get(0).name :
                selectedFileList.size() + " files(s)";

        // prompt to install/copy
        int rc = JOptionPane.showConfirmDialog(this,
                "Download " + msg + "?",
                "Download?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        String downloadFolder = Utils.getDownloadFolder();
        for (DeviceFile file : selectedFileList) {
            if (file.isReadOnly) {
                JOptionPane.showMessageDialog(this, "File is read-only!", "Read-only", JOptionPane.ERROR_MESSAGE);
                return;
            }
            File downloadFile = new File(downloadFolder, file.name);
            DeviceManager.getInstance().downloadFile(device, selectedPath, file, downloadFile, (isSuccess, error) -> {
                if (isSuccess && isSingleFile) {
                    if (downloadFile.exists()) {
                        int openRc = JOptionPane.showConfirmDialog(this,
                                "Open " + downloadFile.getName() + "?",
                                "Open File?", JOptionPane.YES_NO_OPTION);
                        if (openRc != JOptionPane.YES_OPTION) return;
                        Utils.openFile(downloadFile);
                    }
                }
            });
        }
    }

    private void handleDelete() {
        if (!device.isOnline) return;
        List<DeviceFile> selectedFileList = getSelectedFiles(false);
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Iterator<DeviceFile> iterator = selectedFileList.iterator(); iterator.hasNext(); ) {
            DeviceFile file = iterator.next();
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(file.name);
        }

        int rc = JOptionPane.showConfirmDialog(this,
                "Delete " + selectedFileList.size() + " files(s)?\n\n" + sb,
                "Delete Files?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (DeviceFile file : selectedFileList) {
            DeviceManager.getInstance().deleteFile(device, selectedPath, file, (isSuccess, error) -> refreshFiles());
        }
    }

    private void handleGoToFolder() {
        List<String> pathList = getFavoritePathList();
        JComboBox comboBox = new JComboBox(pathList.toArray(new String[]{}));
        comboBox.setEditable(true);
        int rc = JOptionPane.showOptionDialog(this, comboBox, "Go to folder", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return;

        Object selectedObj = comboBox.getSelectedItem();
        if (selectedObj == null) return;
        String selectedItem = (String) selectedObj;
        if (TextUtils.isEmpty(selectedItem)) return;
        //bookmarkPath(selectedItem);
        log.debug("handleGoToFolder: {}", selectedItem);
        showFolder(selectedItem);
    }

    private void showFolder(String path) {
        setPath(path);
        refreshFiles();
    }

    private void handleCopyPath() {
        List<DeviceFile> selectedFileList = getSelectedFiles(false);
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Iterator<DeviceFile> iterator = selectedFileList.iterator(); iterator.hasNext(); ) {
            DeviceFile file = iterator.next();
            if (sb.length() > 0) sb.append('\n');
            sb.append(selectedPath + "/" + file.name);
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private void handleCopyName() {
        List<DeviceFile> selectedFileList = getSelectedFiles(false);
        if (selectedFileList.isEmpty()) {
            showSelectDevicesDialog();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Iterator<DeviceFile> iterator = selectedFileList.iterator(); iterator.hasNext(); ) {
            DeviceFile file = iterator.next();
            if (sb.length() > 0) sb.append('\n');
            sb.append(file.name);
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection stringSelection = new StringSelection(sb.toString());
        clipboard.setContents(stringSelection, null);
    }

    private List<DeviceFile> getSelectedFiles(boolean includeUpFolder) {
        List<DeviceFile> selectedDeviceList = new ArrayList<>();
        int[] selectedRows = table.getSelectedRows();
        for (int selectedRow : selectedRows) {
            // convert view row to data row (in case user changed sort order)
            int dataRow = table.convertRowIndexToModel(selectedRow);
            DeviceFile deviceFile = model.getDeviceFileAtRow(dataRow);
            if (deviceFile != null) {
                // ingore ".." folder
                if (!includeUpFolder && deviceFile.isUpFolder()) continue;
                selectedDeviceList.add(deviceFile);
            }
        }
        return selectedDeviceList;
    }

    private void showSelectDevicesDialog() {
        JOptionPane.showConfirmDialog(this, "Select 1 or more files to use this feature", "No files selected", JOptionPane.DEFAULT_OPTION);
    }

    private void filterDevices(String text) {
        if (TextUtils.notEmpty(text)) {
            rowFilter.searchFor = text;
            rowSorter.setRowFilter(rowFilter);
            //rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        } else {
            rowSorter.setRowFilter(null);
        }
    }

}
