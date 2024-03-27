package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.DeviceView;
import com.jpage4500.devicemanager.ui.SettingsScreen;
import com.jpage4500.devicemanager.utils.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.*;

public class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private DeviceView deviceView;

    public MainApplication() {
        setupLogging();
        Runtime.Version version = Runtime.version();
        log.debug("MainApplication: APP START: {}, java:{}", Build.versionName, version);

        SwingUtilities.invokeLater(this::initializeUI);
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Device Manager");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        String os = System.getProperty("os.name");
        // required to run scripts when packaged as an app
        if (TextUtils.containsIgnoreCase(os, "Mac")) {
            System.setProperty("jdk.lang.Process.launchMechanism", "FORK");
        }
        new MainApplication();
    }

    /**
     * configuring SLF4J custom logger implementation
     * - call soon after Application:onCreate(); can be called again if debug mode changes
     */
    private void setupLogging() {
        AppLoggerFactory logger = (AppLoggerFactory) LoggerFactory.getILoggerFactory();
        // tag prefix allows for easy filtering: ie: 'adb logcat | grep PM_'
        //logger.setTagPrefix("DM");
        // set log level that application should log at (and higher)
        logger.setDebugLevel(Log.VERBOSE);
        logger.setMainThreadId(Thread.currentThread().getId());
        logger.setLogToFile(true);

        Preferences preferences = Preferences.userRoot();
        boolean isDebugMode = preferences.getBoolean(SettingsScreen.PREF_DEBUG_MODE, false);
        logger.setFileLogLevel(isDebugMode ? Log.VERBOSE : Log.DEBUG);
    }

    private void initializeUI() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception e) {
            log.error("initializeUI: {}", e.getMessage());
        }

        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("defaultFont", new Font("Arial", Font.PLAIN, 16));

        // set docker app icon (mac)
        final Taskbar taskbar = Taskbar.getTaskbar();
        try {
            Image image = ImageIO.read(getClass().getResource("/images/logo.png"));
            taskbar.setIconImage(image);
        } catch (final Exception e) {
            log.error("Exception: {}", e.getMessage());
        }

        deviceView = new DeviceView();
    }

}
