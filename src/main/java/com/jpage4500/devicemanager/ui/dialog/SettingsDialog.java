package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.ui.ExploreScreen;
import com.jpage4500.devicemanager.ui.views.CheckBoxList;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.Utils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class SettingsDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);
    public static final String PREF_CUSTOM_APPS = "PREF_CUSTOM_APPS";
    public static final String PREF_DEBUG_MODE = "PREF_DEBUG_MODE";
    public static final String PREF_HIDDEN_COLUMNS = "PREF_HIDDEN_COLUMNS";

    private Component frame;
    private DeviceTableModel tableModel;

    private JCheckBox debugCheckbox;
    private JLabel viewLogsLabel;
    private JLabel resetLabel;

    public static int showSettings(Component frame, DeviceTableModel tableModel) {
        SettingsDialog settingsScreen = new SettingsDialog(frame, tableModel);
        return JOptionPane.showOptionDialog(frame, settingsScreen, "Settings", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);
    }

    private SettingsDialog(Component frame, DeviceTableModel tableModel) {
        this.frame = frame;
        this.tableModel = tableModel;
        setLayout(new MigLayout("", "[][]"));

        // columns
        add(new JLabel("Columns:"));
        JButton colsButton = new JButton("EDIT");
        colsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showColumns();
            }
        });
        add(colsButton, "wrap");

        // custom apps
        add(new JLabel("Custom Apps:"));
        JButton appButton = new JButton("EDIT");
        appButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAppsSettings();
            }
        });
        add(appButton, "wrap");

        // download location
        add(new JLabel("Download Location:"));
        appButton = new JButton("EDIT");
        appButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showDownloadLocation();
            }
        });
        add(appButton, "wrap");

        add(new JSeparator(), "growx, spanx, wrap");

        Preferences preferences = Preferences.userRoot();
        boolean isDebugMode = preferences.getBoolean(PREF_DEBUG_MODE, false);
        debugCheckbox = new JCheckBox("Debug Mode");
        debugCheckbox.setHorizontalTextPosition(SwingConstants.LEFT);
        debugCheckbox.setSelected(isDebugMode);
        debugCheckbox.addChangeListener(e -> {
            boolean updatedValue = debugCheckbox.isSelected();
            preferences.putBoolean(PREF_DEBUG_MODE, updatedValue);
            AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
            logger.setFileLogLevel(updatedValue ? Log.DEBUG : Log.INFO);
            refreshUi();
        });
        add(debugCheckbox, "span 2, al right, wrap");

        viewLogsLabel = new JLabel("View Logs");
        viewLogsLabel.setForeground(Color.BLUE);
        Font font = viewLogsLabel.getFont();
        Map attributes = font.getAttributes();
        attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        viewLogsLabel.setFont(font.deriveFont(attributes));
        viewLogsLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        viewLogsLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                viewLogs();
            }
        });
        add(viewLogsLabel, "span 2, al right, wrap");

        resetLabel = new JLabel("Reset Preferences");
        resetLabel.setForeground(Color.BLUE);
        resetLabel.setFont(font.deriveFont(attributes));
        resetLabel.setHorizontalTextPosition(SwingConstants.LEFT);
        resetLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                resetPreferences();
            }
        });
        add(resetLabel, "span 2, al right, wrap");

        refreshUi();
    }

    private void resetPreferences() {
        int rc = JOptionPane.showConfirmDialog(this, "Reset All Preferences?", "Reset Preferences", JOptionPane.YES_NO_OPTION);
        if (rc != JOptionPane.YES_OPTION) return;

        log.debug("resetPreferences: ");
        Preferences preferences = Preferences.userRoot();
        try {
            preferences.clear();
        } catch (BackingStoreException e) {
        }

    }

    private void refreshUi() {
        boolean isDebugMode = debugCheckbox.isSelected();
        viewLogsLabel.setVisible(isDebugMode);
    }

    private void viewLogs() {
        // show logs
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        File logsFile = logger.getFileLog();
        boolean rc = Utils.editFile(logsFile);
        if (!rc) {
            // open failed
            JOptionPane.showConfirmDialog(frame, "Failed to open logs: " + logsFile.getAbsolutePath(), "Error", JOptionPane.DEFAULT_OPTION);
        }
    }

    public static List<String> getHiddenColumnList() {
        Preferences preferences = Preferences.userRoot();
        String hiddenColsStr = preferences.get(PREF_HIDDEN_COLUMNS, null);
        return GsonHelper.stringToList(hiddenColsStr, String.class);
    }

    private void showColumns() {
        List<String> hiddenColList = getHiddenColumnList();
        CheckBoxList checkBoxList = new CheckBoxList();
        DeviceTableModel.Columns[] columnsArr = DeviceTableModel.Columns.values();
        for (DeviceTableModel.Columns column : columnsArr) {
            String colName = column.name();
            boolean isHidden = hiddenColList.contains(colName);
            checkBoxList.addItem(colName, !isHidden);
        }

        JPanel panel = new JPanel(new MigLayout());
        panel.add(new JLabel("De-select columns to hide"), "span");

        JScrollPane scroll = new JScrollPane(checkBoxList);
        panel.add(scroll, "grow, span, wrap");

        int rc = JOptionPane.showOptionDialog(frame, panel, "Visible Columns", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return;

        // save columns that are NOT selected
        List<String> selectedItems = checkBoxList.getUnSelectedItems();
        log.debug("HIDDEN: {}", GsonHelper.toJson(selectedItems));
        Preferences preferences = Preferences.userRoot();
        preferences.put(PREF_HIDDEN_COLUMNS, GsonHelper.toJson(selectedItems));
        tableModel.setHiddenColumns(selectedItems);
    }

    private void showAppsSettings() {
        List<String> appList = getCustomApps();
        List<String> resultList = showMultilineEditDialog("Custom Apps", "Enter package name(s) to track - 1 per line", appList);
        if (resultList == null) return;

        Preferences preferences = Preferences.userRoot();
        preferences.put(PREF_CUSTOM_APPS, GsonHelper.toJson(resultList));
        tableModel.setAppList(resultList);
    }

    /**
     * get list of custom monitored apps
     */
    public static List<String> getCustomApps() {
        Preferences preferences = Preferences.userRoot();
        String appPrefs = preferences.get(PREF_CUSTOM_APPS, null);
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

        int rc = JOptionPane.showOptionDialog(frame, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
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

        int rc = JOptionPane.showOptionDialog(frame, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return null;

        String results = inputField.getText().trim();
        log.debug("showEditField: results: {}", results);
        return results;
    }

    private void showDownloadLocation() {
        Preferences preferences = Preferences.userRoot();
        String downloadFolder = preferences.get(ExploreScreen.PREF_DOWNLOAD_FOLDER, System.getProperty("user.home"));

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
                preferences.put(ExploreScreen.PREF_DOWNLOAD_FOLDER, selectedFile.getAbsolutePath());
            }
        }

//        String result = showSingleLineEditDialog("Download Folder", "Enter Download Folder", downloadFolder);
//        if (result != null) {
//            preferences.put(ExploreView.PREF_DOWNLOAD_FOLDER, result);
//        }
    }

}
