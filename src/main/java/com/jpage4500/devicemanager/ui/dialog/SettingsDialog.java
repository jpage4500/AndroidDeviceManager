package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.DeviceStatsManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.ui.App;
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

    private final App app;

    public static void showSettings(App app, Component parent) {
        SettingsDialog settingsScreen = new SettingsDialog(app);
        DialogHelper.showCustomDialog(parent, settingsScreen, "Settings", new String[]{});
    }

    private SettingsDialog(App app) {
        this.app = app;

        setLayout(new MigLayout("", "[][]"));
        initalizeUi();
    }

    private void initalizeUi() {
        JPanel devicePanel = UiUtils.createPanel("Device Settings");
        UiUtils.addSettingButton(devicePanel, "Refresh Time", "EDIT", () -> showRefreshTime());
        UiUtils.addSettingButton(devicePanel, "Stats History (days)", "EDIT", () -> showStatsRetention());
        UiUtils.addSettingButton(devicePanel, "Manage Columns", "EDIT", () -> showManageDeviceColumnsDialog(app, this));
        UiUtils.addSettingButton(devicePanel, "Custom Columns", "EDIT", this::showAppsSettings);
        UiUtils.addSettingButton(devicePanel, "Customize Toolbar", "EDIT", () -> showManageToolbar(app, this));
        UiUtils.addSettingButton(devicePanel, "scrcpy Settings", "SHOW", this::showScrcpyOptionsDialog);
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
        UiUtils.addSettingCheckbox(generalPanel, "Check for updates", PreferenceUtils.PrefBoolean.PREF_CHECK_UPDATES, true, isChecked -> app.scheduleUpdateChecks());
        UiUtils.addSettingCheckbox(generalPanel, "Show background image", PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true, isChecked -> app.refreshDeviceListView());

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
        app.rebuildDeviceTable();
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

    /**
     * @return enum names of columns that should be hidden. When the user has never customized
     * columns (pref is null/empty), falls back to {@link DeviceTableModel.Columns#hideByDefault()}.
     */
    public static List<String> getHiddenColumnList() {
        String hiddenColsStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS);
        if (TextUtils.isEmpty(hiddenColsStr)) {
            // never customized — apply defaults
            List<String> defaults = new ArrayList<>();
            for (DeviceTableModel.Columns column : DeviceTableModel.Columns.values()) {
                if (column.hideByDefault()) defaults.add(column.name());
            }
            return defaults;
        }
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

    /**
     * how many days of device stats are kept for the stats chart
     * <p>
     * NOTE: this is also the sample rate's only real knob - stats are recorded on the device refresh
     * above, so a longer refresh time means fewer points across the same number of days
     */
    public void showStatsRetention() {
        int retentionDays = DeviceStatsManager.getRetentionDays();
        String result = DialogHelper.showInputDialog(this, "Stats History",
            "Number of days of device stats to keep (between " + DeviceStatsManager.MIN_RETENTION_DAYS
                + " and " + DeviceStatsManager.MAX_RETENTION_DAYS + ")", String.valueOf(retentionDays));
        if (TextUtils.isEmpty(result)) return;

        int newValue = TextUtils.getNumber(result, DeviceStatsManager.DEFAULT_RETENTION_DAYS);
        if (newValue > DeviceStatsManager.MAX_RETENTION_DAYS) newValue = DeviceStatsManager.MAX_RETENTION_DAYS;
        else if (newValue < DeviceStatsManager.MIN_RETENTION_DAYS) newValue = DeviceStatsManager.MIN_RETENTION_DAYS;
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_STATS_RETENTION_DAYS, newValue);
    }

    public static void showManageDeviceColumnsDialog(App app, Component component) {
        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(new JLabel("Select columns to SHOW"), "span");

        CheckBoxList checkBoxList = new CheckBoxList();
        populateHiddelColumns(checkBoxList);
        JScrollPane scroll = new JScrollPane(checkBoxList);
        panel.add(scroll, "grow, span, wrap");

        HoverLabel resetLabel = new HoverLabel("Reset to defaults", UiUtils.getImageIcon(Icons.TRASH, UiUtils.IMG_SIZE_SMALL));
        resetLabel.addActionListener(actionEvent -> {
            if (!DialogHelper.showConfirmDialog(component, "Reset Table?", "Reset Table to defaults?")) return;
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS, null);

            Preferences prefs = Preferences.userRoot();
            log.debug("showManageDeviceColumnsDialog: reset table");
            prefs.remove(DeviceScreen.PREF_KEY_DEVICES + "-details");
            populateHiddelColumns(checkBoxList);
            checkBoxList.invalidate();
            // force table to be re-created and show columns in order
            app.rebuildDeviceTable();
        });
        panel.add(resetLabel, "newline 20px, al right, span, wrap");

        if (DialogHelper.showCustomDialog(component, panel, "Manage Columns", null) != JOptionPane.YES_OPTION) return;

        // save columns that are NOT selected
        List<String> selectedItems = checkBoxList.getUnSelectedItems();
        log.debug("HIDDEN: {}", GsonHelper.toJson(selectedItems));
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS, GsonHelper.toJson(selectedItems));
        app.restoreDeviceTable();
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

    /**
     * @return enum names of toolbar buttons that should be hidden. When the user has never
     * customized the toolbar (pref is null/empty), falls back to
     * {@link DeviceScreen.ToolbarButton#hideByDefault()}.
     */
    public static List<String> getHiddenToolbarList() {
        String hiddenStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS);
        if (TextUtils.isEmpty(hiddenStr)) {
            // never customized — apply defaults
            List<String> defaults = new ArrayList<>();
            for (DeviceScreen.ToolbarButton button : DeviceScreen.ToolbarButton.values()) {
                if (button.hideByDefault()) defaults.add(button.name());
            }
            return defaults;
        }
        return GsonHelper.stringToList(hiddenStr, String.class);
    }

    /** @param enumName ToolbarButton enum name (e.g. {@code ToolbarButton.SAVE_LOGS.name()}) */
    public static void addHiddenToolbarItem(String enumName) {
        List<String> hiddenToolbarList = SettingsDialog.getHiddenToolbarList();
        if (!hiddenToolbarList.contains(enumName)) hiddenToolbarList.add(enumName);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, GsonHelper.toJson(hiddenToolbarList));
    }

    public static void showManageToolbar(App app, Component component) {
        List<String> hiddenList = getHiddenToolbarList();

        DraggableCheckBoxList checkBoxList = new DraggableCheckBoxList();
        // TODO: support re-ordering
        checkBoxList.setDragEnabled(false);

        // build ordered array of toolbar buttons with icons
        for (DeviceScreen.ToolbarButton button : DeviceScreen.ToolbarButton.values()) {
            // prevent some buttons from being hidden
            switch (button) {
                case SETTINGS:
                    continue;
            }
            boolean isHidden = hiddenList.contains(button.name());
            ImageIcon icon = button.image != null ? UiUtils.getImageIcon(button.image, 32) : null;
            checkBoxList.addItem(button.label, !isHidden, icon);
        }

        JPanel panel = new JPanel(new MigLayout("fillx"));
        panel.add(new JLabel("☑ Check items to SHOW"), "span, wrap");
        panel.add(new JLabel("☐ Uncheck items to HIDE"), "span, wrap 20px");

        JScrollPane scroll = new JScrollPane(checkBoxList);
        panel.add(scroll, "grow, span, wrap");

        // Restore Default link
        HoverLabel defaultButton = new HoverLabel("Reset to defaults", UiUtils.getImageIcon(Icons.TRASH, UiUtils.IMG_SIZE_SMALL));
        defaultButton.addActionListener(e -> {
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, null);
            app.rebuildDeviceToolbar();
            UiUtils.closeWindow(panel);
        });
        panel.add(defaultButton, "span, align right, wrap");

        // OK / Cancel
        JPanel buttonPanel = new JPanel(new MigLayout("fillx", "push[][]"));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> UiUtils.closeWindow(panel));
        buttonPanel.add(cancelButton, "");

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            // checkbox list returns labels — convert to enum names
            List<String> hiddenLabels = checkBoxList.getUnSelectedItems();
            List<String> hiddenNames = new ArrayList<>();
            for (String label : hiddenLabels) {
                DeviceScreen.ToolbarButton button = DeviceScreen.ToolbarButton.buttonFromLabel(label);
                if (button != null) hiddenNames.add(button.name());
            }
            log.debug("HIDDEN: {}", GsonHelper.toJson(hiddenNames));
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, GsonHelper.toJson(hiddenNames));
            app.rebuildDeviceToolbar();
            UiUtils.closeWindow(panel);
        });
        buttonPanel.add(okButton, "");

        panel.add(buttonPanel, "span, align right");

        DialogHelper.showCustomDialog(component, panel, "Toolbar Buttons", new String[]{});
    }

    private void showScrcpyOptionsDialog() {
        ScrcpyOptionsDialog.showRemoteServerDialog(this);
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
        app.notifyCustomColumnsChanged();
    }

    /**
     * get list of custom columns
     */
    public static List<String> getCustomColumns() {
        String appPrefs = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_CUSTOM_APPS);
        return GsonHelper.stringToList(appPrefs, String.class);
    }

    /**
     * the column names out of {@link #getCustomColumns} entries ("PM:VER:com.test.pm" -> "PM"),
     * skipping blanks and commented-out lines
     */
    public static List<String> getCustomColumnLabels() {
        List<String> labelList = new ArrayList<>();
        for (String entry : getCustomColumns()) {
            if (TextUtils.isEmpty(entry) || TextUtils.startsWithAny(entry, false, "#", "//")) continue;
            String[] entryArr = entry.split(":");
            labelList.add(entryArr.length >= 1 ? entryArr[0].trim() : entry);
        }
        return labelList;
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

        if (DialogHelper.showCustomDialog(this, panel, title, null) != JOptionPane.YES_OPTION) return null;

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

        int rc = JOptionPane.showOptionDialog(this, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
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
