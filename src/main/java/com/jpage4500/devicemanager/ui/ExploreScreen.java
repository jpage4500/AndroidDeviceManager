package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.ExploreTableModel;
import com.jpage4500.devicemanager.table.utils.ExplorerCellRenderer;
import com.jpage4500.devicemanager.table.utils.ExplorerRowComparator;
import com.jpage4500.devicemanager.table.utils.ExplorerRowFilter;
import com.jpage4500.devicemanager.ui.views.CustomTable;
import com.jpage4500.devicemanager.ui.views.EmptyView;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.FileDragAndDropListener;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public class ExploreScreen extends BaseScreen implements CustomTable.TableListener {
    private static final Logger log = LoggerFactory.getLogger(ExploreScreen.class);

    public static final String PREF_DOWNLOAD_FOLDER = "PREF_DOWNLOAD_FOLDER";
    public static final String PREF_GO_TO_FOLDER_LIST = "PREF_GO_TO_FOLDER_LIST";
    private static final String HINT_FILTER_DEVICES = "Filter files...";
    public static final int MAX_PATH_SAVE = 10;
    public static final String PREF_USE_ROOT = "PREF_USE_ROOT";

    private final DeviceScreen deviceScreen;

    public CustomTable table;
    public ExploreTableModel model;
    public TableRowSorter<TableModel> rowSorter;
    private ExplorerRowFilter rowFilter;

    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;

    private final Device device;

    private String selectedPath = "/sdcard";
    private List<String> prevPathList = new ArrayList<>();
    private String errorMessage;

    private HintTextField filterTextField;
    private JButton rootButton;
    private boolean useRoot;

    public ExploreScreen(DeviceScreen deviceScreen, Device device) {
        super("browse");
        this.deviceScreen = deviceScreen;
        this.device = device;
        initalizeUi();
        updateDeviceState();
    }

    public void updateDeviceState() {
        if (device.isOnline) {
            setTitle("Browse [" + device.getDisplayName() + "]");
            refreshFiles();
        } else {
            setTitle("OFFLINE [" + device.getDisplayName() + "]");
        }
    }

    protected void initalizeUi() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // -- toolbar --
        toolbar = new JToolBar("Applications");
        setupToolbar();
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // -- table --
        table = new CustomTable("browse", this);
        setupTable();
        mainPanel.add(table.getScrollPane(), BorderLayout.CENTER);

        // -- statusbar --
        statusBar = new StatusBar();
        setupStatusBar();
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

    private void setupStatusBar() {
        statusBar.setLeftLabelListener(this::handleGoToFolder);
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        switch (state) {
            case CLOSED -> table.persist();
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
            deviceScreen.toFront();
        });

        // [CMD + 3] = show logs
        createCmdAction(windowMenu, "View Logs", KeyEvent.VK_3, e -> {
            deviceScreen.handleLogsCommand();
        });

        // [CMD + T] = hide toolbar
        createCmdAction(windowMenu, "Hide Toolbar", KeyEvent.VK_T, e -> {
            hideToolbar();
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

    private void hideToolbar() {
        toolbar.setVisible(!toolbar.isVisible());
    }

    private void setupTable() {
        table.setShowTooltips(true);
        model = new ExploreTableModel();
        table.setModel(model);
        table.setDefaultRenderer(DeviceFile.class, new ExplorerCellRenderer());
        //table.getTableHeader().setDefaultRenderer(new TableHeaderRenderer());

        // default column sizes
        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(ExploreTableModel.Columns.SIZE.ordinal()).setPreferredWidth(80);

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

        // support drag and drop of files IN TO explorer window
        new DropTarget(table.getScrollPane(), new FileDragAndDropListener(table.getScrollPane(), this::handleFilesDropped));
    }

    private void handleFileClicked() {
        if (!device.isOnline) {
            errorMessage = "device offline";
            refreshUi();
            return;
        }
        List<DeviceFile> selectedFiles = getSelectedFiles();
        log.debug("handleFileClicked: SELECTED FILES: " + GsonHelper.toJson(selectedFiles));
        if (selectedFiles.isEmpty()) return;
        DeviceFile selectedFile = selectedFiles.get(0);
        if (selectedFile.isReadOnly && !useRoot) {
            errorMessage = "read-only";
            refreshUi();
            return;
        } else if (selectedFile.isDirectory || selectedFile.isSymbolicLink) {
            if (TextUtils.equalsIgnoreCase(selectedFile.name, "..")) {
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
                setPath(selectedPath + "/" + selectedFile.name);
            }
            errorMessage = null;
            refreshFiles();
        } else {
            handleDownload();
        }
    }

    private void setPath(String path) {
        // selectedPath should never end with "/"
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);

        prevPathList.add(path);
        if (prevPathList.size() > MAX_PATH_SAVE) {
            prevPathList.remove(0);
        }

        selectedPath = path;
    }

    private void refreshFiles() {
        if (!device.isOnline) return;
        DeviceManager.getInstance().listFiles(device, selectedPath, useRoot, (fileList, error) -> {
            SwingUtilities.invokeLater(() -> {
                if (error != null) {
                    errorMessage = error;
                    refreshUi();
                    if (useRoot && TextUtils.contains(error, "su")) {
                        JOptionPane.showMessageDialog(this, "ROOT not available!");
                        toggleRoot();
                    }
                    return;
                }
                if (fileList == null) {
                    log.debug("refreshFiles: NO FILES");
                    errorMessage = "permission denied - " + selectedPath;
                    // remove bad path
                    prevPathList.remove(prevPathList.size() - 1);
                    if (!prevPathList.isEmpty()) {
                        // reset good path
                        selectedPath = prevPathList.get(prevPathList.size() - 1);
                        if (selectedPath.endsWith("/")) selectedPath = selectedPath.substring(0, selectedPath.length() - 1);
                    } else {
                        selectedPath = "";
                    }
                    log.trace("refreshFiles: selectedPath={}", selectedPath);
                } else {
                    // clear out any previous set filter and error
                    filterTextField.reset();
                    errorMessage = null;
                    // TODO: add ".." to top of list
                    if (!TextUtils.isEmpty(selectedPath)) {
                        DeviceFile upFile = new DeviceFile();
                        upFile.name = "..";
                        upFile.isDirectory = true;
                        fileList.add(0, upFile);
                    }
                    model.setFileList(fileList);
                }
                refreshUi();
            });
        });
    }

    private void refreshUi() {
        // file path
        statusBar.setLeftLabel(selectedPath.isEmpty() ? "/" : selectedPath);

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

    @Override
    public void showPopupMenu(int row, int column, MouseEvent e) {

    }

    @Override
    public void showHeaderPopupMenu(int column, MouseEvent e) {

    }

    @Override
    public void handleTableDoubleClick(int row, int column, MouseEvent e) {
        handleFileClicked();
    }

    private String getDeviceField(DeviceFile deviceFile, ExploreTableModel.Columns column) {
        String val;
        switch (column) {
            case NAME:
                val = deviceFile.name;
                break;
            case SIZE:
                val = String.valueOf(deviceFile.size);
                break;
            case DATE:
                val = String.valueOf(deviceFile.dateMs);
                break;
            default:
                val = column.name();
                break;
        }
        if (TextUtils.isEmpty(val)) return "";
        else return val;
    }

    private void handleFilesDropped(List<File> fileList) {
        if (!device.isOnline) return;
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
                DeviceManager.getInstance().copyFile(device, file, selectedPath + "/", (isSuccess, error) -> {
                    if (isSuccess) refreshFiles();
                });
            }
        }
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
        Preferences preferences = Preferences.userRoot();
        useRoot = preferences.getBoolean(PREF_USE_ROOT, false);
        rootButton = createToolbarButton(toolbar, "root.png", "Root", "Root Mode", actionEvent -> {
            toggleRoot();
        });
        refreshRootButton();
    }

    private void toggleRoot() {
        Preferences preferences = Preferences.userRoot();
        useRoot = !useRoot;
        preferences.putBoolean(PREF_USE_ROOT, useRoot);
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

        DeviceManager.getInstance().createFolder(device, selectedPath + "/" + result, (isSuccess, error) -> {
            refreshFiles();
        });
    }

    private void handleDownload() {
        if (!device.isOnline) return;
        List<DeviceFile> selectedFileList = getSelectedFiles();
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

        Preferences preferences = Preferences.userRoot();
        String downloadFolder = preferences.get(ExploreScreen.PREF_DOWNLOAD_FOLDER, null);
        if (downloadFolder == null) {
            downloadFolder = System.getProperty("user.home") + "/Downloads";
        }
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
        if (!device.isOnline) return;
        List<DeviceFile> selectedFileList = getSelectedFiles();
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

        int rc = JOptionPane.showConfirmDialog(this,
                "Delete " + selectedFileList.size() + " files(s)?\n\n" + sb,
                "Delete Files?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (DeviceFile file : selectedFileList) {
            DeviceManager.getInstance().deleteFile(device, selectedPath, file, (isSuccess, error) -> {
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
        Object selectedObj = comboBox.getSelectedItem();
        if (selectedObj == null) return;
        String selectedItem = (String) selectedObj;
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
        List<DeviceFile> selectedFileList = getSelectedFiles();
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
        List<DeviceFile> selectedFileList = getSelectedFiles();
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

    private List<DeviceFile> getSelectedFiles() {
        List<DeviceFile> selectedDeviceList = new ArrayList<>();
        int[] selectedRows = table.getSelectedRows();
        for (int selectedRow : selectedRows) {
            // convert view row to data row (in case user changed sort order)
            int dataRow = table.convertRowIndexToModel(selectedRow);
            DeviceFile deviceFile = model.getDeviceFileAtRow(dataRow);
            if (deviceFile != null) selectedDeviceList.add(deviceFile);
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