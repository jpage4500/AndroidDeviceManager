package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.SizeData;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.*;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.IconTableCellRenderer;
import com.jpage4500.devicemanager.utils.MyDragDropListener;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.viewmodel.ExploreTableModel;

import net.coobird.thumbnailator.Thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DropTarget;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * create and manage device view
 */
public class ExploreView {
    private static final Logger log = LoggerFactory.getLogger(ExploreView.class);

    public static final String PREF_DOWNLOAD_FOLDER = "PREF_DOWNLOAD_FOLDER";
    public static final String PREF_GO_TO_FOLDER_LIST = "PREF_GO_TO_FOLDER_LIST";
    private static final String HINT_FILTER_DEVICES = "Filter files...";
    public static final int MAX_PATH_SAVE = 10;

    private JFrame deviceFrame;

    public CustomTable table;
    public ExploreTableModel model;

    public CustomFrame frame;
    public JPanel panel;
    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;

    public int selectedColumn = -1;

    private Device selectedDevice;
    private String selectedPath = "/sdcard";
    private List<String> prevPathList = new ArrayList<>();
    private String errorMessage;

    public ExploreView(JFrame deviceFrame) {
        this.deviceFrame = deviceFrame;
        initalizeUi();
    }

    public void setDevice(Device selectedDevice) {
        this.selectedDevice = selectedDevice;
        frame.setTitle("Browse " + selectedDevice.getDisplayName());
        refreshFiles();
        show();
    }

    public void show() {
        frame.setVisible(true);
    }

    private void initalizeUi() {
        frame = new CustomFrame("browse");
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                refreshFiles();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
            }
        });
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                table.persist();
            }
        });
        table = new CustomTable("browse");
        model = new ExploreTableModel();
        table.setModel(model);
        table.setDefaultRenderer(DeviceFile.class, new IconTableCellRenderer());

        // right-align size column
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        table.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);

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

        // -- CMD + DELETE = delete selected files --
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.META_MASK), "delete");
        actionMap.put("delete", new AbstractAction() {
            public void actionPerformed(ActionEvent evt) {
                handleDelete();
            }
        });

        // -- CMD + G = open folder --
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.META_MASK), "goto");
        actionMap.put("goto", new AbstractAction() {
            public void actionPerformed(ActionEvent evt) {
                handleGoToFolder();
            }
        });

        refreshFiles();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBackground(Color.RED);
        panel.add(scrollPane, BorderLayout.CENTER);

        // setup toolbar
        setupToolbar();
        panel.add(toolbar, BorderLayout.NORTH);

        // statusbar
        statusBar = new StatusBar();
        statusBar.setLeftLabelListener(this::handleGoToFolder);
        panel.add(statusBar, BorderLayout.SOUTH);

        frame.setContentPane(panel);
        frame.setVisible(true);

        JRootPane rootPane = SwingUtilities.getRootPane(table);
        emptyView = new EmptyView("No Files");
        rootPane.setGlassPane(emptyView);
        emptyView.setOpaque(false);
        emptyView.setVisible(true);

        // support drag and drop of files
        MyDragDropListener dragDropListener = new MyDragDropListener(table, false, this::handleFilesDropped);
        new DropTarget(table, dragDropListener);

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
                    DeviceFile deviceFile = model.getDeviceFileAtRow(row);
                    log.trace("mouseClicked: DOUBLE-CLICK: " + GsonHelper.toJson(deviceFile));
                    if (deviceFile.isDir || deviceFile.isLink) {
                        if (TextUtils.equalsIgnoreCase(deviceFile.name, "..")) {
                            log.debug("mouseClicked: UP: {}", selectedPath);
                            int pos = selectedPath.lastIndexOf('/');
                            if (pos > 0) {
                                setPath(selectedPath.substring(0, pos));
                            } else if (pos == 0) {
                                // root folder
                                setPath("");
                            }
                        } else {
                            // append selected folder to current path
                            setPath(selectedPath + "/" + deviceFile.name);
                        }
                        refreshFiles();
                    } else {
                        handleDownload();
                    }
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

    private void setPath(String path) {
        prevPathList.add(path);
        if (prevPathList.size() > MAX_PATH_SAVE) {
            prevPathList.remove(0);
        }

        selectedPath = path;
    }

    private void refreshFiles() {
        if (selectedDevice == null) return;
        DeviceManager.getInstance().listFiles(selectedDevice, selectedPath, fileList -> {
            if (fileList == null) {
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
                model.setFileList(fileList);
            }
            refreshUi();
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
                val = String.valueOf(deviceFile.date);
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
                DeviceManager.getInstance().installApp(selectedDevice, file, null);
            } else {
                DeviceManager.getInstance().copyFile(selectedDevice, file, selectedPath + "/", null);
            }
        }
    }

    private void setupToolbar() {
        if (toolbar == null) {
            toolbar = new JToolBar("Applications");
            toolbar.setRollover(true);
        } else {
            toolbar.removeAll();
        }

        createButton(toolbar, "icon_open_folder.png", "Go To..", "Open Folder", actionEvent -> handleGoToFolder());
        createButton(toolbar, "icon_download.png", "Download", "Download Files", actionEvent -> handleDownload());
        toolbar.addSeparator();
        createButton(toolbar, "icon_delete.png", "Delete", "Delete Files", actionEvent -> handleDelete());

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

    private void handleDownload() {
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
        int rc = JOptionPane.showConfirmDialog(frame,
                "Download " + msg + "?",
                "Download?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        Preferences preferences = Preferences.userRoot();
        String downloadFolder = preferences.get(ExploreView.PREF_DOWNLOAD_FOLDER, "~/Downloads");
        for (DeviceFile file : selectedFileList) {
            DeviceManager.getInstance().downloadFile(selectedDevice, selectedPath, file.name, downloadFolder, isSuccess -> {
                if (isSuccess && isSingleFile) {
                    File downloadedFile = new File(downloadFolder + "/" + file.name);
                    if (downloadedFile.exists()) {
                        int openRc = JOptionPane.showConfirmDialog(frame,
                                "Open " + downloadedFile.getName() + "?",
                                "Open File?", JOptionPane.YES_NO_OPTION);
                        if (openRc != JOptionPane.YES_OPTION) return;
                        try {
                            Desktop.getDesktop().open(downloadedFile);
                        } catch (IOException e) {
                            log.error("handleDownload " + e.getMessage());
                        }
                    }
                }
            });
        }
    }

    private void handleDelete() {
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

        int rc = JOptionPane.showConfirmDialog(frame,
                "Delete " + selectedFileList.size() + " files(s)?\n\n" + sb,
                "Delete Files?", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        for (DeviceFile file : selectedFileList) {
            DeviceManager.getInstance().deleteFile(selectedDevice, selectedPath, file.name, isSuccess -> {
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
        int rc = JOptionPane.showOptionDialog(frame, comboBox, "Go to folder", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
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
        JOptionPane.showConfirmDialog(frame, "Select 1 or more files to use this feature", "No files selected", JOptionPane.DEFAULT_OPTION);
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
