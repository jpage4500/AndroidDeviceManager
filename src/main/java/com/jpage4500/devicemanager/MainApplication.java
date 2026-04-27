package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.ui.AppController;
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

    public static String version;

    public MainApplication(LaunchMode launchMode) {
        this.launchMode = launchMode;
        setupLogging();
        handleLaunchParams();
        SwingUtilities.invokeLater(this::initializeUI);
        log.debug("APP START: mode:{}, {}, java:{}, os:{}", launchMode, version, Runtime.version(), System.getProperty("os.name"));
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
        new MainApplication(LaunchMode.detect(args));
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

            // filter out JmDNS logging
            logger.setIgnoreArr(new String[]{
                "JmDNSImpl",
                "DNSStateTask",
                "DNSIncoming",
                "DNSCache",
                "RecordReaper",
                "SocketListener",
                "Responder",
                "DNSQuestion",
                "DNSResolverTask",
                "NetworkTopologyDiscover",
            });
        } else {
            System.out.println("ERROR: no logger found: " + iLoggerFactory.getClass().getSimpleName());
        }
    }

    private void initializeUI() {
        FlatLightLaf.setup();
        registerEmbeddedFonts();
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

    private void handleLaunchParams() {
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
