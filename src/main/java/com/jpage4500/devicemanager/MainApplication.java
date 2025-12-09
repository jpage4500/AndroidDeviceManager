package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.RemoteServerManager;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.utils.*;
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
    private final List<File> openFileList;

    public static String version;

    public MainApplication(String[] args) {
        openFileList = new ArrayList<>();

        setupLogging();
        log.debug("APP START: {}, java:{}, os:{}", version, Runtime.version(), System.getProperty("os.name"));

        // handle command-line args
        boolean serverMode = false;
        for (String arg : args) {
            if (TextUtils.equalsIgnoreCase(arg, "--server")) {
                serverMode = true;
            }
        }

        // if run in a headless session this method will throw an exception..
        try {
            registerFileHandler();
        } catch (Exception e) {
            log.info("registerFileHandler: running in headless environment.. starting in server mode");
            serverMode = true;
        }

        if (serverMode) SwingUtilities.invokeLater(() -> runServerMode(args));
        else SwingUtilities.invokeLater(() -> initializeUI(args));
    }

    private void runServerMode(String[] args) {
        log.trace("runServerMode: SERVER MODE (headless)");
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (TextUtils.equalsIgnoreCase(arg, "--port") && i + 1 < args.length) {
                int port = TextUtils.getNumber(args[i + 1], 0);
                if (port > 0) {
                    // save port so server uses it
                    PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_SERVER_PORT, port);
                }
            } else if (TextUtils.equalsIgnoreCase(arg, "--token") && i + 1 < args.length) {
                String token = args[i + 1];
                PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN, token);
            }
        }

        // generate auth token if one isn't set
        String authToken = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN);
        if (TextUtils.isEmpty(authToken)) {
            authToken = RemoteConnectionUtils.generateAuthToken();
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN, authToken);
        }

        int port = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_SERVER_PORT, RemoteServerManager.DEFAULT_PORT);

        // auto connect to server
        PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_SERVER_ENABLED, true);

        // fetch network info in background
        Utils.runBackground(() -> {
            List<RemoteConnectionUtils.Network> networkList = RemoteConnectionUtils.getActiveNetworkInfo();
            String token = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN);
            log.info("***********************************************************");
            log.info("Connect to server using:");
            log.info("Token: {}", token);
            log.info("Port: {}", port);
            for (RemoteConnectionUtils.Network network : networkList) {
                log.info("Network: {}: {}, {}", network.label, network.ip, network.host);
                log.info("  > {}", RemoteConnectionUtils.generateConnectionString(network.ip, port, token, network.label));
            }
            log.info("***********************************************************");
        });

        DeviceManager deviceManager = DeviceManager.getInstance();
        // server will automatically start
        deviceManager.initialize(null);

        deviceManager.connectAdbServer(true);
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
                    BufferedImage image = UiUtils.getImage(Icons.LOGO, 256);
                    taskbar.setIconImage(image);
                }
            } catch (final Exception e) {
                log.error("initializeUI: Taskbar Exception: {}", e.getMessage());
            }
        }

        deviceScreen = new DeviceScreen();

        // handle command line args
        // --install <apk>
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (TextUtils.equalsIgnoreCase(arg, "--install") && i + 1 < args.length) {
                String apk = args[i + 1];
                File file = new File(apk);
                if (file.exists()) {
                    openFileList.add(file);
                } else {
                    log.warn("initializeUI: --install file not found: {}", apk);
                }
            }
        }

        sendFilesToDevice();
    }

    private void registerFileHandler() {
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

    private void sendFilesToDevice() {
        if (deviceScreen != null && !openFileList.isEmpty()) {
            deviceScreen.handleFilesOpened(openFileList, 1);
            openFileList.clear();
        }
    }

}
