package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Properties;
import java.util.prefs.Preferences;

public class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private DeviceScreen deviceScreen;
    public static String version;

    public MainApplication() {
        setupLogging();
        SwingUtilities.invokeLater(this::initializeUI);
        log.debug("MainApplication: APP START: {}, java:{}, os:{}", version, Runtime.version(), System.getProperty("os.name"));
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "Device Manager");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        // required to run scripts when packaged as an app
        if (Utils.isMac()) {
            System.setProperty("jdk.lang.Process.launchMechanism", "FORK");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Device Manager");
        }
        Properties prop = new Properties();
        try {
            prop.load(MainApplication.class.getClassLoader().getResourceAsStream("app.properties"));
            version = prop.getProperty("version");
        } catch (IOException ex) {
            System.out.println("Failed to load app.properties");
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
        logger.setLogToFile(true);

        Preferences preferences = Preferences.userRoot();
        boolean isDebugMode = preferences.getBoolean(SettingsDialog.PREF_DEBUG_MODE, false);
        logger.setFileLogLevel(isDebugMode ? Log.DEBUG : Log.INFO);
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
        if (Utils.isMac()) {
            final Taskbar taskbar = Taskbar.getTaskbar();
            try {
                Image image = ImageIO.read(getClass().getResource("/images/logo.png"));
                taskbar.setIconImage(image);
            } catch (final Exception e) {
                log.error("Exception: {}", e.getMessage());
            }
        }

        deviceScreen = new DeviceScreen();
    }

}
