package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.MainApplication;
import com.jpage4500.devicemanager.utils.GsonHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.*;

public class SettingsScreen extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(SettingsScreen.class);
    public static final String PREF_CUSTOM_APPS = "PREF_CUSTOM_APPS";

    private static final Insets WEST_INSETS = new Insets(5, 0, 5, 5);
    private static final Insets EAST_INSETS = new Insets(5, 5, 5, 0);

    private MainApplication app;

    public static int showSettings(MainApplication app) {
        SettingsScreen settingsScreen = new SettingsScreen(app);
        return JOptionPane.showOptionDialog(app.frame, settingsScreen, "Settings", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
    }

    public SettingsScreen(MainApplication app) {
        this.app = app;
        // custom apps
        add(new JLabel("Custom Apps:"), createGbc(0, 0));
        JButton appButton = new JButton("EDIT");
        appButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showAppsSettings();
            }
        });
        add(appButton, createGbc(1, 0));

    }

    private void showAppsSettings() {
        List<String> appList = getCustomApps();
        StringBuilder sb = new StringBuilder();
        for (String app : appList) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(app);
        }

        JTextArea inputField = new JTextArea(5, 1);
        inputField.setText(sb.toString());
        int rc = JOptionPane.showOptionDialog(app.frame, inputField, "Enter package name(s) to track - 1 per line", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
        if (rc != JOptionPane.YES_OPTION) return;
        String results = inputField.getText();
        log.debug("handleCustomApps: results: {}", results);
        String[] resultArr = results.split("\n");
        appList.clear();
        for (String result : resultArr) {
            if (result.trim().length() == 0) continue;
            appList.add(result);
        }

        Preferences preferences = Preferences.userRoot();
        preferences.put(PREF_CUSTOM_APPS, GsonHelper.toJson(appList));
        app.model.setAppList(appList);
    }

    /**
     * get list of custom monitored apps
     */
    public static List<String> getCustomApps() {
        Preferences preferences = Preferences.userRoot();
        String appPrefs = preferences.get(PREF_CUSTOM_APPS, null);
        return GsonHelper.stringToList(appPrefs, String.class);
    }

    private static GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;

        gbc.anchor = (x == 0) ? GridBagConstraints.WEST : GridBagConstraints.EAST;
        gbc.fill = (x == 0) ? GridBagConstraints.BOTH
            : GridBagConstraints.HORIZONTAL;

        gbc.insets = (x == 0) ? WEST_INSETS : EAST_INSETS;
        gbc.weightx = (x == 0) ? 0.1 : 1.0;
        gbc.weighty = 1.0;
        return gbc;
    }
}
