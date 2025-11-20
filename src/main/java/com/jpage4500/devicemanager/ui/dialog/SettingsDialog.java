package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.ui.views.CheckBoxList;
import com.jpage4500.devicemanager.ui.views.DraggableCheckBoxList;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class SettingsDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);

    private final DeviceScreen deviceScreen;

    public static void showSettings(DeviceScreen deviceScreen) {
        SettingsDialog settingsScreen = new SettingsDialog(deviceScreen);
        DialogHelper.showCustomDialog(deviceScreen, settingsScreen, "Settings", new String[]{});
    }

    private SettingsDialog(DeviceScreen deviceScreen) {
        this.deviceScreen = deviceScreen;

        setLayout(new MigLayout("", "[][]"));
        initalizeUi();
    }

    private void initalizeUi() {
        JPanel devicePanel = UiUtils.createPanel("Device Settings");
        UiUtils.addSettingButton(devicePanel, "Refresh Time", "EDIT", () -> showRefreshTime());
        UiUtils.addSettingButton(devicePanel, "Manage Columns", "EDIT", () -> showManageDeviceColumnsDialog(deviceScreen, this));
        UiUtils.addSettingButton(devicePanel, "Custom Columns", "EDIT", this::showAppsSettings);
        UiUtils.addSettingButton(devicePanel, "Customize Toolbar", "EDIT", () -> showManageToolbar(deviceScreen, this));
        add(devicePanel, "growx, wrap");

        JPanel remotePanel = UiUtils.createPanel("Remote Servers");
        UiUtils.addSettingButton(remotePanel, "Connect to Remote Servers", "MANAGE", () -> RemoteServerDialog.showRemoteServerDialog(this));
        UiUtils.addSettingButton(remotePanel, "Share My Devices", "SHARE", () -> ShareServerDialog.showShareServerDialog(this));
        add(remotePanel, "growx, wrap");

        JPanel logPanel = UiUtils.createPanel("Log Settings");
        UiUtils.addSettingButton(logPanel, "Buffer (lines)", "EDIT", () -> showLogBuffer());
        add(logPanel, "growx, wrap");

        JPanel explorePanel = UiUtils.createPanel("File Explorer Settings");
        UiUtils.addSettingButton(explorePanel, "Download Location", "EDIT", this::showDownloadLocation);
        add(explorePanel, "growx, wrap");

        JPanel generalPanel = UiUtils.createPanel("General Settings");
        UiUtils.addSettingCheckbox(generalPanel, "Minimize to System Tray", PreferenceUtils.PrefBoolean.PREF_EXIT_TO_TRAY, false, null);
        UiUtils.addSettingCheckbox(generalPanel, "Check for updates", PreferenceUtils.PrefBoolean.PREF_CHECK_UPDATES, true, isChecked -> deviceScreen.scheduleUpdateChecks());
        UiUtils.addSettingCheckbox(generalPanel, "Show background image", PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true, isChecked -> {
            // force table background to be repainted
            deviceScreen.model.fireTableDataChanged();
        });

        JButton logButton = UiUtils.addSettingButton(generalPanel, "Log Level", "EDIT", null);
        UiUtils.addLeftClickListener(logButton, e -> toggleLogLevels(logButton));
        updateLogLevel(logButton);

        UiUtils.addSettingButton(generalPanel, "View Logs", "VIEW", this::viewLogs);
        UiUtils.addSettingButton(generalPanel, "Reset Preferences", "RESET", this::resetPreferences);
        add(generalPanel, "growx, wrap");

        doLayout();
        invalidate();
    }

    private void showLogBuffer() {
        int maxLines = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LOGS_MAX_LINES, LogsTableModel.DEFAULT_BUFFER);
        String msg = String.format("Enter # of lines (%d - %d)", LogsTableModel.MIN_BUFFER, LogsTableModel.MAX_BUFFER);
        String result = DialogHelper.showInputDialog(this, "Log Buffer", msg, String.valueOf(maxLines));
        if (TextUtils.isEmpty(result)) return;

        int newValue = TextUtils.getNumber(result, LogsTableModel.DEFAULT_BUFFER);
        if (newValue > LogsTableModel.MAX_BUFFER) newValue = LogsTableModel.MAX_BUFFER;
        else if (newValue < LogsTableModel.MIN_BUFFER) newValue = LogsTableModel.MIN_BUFFER;
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LOGS_MAX_LINES, newValue);
    }

    private void updateLogLevel(JButton logButton) {
        int logLevel = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LOG_LEVEL, Log.INFO);
        String name;
        switch (logLevel) {
            case Log.INFO:
                name = "Info";
                break;
            case Log.DEBUG:
                name = "Debug";
                break;
            case Log.VERBOSE:
            default:
                name = "Trace";
                break;
        }
        logButton.setText(name);
    }

    private void toggleLogLevels(JButton logButton) {
        int logLevel = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LOG_LEVEL, Log.INFO);
        switch (logLevel) {
            case Log.INFO:
                logLevel = Log.DEBUG;
                break;
            case Log.DEBUG:
                logLevel = Log.VERBOSE;
                break;
            case Log.VERBOSE:
            default:
                logLevel = Log.INFO;
                break;
        }
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LOG_LEVEL, logLevel);

        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        logger.setFileLogLevel(logLevel);

        updateLogLevel(logButton);
    }

    private void resetPreferences() {
        if (!DialogHelper.showConfirmDialog(this, "Reset Preferences", "Reset All Preferences?")) return;

        log.debug("resetPreferences: ");
        PreferenceUtils.resetAll();

        removeAll();
        // update UI to show updated states
        initalizeUi();
        // force table to be re-created and show columns in order
        deviceScreen.setupTable();
        List<Device> deviceList = DeviceManager.getInstance().getDevices();
        deviceScreen.handleDevicesUpdated(deviceList);
    }

    private void viewLogs() {
        // show logs
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        File logsFile = logger.getFileLog();
        boolean rc = Utils.editFile(logsFile);
        if (!rc) {
            // open failed
            DialogHelper.showDialog(this, "Error", "Failed to open logs: " + logsFile.getAbsolutePath());
        }
    }

    public static List<String> getHiddenColumnList() {
        String hiddenColsStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS);
        return GsonHelper.stringToList(hiddenColsStr, String.class);
    }

    public void showRefreshTime() {
        int refreshTimeMins = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_REFRESH_TIME_MINS, DeviceManager.DEVICE_REFRESH_MINS);
        String result = DialogHelper.showInputDialog(this, "Refresh Time", "Enter Refresh Time (in mins, between 5 and 600)", String.valueOf(refreshTimeMins));
        if (TextUtils.isEmpty(result)) return;

        int newValue = TextUtils.getNumber(result, DeviceManager.DEVICE_REFRESH_MINS);
        if (newValue > 600) newValue = 600;
        else if (newValue < 5) newValue = 5;
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_REFRESH_TIME_MINS, newValue);
        DeviceManager.getInstance().updateRefreshTime();
    }

    public static void showManageDeviceColumnsDialog(DeviceScreen deviceScreen, Component component) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(new JLabel("Select columns to SHOW"), "span");

        CheckBoxList checkBoxList = new CheckBoxList();
        populateHiddelColumns(checkBoxList);
        JScrollPane scroll = new JScrollPane(checkBoxList);
        panel.add(scroll, "grow, span, wrap");

        HoverLabel resetLabel = new HoverLabel("Reset to defaults", UiUtils.getImageIcon("icon_trash.png", UiUtils.IMG_SIZE_SMALL));
        resetLabel.addActionListener(actionEvent -> {
            if (!DialogHelper.showConfirmDialog(component, "Reset Table?", "Reset Table to defaults?")) return;
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS, null);

            Preferences prefs = Preferences.userRoot();
            log.debug("showManageDeviceColumnsDialog: reset table");
            prefs.remove(DeviceScreen.PREF_KEY_DEVICES + "-details");
            populateHiddelColumns(checkBoxList);
            checkBoxList.invalidate();
            // force table to be re-created and show columns in order
            deviceScreen.setupTable();
            List<Device> deviceList = DeviceManager.getInstance().getDevices();
            deviceScreen.handleDevicesUpdated(deviceList);
        });
        panel.add(resetLabel, "newline 20px, al right, span, wrap");

        if (!DialogHelper.showCustomDialog(component, panel, "Manage Columns", null)) return;

        // save columns that are NOT selected
        List<String> selectedItems = checkBoxList.getUnSelectedItems();
        log.debug("HIDDEN: {}", GsonHelper.toJson(selectedItems));
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS, GsonHelper.toJson(selectedItems));
        deviceScreen.restoreTable();
    }

    private static void populateHiddelColumns(CheckBoxList checkBoxList) {
        checkBoxList.removeAll();
        List<String> hiddenColList = getHiddenColumnList();
        DeviceTableModel.Columns[] columnsArr = DeviceTableModel.Columns.values();
        for (DeviceTableModel.Columns column : columnsArr) {
            String colName = column.name();
            boolean isHidden = hiddenColList.contains(colName);
            checkBoxList.addItem(colName, !isHidden);
        }
    }

    public static List<String> getHiddenToolbarList() {
        String hiddenStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS);
        return GsonHelper.stringToList(hiddenStr, String.class);
    }

    public static void addHiddenToolbarItem(String item) {
        List<String> hiddenToolbarList = SettingsDialog.getHiddenToolbarList();
        if (!hiddenToolbarList.contains(item)) hiddenToolbarList.add(item);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, GsonHelper.toJson(hiddenToolbarList));
    }

    public static List<String> getToolbarOrder() {
        String orderStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_TOOLBAR_ORDER);
        List<String> orderList = GsonHelper.stringToList(orderStr, String.class);
        if (orderList == null) orderList = new ArrayList<>();
        return orderList;
    }

    public static void showManageToolbar(DeviceScreen deviceScreen, Component component) {
        List<String> hiddenColList = getHiddenToolbarList();
        List<String> orderedList = getToolbarOrder();
        
        DraggableCheckBoxList checkBoxList = new DraggableCheckBoxList();
        
        // build ordered array of toolbar buttons
        DeviceScreen.ToolbarButton[] allButtons = DeviceScreen.ToolbarButton.values();
        List<DeviceScreen.ToolbarButton> orderedButtons = new ArrayList<>();
        
        // first add buttons in saved order
        for (String label : orderedList) {
            DeviceScreen.ToolbarButton button = DeviceScreen.ToolbarButton.buttonFromLabel(label);
            if (button != null && button != DeviceScreen.ToolbarButton.SETTINGS) {
                orderedButtons.add(button);
            }
        }
        
        // then add any new buttons not in saved order
        for (DeviceScreen.ToolbarButton button : allButtons) {
            if (button == DeviceScreen.ToolbarButton.SETTINGS) continue;
            if (!orderedButtons.contains(button)) {
                orderedButtons.add(button);
            }
        }
        
        // add items to list with icons
        for (DeviceScreen.ToolbarButton button : orderedButtons) {
            boolean isHidden = hiddenColList.contains(button.label);
            ImageIcon icon = null;
            if (button.image != null) {
                icon = UiUtils.getImageIcon(button.image, 32);
            }
            checkBoxList.addItem(button.label, !isHidden, icon);
        }

        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(new JLabel("<html>Select buttons to SHOW<br>Drag to reorder</html>"), "span");

        JScrollPane scroll = new JScrollPane(checkBoxList);
        panel.add(scroll, "grow, span, wrap");

        // add Restore Default button
        JButton defaultButton = new JButton("Restore Default");
        defaultButton.addActionListener(e -> {
            // reset to default order and visibility
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_TOOLBAR_ORDER, null);
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, null);
            deviceScreen.setupToolbar();
            UiUtils.closeWindow(panel);
        });
        panel.add(defaultButton, "span, align right, wrap");
        
        // add OK/Cancel buttons at bottom
        JPanel buttonPanel = new JPanel(new MigLayout("fillx", "push[][]"));
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            UiUtils.closeWindow(panel);
        });
        buttonPanel.add(cancelButton, "");
        
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            // save order
            List<String> orderedItems = checkBoxList.getAllItems();
            log.debug("ORDER: {}", GsonHelper.toJson(orderedItems));
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_TOOLBAR_ORDER, GsonHelper.toJson(orderedItems));
            
            // save hidden items
            List<String> hiddenItems = checkBoxList.getUnSelectedItems();
            log.debug("HIDDEN: {}", GsonHelper.toJson(hiddenItems));
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, GsonHelper.toJson(hiddenItems));
            
            deviceScreen.setupToolbar();
            
            UiUtils.closeWindow(panel);
        });
        buttonPanel.add(okButton, "");
        
        panel.add(buttonPanel, "span, align right");

        DialogHelper.showCustomDialog(component, panel, "Toolbar Buttons", new String[]{});
    }

    private void showAppsSettings() {
        String msg = """
            <html>
            <b>Format: "LABEL:TYPE:VALUE"</b>
            <ul>
            <li>LABEL is the column header</li>
            <li>TYPE describes the VALUE. one of: [VER|PROP|QUERY]</li>
            <li>VALUE depends on the TYPE:
                <ul>
                <li>VER: package name (org.telegram.messenger.web)</li>
                <li>PROP: property (my.cust.prop)</li>
                <li>QUERY: content:// URI which returns a single row/value</li>
                </ul>
            </li>
            <li>Each line is a column</li>
            </ul>
            Examples:
            <ul>
            <li>Telegram Version:VER:org.telegram.messenger.web</li>
            <li>My Property:PROP:my.cust.prop</li>
            <li>Content Value:QUERY:content://com.test.provider/queryForValue</li>
            </ul>
            </html>
            """;
        List<String> appList = getCustomColumns();
        List<String> resultList = showMultilineEditDialog("Custom Columns", msg, appList);
        if (resultList == null) return;

        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_CUSTOM_APPS, GsonHelper.toJson(resultList));
        deviceScreen.setCustomColumns();
        DeviceManager.getInstance().refreshDevices();
    }

    /**
     * get list of custom columns
     */
    public static List<String> getCustomColumns() {
        String appPrefs = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_CUSTOM_APPS);
        return GsonHelper.stringToList(appPrefs, String.class);
    }

    private List<String> showMultilineEditDialog(String title, String message, List<String> stringList) {
        StringBuilder sb = new StringBuilder();
        for (String app : stringList) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(app);
        }

        JPanel panel = new JPanel(new MigLayout());
        panel.add(new JLabel(message), "span");

        JTextArea inputField = new JTextArea(5, 0);
        inputField.setText(sb.toString());
        JScrollPane scroll = new JScrollPane(inputField);
        panel.add(scroll, "grow, span, wrap");

        if (!DialogHelper.showCustomDialog(this, panel, title, null)) return null;

        String results = inputField.getText();
        log.debug("showEditField: results: {}", results);
        String[] resultArr = results.split("\n");
        List<String> resultList = new ArrayList<>();
        for (String result : resultArr) {
            if (result.trim().length() == 0) continue;
            resultList.add(result);
        }
        return resultList;
    }

    private String showSingleLineEditDialog(String title, String message, String value) {
        JPanel panel = new JPanel(new MigLayout());
        panel.add(new JLabel(message), "span");

        JTextArea inputField = new JTextArea(5, 0);
        inputField.setText(value);
        JScrollPane scroll = new JScrollPane(inputField);
        panel.add(scroll, "grow, span, wrap");

        int rc = JOptionPane.showOptionDialog(deviceScreen, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return null;

        String results = inputField.getText().trim();
        log.debug("showEditField: results: {}", results);
        return results;
    }

    private void showDownloadLocation() {
        String downloadFolder = Utils.getDownloadFolder();

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File(downloadFolder));
        chooser.setDialogTitle("Select Folder");
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setApproveButtonText("OK");
        chooser.setAcceptAllFileFilterUsed(false);

        int rc = chooser.showOpenDialog(this);
        if (rc == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile != null && selectedFile.exists() && selectedFile.isDirectory()) {
                PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_DOWNLOAD_FOLDER, selectedFile.getAbsolutePath());
            }
        }

//        String result = showSingleLineEditDialog("Download Folder", "Enter Download Folder", downloadFolder);
//        if (result != null) {
//            preferences.put(ExploreView.PREF_DOWNLOAD_FOLDER, result);
//        }
    }

}
