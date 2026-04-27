package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.server.RemoteServerManager;
import com.jpage4500.devicemanager.ui.AppController;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.RemoteConnectionUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.Utils;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MainApplication {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    public enum LaunchMode {
        DEFAULT,
        LOGS_ONLY;

        /** Detect launch mode from CLI args + jdeploy launcher path basename. */
        static LaunchMode detect(String[] args) {
            if (args != null && args.length > 0) {
                String arg = args[0].toLowerCase();
                if (arg.equals("logs") || arg.equals("--logs") || arg.equals("--logs-only")) {
                    return LOGS_ONLY;
                }
            }
            String launcherPath = System.getProperty("jdeploy.launcher.path", "").toLowerCase();
            if (launcherPath.contains("adm-logs")) return LOGS_ONLY;
            return DEFAULT;
        }
    }

    private final AppController appController = new AppController();
    private final LaunchMode launchMode;
    private final List<File> openFileList = new ArrayList<>();

    public static String version;

    public MainApplication(String[] args, LaunchMode launchMode) {
        this.launchMode = launchMode;

        setupLogging();
        log.debug("APP START: mode:{}, {}, java:{}, os:{}", launchMode, version, Runtime.version(), System.getProperty("os.name"));

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
        new MainApplication(args, LaunchMode.detect(args));
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
        registerEmbeddedFonts();
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("defaultFont", new Font("Arial", Font.PLAIN, 16));
        defaults.put("Button.defaultButtonFollowsFocus", Boolean.TRUE);

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

        appController.installLifecycleHooks();

        if (launchMode == LaunchMode.LOGS_ONLY) {
            appController.startLogsOnly();
            appController.connectAdbServer();
        } else {
            appController.connectAdbServer();
            DeviceScreen deviceScreen = new DeviceScreen(appController);
            appController.setDeviceScreen(deviceScreen);
            appController.setupSystemTray();
            appController.scheduleUpdateChecks();

            if (!openFileList.isEmpty()) {
                appController.handleFilesOpened(new ArrayList<>(openFileList));
                openFileList.clear();
            }
        }
    }

    private void registerEmbeddedFonts() {
        String[] fontResources = {"fonts/JetBrainsMono-Regular.ttf"};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String resource : fontResources) {
            try (InputStream in = MainApplication.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    log.warn("registerEmbeddedFonts: not found: {}", resource);
                    continue;
                }
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                ge.registerFont(font);
            } catch (Exception e) {
                log.error("registerEmbeddedFonts: {} - {}", resource, e.getMessage());
            }
        }
    }

    private void registerFileHandler() {
        if (!Desktop.isDesktopSupported()) return;

        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(e -> {
                List<File> files = e.getFiles();
                log.debug("handleLaunchParams: openFile {}", files);
                appController.handleFilesOpened(new ArrayList<>(files));
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_OPEN_URI)) {
            desktop.setOpenURIHandler(e -> {
                java.net.URI uri = e.getURI();
                if (uri == null) return;
                log.debug("handleLaunchParams: openURI {}", uri);
                if (!"adm".equalsIgnoreCase(uri.getScheme())) return;
                if ("logs".equalsIgnoreCase(uri.getHost())) {
                    // adm://logs[/<serial>]
                    String path = uri.getPath();
                    String serial = (path != null && path.length() > 1) ? path.substring(1) : null;
                    appController.openLogsViaUrl(serial);
                }
                // other adm:// URIs (e.g. share/connect) are handled elsewhere
            });
        }
    }

}
