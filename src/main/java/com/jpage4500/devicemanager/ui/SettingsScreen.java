package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.viewmodel.DeviceTableModel;

import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.*;

public class SettingsScreen extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(SettingsScreen.class);
    public static final String PREF_CUSTOM_APPS = "PREF_CUSTOM_APPS";

    private Component frame;
    private DeviceTableModel tableModel;

    public static int showSettings(Component frame, DeviceTableModel tableModel) {
        SettingsScreen settingsScreen = new SettingsScreen(frame, tableModel);
        return JOptionPane.showOptionDialog(frame, settingsScreen, "Settings", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
    }

    public SettingsScreen(Component frame, DeviceTableModel tableModel) {
        this.frame = frame;
        this.tableModel = tableModel;
        setLayout(new MigLayout());
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

        // custom adb commands
        add(new JLabel("Custom ADB Commands:"));
        appButton = new JButton("EDIT");
        appButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showCommands();
            }
        });
        add(appButton);
    }

    private void showAppsSettings() {
        List<String> appList = getCustomApps();
        List<String> resultList = showEditField("Custom Apps", "Enter package name(s) to track - 1 per line", appList);
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

    private void showCommands() {
        Preferences preferences = Preferences.userRoot();
        String customCommands = preferences.get(DeviceView.PREF_CUSTOM_COMMAND_LIST, null);
        List<String> customList = GsonHelper.stringToList(customCommands, String.class);
        List<String> resultList = showEditField("Enter custom adb commands", "Enter adb custom command - 1 per line", customList);
        if (resultList == null) return;
        preferences.put(DeviceView.PREF_CUSTOM_COMMAND_LIST, GsonHelper.toJson(resultList));
    }

    private List<String> showEditField(String title, String message, List<String> stringList) {
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

}
