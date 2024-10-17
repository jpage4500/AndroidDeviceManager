package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import com.jpage4500.devicemanager.utils.Utils;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private DeviceScreen deviceScreen;
    private List<File> openFileList;

    public static String version;

    public MainApplication() {
        setupLogging();
        handleLaunchParams();
        SwingUtilities.invokeLater(this::initializeUI);
        log.debug("APP START: {}, java:{}, os:{}", version, Runtime.version(), System.getProperty("os.name"));
    }

    public static void main(String[] args) {
        if (Utils.isMac()) {
            System.setProperty("apple.awt.application.name", "Device Manager");
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            // required to run scripts when packaged as an app
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
        ILoggerFactory iLoggerFactory = LoggerFactory.getILoggerFactory();
        if (iLoggerFactory instanceof AppLoggerFactory logger) {
            // tag prefix allows for easy filtering: ie: 'adb logcat | grep PM_'
            //logger.setTagPrefix("DM");
            // set log level that application should log at (and higher)
            logger.setDebugLevel(Log.VERBOSE);
            logger.setLogToFile(true);
            //String tmpDir = System.getProperty("java.io.tmpdir");
            //logger.setFileLog(new File(tmpDir, "device_manager_log.txt"));

            boolean isDebugMode = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_DEBUG_MODE, false);
            logger.setFileLogLevel(isDebugMode ? Log.DEBUG : Log.INFO);
        } else {
            System.out.println("ERROR: no logger found: " + iLoggerFactory.getClass().getSimpleName());
        }
    }

    private void initializeUI() {
        FlatLightLaf.setup();
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("defaultFont", new Font("Arial", Font.PLAIN, 16));
        defaults.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);

        if (Taskbar.isTaskbarSupported()) {
            try {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    BufferedImage image = UiUtils.getImage("logo.png", 256);
                    taskbar.setIconImage(image);
                }
            } catch (final Exception e) {
                log.error("initializeUI: Taskbar Exception: {}", e.getMessage());
            }
        }

        deviceScreen = new DeviceScreen();
        sendFilesToDevice();
    }

    private void handleLaunchParams() {
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(e -> {
                List<File> files = e.getFiles();
                if (openFileList == null) openFileList = new ArrayList<>();
                openFileList.addAll(files);
                sendFilesToDevice();
            });
        }
    }

    private void sendFilesToDevice() {
        if (deviceScreen != null && openFileList != null && !openFileList.isEmpty()) {
            deviceScreen.handleFilesOpened(openFileList);
            openFileList = null;
        }
    }

}
