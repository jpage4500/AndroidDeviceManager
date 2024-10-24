package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.ui.views.CheckBoxList;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import com.jpage4500.devicemanager.utils.Utils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class SettingsDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);

    private DeviceScreen deviceScreen;

    public static int showSettings(DeviceScreen deviceScreen) {
        SettingsDialog settingsScreen = new SettingsDialog(deviceScreen);
        return JOptionPane.showOptionDialog(deviceScreen, settingsScreen, "Settings", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
    }

    private SettingsDialog(DeviceScreen deviceScreen) {
        this.deviceScreen = deviceScreen;

        setLayout(new MigLayout("", "[][]"));
        initalizeUi();
    }

    private void initalizeUi() {
        addButton("Manage Columns", "EDIT", () -> showManageDeviceColumnsDialog(deviceScreen));
        addButton("Custom Apps", "EDIT", this::showAppsSettings);
        addButton("Customize Toolbar", "EDIT", this::showToolbarOptions);
        addButton("Download Location", "EDIT", this::showDownloadLocation);

        addCheckbox("Minimize to System Tray", PreferenceUtils.PrefBoolean.PREF_EXIT_TO_TRAY, false, null);
        addCheckbox("Check for updates", PreferenceUtils.PrefBoolean.PREF_CHECK_UPDATES, true, isChecked -> deviceScreen.scheduleUpdateChecks());
        addCheckbox("Show background image", PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true, isChecked -> {
            // force table background to be repainted
            deviceScreen.model.fireTableDataChanged();
        });
        addCheckbox("Debug Mode", PreferenceUtils.PrefBoolean.PREF_DEBUG_MODE, false, isChecked -> {
            AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
            logger.setFileLogLevel(isChecked ? Log.DEBUG : Log.INFO);
        });

        addButton("View Logs", "VIEW", this::viewLogs);
        addButton("Reset Preferences", "RESET", this::resetPreferences);

        doLayout();
        invalidate();
    }

    public interface ButtonListener {
        void onClicked();
    }

    private void addButton(String label, String action, ButtonListener listener) {
        add(new JLabel(label));
        JButton button = new JButton(action);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                listener.onClicked();
            }
        });
        add(button, "wrap");
    }

    public interface CheckBoxListener {
        void onChecked(boolean isChecked);
    }

    private void addCheckbox(String label, PreferenceUtils.PrefBoolean pref, boolean defaultValue, CheckBoxListener listener) {
        JLabel textLabel = new JLabel(label);
        add(textLabel);

        JCheckBox checkbox = new JCheckBox();
        boolean currentChecked = PreferenceUtils.getPreference(pref, defaultValue);
        checkbox.setSelected(currentChecked);
        checkbox.setHorizontalTextPosition(SwingConstants.LEFT);
        add(checkbox, "align center, wrap");

        checkbox.addActionListener(actionEvent -> {
            boolean selected = checkbox.isSelected();
            PreferenceUtils.setPreference(pref, selected);
            if (listener != null) listener.onChecked(selected);
        });

        textLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                // TODO: fire checkbox action listener directly
                boolean selected = !checkbox.isSelected();
                checkbox.setSelected(selected);
                PreferenceUtils.setPreference(pref, selected);
                if (listener != null) listener.onChecked(selected);
            }
        });

    }

    private void resetPreferences() {
        int rc = JOptionPane.showConfirmDialog(this, "Reset All Preferences?", "Reset Preferences", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

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
            JOptionPane.showConfirmDialog(deviceScreen, "Failed to open logs: " + logsFile.getAbsolutePath(), "Error", JOptionPane.DEFAULT_OPTION);
        }
    }

    public static List<String> getHiddenColumnList() {
        String hiddenColsStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_HIDDEN_COLUMNS);
        return GsonHelper.stringToList(hiddenColsStr, String.class);
    }

    public static void showManageDeviceColumnsDialog(DeviceScreen deviceScreen) {
        JPanel panel = new JPanel(new MigLayout());
        panel.add(new JLabel("Select columns to SHOW"), "span");

        CheckBoxList checkBoxList = new CheckBoxList();
        populateHiddelColumns(checkBoxList);
        JScrollPane scroll = new JScrollPane(checkBoxList);
        panel.add(scroll, "grow, span, wrap");

        HoverLabel resetLabel = new HoverLabel("Reset to defaults", UiUtils.getImageIcon("icon_trash.png", UiUtils.IMG_SIZE_SMALL));
        resetLabel.addActionListener(actionEvent -> {
            int rc = JOptionPane.showConfirmDialog(deviceScreen, "Reset Table to defaults?", "Reset Table?", JOptionPane.YES_NO_OPTION);
            if (rc != JOptionPane.YES_OPTION) return;
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

        int rc = JOptionPane.showOptionDialog(deviceScreen, panel, "Manage Columns", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return;

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

    private void showToolbarOptions() {
        List<String> hiddenColList = getHiddenToolbarList();
        CheckBoxList checkBoxList = new CheckBoxList();
        DeviceScreen.ToolbarButton[] arr = DeviceScreen.ToolbarButton.values();
        for (DeviceScreen.ToolbarButton val : arr) {
            boolean isHidden = hiddenColList.contains(val.label);
            checkBoxList.addItem(val.label, !isHidden);
        }

        JPanel panel = new JPanel(new MigLayout());
        panel.add(new JLabel("Select buttons to SHOW"), "span");

        JScrollPane scroll = new JScrollPane(checkBoxList);
        panel.add(scroll, "grow, span, wrap");

        int rc = JOptionPane.showOptionDialog(deviceScreen, panel, "Toolbar Buttons", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return;

        // save columns that are NOT selected
        List<String> selectedItems = checkBoxList.getUnSelectedItems();
        log.debug("HIDDEN: {}", GsonHelper.toJson(selectedItems));
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_HIDDEN_TOOLBAR_ITEMS, GsonHelper.toJson(selectedItems));
        deviceScreen.setupToolbar();
    }

    private void showAppsSettings() {
        List<String> appList = getCustomApps();
        List<String> resultList = showMultilineEditDialog("Custom Apps", "Enter package name(s) to track - 1 per line", appList);
        if (resultList == null) return;

        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_CUSTOM_APPS, GsonHelper.toJson(resultList));
        deviceScreen.model.setAppList(resultList);
    }

    /**
     * get list of custom monitored apps
     */
    public static List<String> getCustomApps() {
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

        int rc = JOptionPane.showOptionDialog(deviceScreen, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return null;

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
