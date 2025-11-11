package com.jpage4500.devicemanager;

import com.formdev.flatlaf.FlatLightLaf;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.GithubRelease;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.logging.Log;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.BaseScreen;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainApplication implements DeviceManager.DeviceListener {
    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    // update check for github releases
    public static final String UPDATE_SOURCE_GITHUB = "https://api.github.com/repos/jpage4500/AndroidDeviceManager/releases";
    public static final String URL_GITHUB = "https://github.com/jpage4500/AndroidDeviceManager/releases";

    private final List<BaseScreen> screenList = new ArrayList<>();
    private final List<File> openFileList = new ArrayList<>();

    public String version;

    private ScheduledExecutorService updateExecutorService;
    private GithubRelease latestRelease;

    public MainApplication(String[] args) {
        setupLogging();

        Properties prop = new Properties();
        try {
            prop.load(MainApplication.class.getClassLoader().getResourceAsStream("app.properties"));
            version = prop.getProperty("version");
        } catch (IOException ex) {
            System.out.println("Failed to load app.properties");
        }

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

        scheduleUpdateChecks();
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
        if (openFileList.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            for (BaseScreen screen : screenList) {
                screen.handleFilesOpened(openFileList);
            }
            openFileList.clear();
        });
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
    public void handleDeviceUpdated(Device device) {
        SwingUtilities.invokeLater(() -> {
            for (BaseScreen screen : screenList) {
                screen.handleDeviceUpdated(device);
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

    public void scheduleUpdateChecks() {
        // cancel any current scheduled update checks
        if (updateExecutorService != null) {
            updateExecutorService.shutdownNow();
            updateExecutorService = null;
        }

        // check for updates (default: true)
        boolean checkUpdates = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_CHECK_UPDATES, true);
        if (checkUpdates) {
            updateExecutorService = Executors.newSingleThreadScheduledExecutor();
            // check after 5 seconds, then again every 12 hours
            updateExecutorService.scheduleAtFixedRate(() -> checkForUpdates(null), 5, TimeUnit.HOURS.toSeconds(12), TimeUnit.SECONDS);
        }
    }

    public interface UpdateListener {
        void onUpdateCheckComplete(GithubRelease latestRelease);
    }

    public void checkForUpdates(UpdateListener updateListener) {
        // must be run off main/UI thread
        if (SwingUtilities.isEventDispatchThread()) {
            Utils.runBackground(() -> checkForUpdates(updateListener));
            return;
        }
        String response = NetworkUtils.getRequest(UPDATE_SOURCE_GITHUB);
        List<GithubRelease> releases = GsonHelper.stringToList(response, GithubRelease.class);
        if (!releases.isEmpty()) {
            latestRelease = releases.get(0);
            Utils.CompareResult compareResult = Utils.compareVersion(version, latestRelease.tagName);
            if (compareResult == Utils.CompareResult.VERSION_NEWER) {
                // update available
                log.debug("checkForUpdates: LATEST:{}, CURRENT:{}", latestRelease.tagName, version);
                if (updateListener != null) updateListener.onUpdateCheckComplete(latestRelease);

                for (BaseScreen screen : screenList) {
                    screen.handleUpdateAvailable(latestRelease);
                }
                return;
            }
        }
        if (updateListener != null) updateListener.onUpdateCheckComplete(null);

        // update UI on main thread
//        String finalVersion = version;
//        String finalDesc = desc;
//        if (version != null) {
//            log.debug("checkForUpdates: LATEST:{}, CURRENT:{}", version, MainApplication.version);
//            SwingUtilities.invokeLater(() -> {
//                updateVersion = finalVersion;
//                updateDesc = finalDesc;
//                // TODO:
////                updateLabel.setToolTipText("Update Available " + updateVersion + ", desc: " + finalDesc);
////                BufferedImage image = UiUtils.getImage("icon_update.png", UiUtils.IMG_SIZE_SMALL, UiUtils.IMG_SIZE_SMALL, Colors.COLOR_ERROR);
////                if (image != null) updateLabel.setIcon(new ImageIcon(image));
////                updateLabel.setVisible(true);
//                if (updateListener != null) {
//                    updateListener.onUpdateCheckComplete(finalVersion, finalDesc);
//                }
//            });
//        } else if (updateListener != null) {
//            SwingUtilities.invokeLater(() -> updateListener.onUpdateCheckComplete(null, null));
//        }
    }

    public void updateApp(Component frame, GithubRelease latestRelease) {
        // Jdeploy will auto-update app on start
        String jdeployPath = System.getProperty("jdeploy.launcher.path");
        boolean isJdeploy = jdeployPath != null;
        int index = TextUtils.indexOf(jdeployPath, "/Contents/MacOS/Client4JLauncher");
        if (index > 0) {
            // remove the launcher part and just open "Android Device Manager.app"
            // "/Users/USERNAME/Applications/Android Device Manager.app/Contents/MacOS/Client4JLauncher";
            jdeployPath = jdeployPath.substring(0, index);
        }

        String updateVersion = latestRelease.tagName;
        String updateDesc = latestRelease.body;

        JPanel panel = new JPanel(new MigLayout());
        panel.add(new JLabel(String.format("Update %s Available", updateVersion)), "wrap");
        JTextArea textArea = new JTextArea(updateDesc);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, "newline 20px, wrap");
        String yesOption;
        if (isJdeploy) {
            panel.add(new JLabel("Restart App?"), "newline 20px, wrap");
            yesOption = "Restart";
        } else {
            panel.add(new JLabel("View release in browser?"), "newline 20px, wrap");
            yesOption = "View";
        }
        String[] choices = {yesOption, "Cancel"};
        if (!DialogHelper.showCustomDialog(frame, panel, "Update Available", choices)) return;

        if (isJdeploy) {
            // exit and restart app
            //  ~/Applications/Android Device Manager.app/Contents/MacOS/Client4JLauncher
            final ArrayList<String> command = new ArrayList<>();
            command.add("open");
            command.add(jdeployPath);

            final ProcessBuilder builder = new ProcessBuilder(command);
            try {
                builder.start();
                System.exit(0);
            } catch (IOException ex) {
                log.error("handleVersionClicked: IOException: {}", ex.getMessage());
            }
        } else {
            // NOTE: check if app was launched from console or other (IntelliJ, .app)
            // log.debug("handleVersionClicked: CONSOLE:{}", System.console());
            Utils.openBrowser(URL_GITHUB);
        }
    }

}
