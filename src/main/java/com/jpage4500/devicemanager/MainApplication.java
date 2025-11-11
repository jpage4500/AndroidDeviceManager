package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.BaseScreen;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.utils.DialogHelper;
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

public class MainApplication implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    private final List<BaseScreen> screenList = new ArrayList<>();
    private final List<File> openFileList = new ArrayList<>();

    public static String version;

    public MainApplication(String[] args) {
        setupLogging();
        handleLaunchParams();
        SwingUtilities.invokeLater(() -> initializeUI(args));
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
        new MainApplication(args);
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
            // save logs to ~/.device_manager folder
            File deviceManagerFolder = Utils.getDeviceManagerFolder();
            logger.setFileLog(new File(deviceManagerFolder, "device_manager_log.txt"));

            int logLevel = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LOG_LEVEL, Log.INFO);
            logger.setFileLogLevel(logLevel);
        } else {
            System.out.println("ERROR: no logger found: " + iLoggerFactory.getClass().getSimpleName());
        }
    }

    private void initializeUI(String[] args) {
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

        // open main screen

        // TODO: handle command line arguments
        // - no args - open main window (DeviceScreen)
        // - logs - open ViewLogsScreen
        screenList.add(new DeviceScreen(this));
        sendFilesToDevice();

        connectAdbServer();
    }

    private void handleLaunchParams() {
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(e -> {
                List<File> files = e.getFiles();
                openFileList.addAll(files);
                log.debug("handleLaunchParams: {}", openFileList);
                sendFilesToDevice();
            });
        }
    }

    public void sendFilesToDevice() {
        if (!screenList.isEmpty() && !openFileList.isEmpty()) {
            BaseScreen baseScreen = screenList.get(0);
            baseScreen.handleFilesOpened(openFileList);
            openFileList.clear();
        }
    }

    private void connectAdbServer() {
        DeviceManager.getInstance().setDeviceListener(this);
        DeviceManager.getInstance().connectAdbServer(true);
    }

    @Override
    public void handleDevicesUpdated(List<Device> deviceList) {
        SwingUtilities.invokeLater(() -> {
            for (BaseScreen screen : screenList) {
                screen.handleDevicesUpdated(deviceList);
            }
        });
    }

    @Override
    public void handleDeviceRemoved(Device device) {
        SwingUtilities.invokeLater(() -> {
            for (BaseScreen screen : screenList) {
                screen.handleDeviceRemoved(device);
            }
        });
    }

    @Override
    public void handleException(Exception e) {
        SwingUtilities.invokeLater(() -> {
            String[] choices = {"Retry", "Cancel"};
            if (!DialogHelper.showOptionDialog(null, "ADB Server",
                "Unable to connect to ADB server. Please check that it's running and re-try", choices))
                return;

            connectAdbServer();
        });
    }

}
