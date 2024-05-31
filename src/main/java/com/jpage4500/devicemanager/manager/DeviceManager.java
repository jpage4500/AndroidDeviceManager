package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.ui.dialog.ConnectDialog;
import com.jpage4500.devicemanager.ui.ExploreScreen;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.Timer;
import com.jpage4500.devicemanager.utils.Utils;
import se.vidstige.jadb.*;
import se.vidstige.jadb.managers.PackageManager;
import se.vidstige.jadb.managers.PropertyManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.prefs.Preferences;

public class DeviceManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceManager.class);

    // adb commands
    public static final String COMMAND_SERVICE_PHONE1 = "service call iphonesubinfo 15 s16 com.android.shell";
    public static final String COMMAND_SERVICE_PHONE2 = "service call iphonesubinfo 12 s16 com.android.shell";
    public static final String COMMAND_SERVICE_IMEI = "service call iphonesubinfo 1 s16 com.android.shell";
    public static final String COMMAND_REBOOT = "reboot";
    public static final String COMMAND_DISK_SIZE = "df";
    public static final String COMMAND_LIST_PROCESSES = "ps -A -o PID,ARGS"; // | grep u0_
    public static final String COMMAND_DUMPSYS_BATTERY = "dumpsys battery";

    // scripts that app will run
    private static final String SCRIPT_TERMINAL = "terminal";
    private static final String SCRIPT_MIRROR = "mirror";

    public static final String FILE_CUSTOM_PROP = "/sdcard/android_device_manager.properties";

    private static volatile DeviceManager instance;

    private final List<Device> deviceList;
    private final String tempFolder;
    private final List<Process> processList;

    private final ExecutorService commandExecutorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicBoolean isLogging = new AtomicBoolean(false);
    private ScheduledFuture<?> deviceRefreshRuture;

    private JadbConnection connection;

    public static DeviceManager getInstance() {
        if (instance == null) {
            synchronized (DeviceManager.class) {
                if (instance == null) {
                    instance = new DeviceManager();
                }
            }
        }
        return instance;
    }

    private DeviceManager() {
        deviceList = new ArrayList<>();
        processList = new ArrayList<>();

        commandExecutorService = Executors.newFixedThreadPool(10);
        scheduledExecutorService = Executors.newScheduledThreadPool(3);

        tempFolder = System.getProperty("java.io.tmpdir");
        copyResourcesToFiles();
    }

    public interface DeviceListener {
        // device list was refreshed
        void handleDevicesUpdated(List<Device> deviceList);

        // single device was updated
        void handleDeviceUpdated(Device device);

        void handleException(Exception e);
    }

    public void connectAdbServer(DeviceManager.DeviceListener listener) {
        connection = new JadbConnection();
        commandExecutorService.submit(() -> {
            try {
                String hostVersion = connection.getHostVersion();
                log.debug("connectAdbServer: v:{}", hostVersion);
                connection.createDeviceWatcher(new DeviceDetectionListener() {
                    @Override
                    public void onDetect(List<JadbDevice> devices) {
                        handleDeviceUpdate(devices, listener);
                    }

                    @Override
                    public void onException(Exception e) {
                        log.error("connectAdbServer: onException: {}", e.getMessage());
                        listener.handleException(e);
                    }
                }).run();
            } catch (Exception e) {
                log.error("Exception: {}", e.getMessage());
            }
        });
    }

    /**
     * called when a device is added/updated/removed
     * NOTE: run on background thread
     */
    private void handleDeviceUpdate(List<JadbDevice> devices, DeviceListener listener) {
        //log.debug("onDetect: GOT:{}, {}", devices.size(), GsonHelper.toJson(devices));
        List<Device> addedDeviceList = new ArrayList<>();

        // 1) look for devices that don't exist today
        for (JadbDevice jadbDevice : devices) {
            String serial = jadbDevice.getSerial();
            // -- does this device already exist? --
            Device device = getDevice(serial);
            if (device == null || !device.isOnline) {
                // -- ADD DEVICE --
                if (device == null) {
                    device = new Device();
                    log.trace("handleDeviceUpdate: DEVICE_ADDED: {}", serial);
                    synchronized (deviceList) {
                        deviceList.add(device);
                    }
                }
                device.serial = serial;
                device.jadbDevice = jadbDevice;
                addedDeviceList.add(device);
            }
        }

        // 2) look for devices that are now offline
        for (Device device : deviceList) {
            boolean isFound = false;
            for (JadbDevice jadbDevice : devices) {
                if (device.serial.equals(jadbDevice.getSerial())) {
                    isFound = true;
                    break;
                }
            }
            if (!isFound) {
                // -- DEVICE REMOVED --
                device.isOnline = false;
                device.lastUpdateMs = System.currentTimeMillis();
                if (log.isTraceEnabled()) log.trace("handleDeviceUpdate: DEVICE_OFFLINE: {}", device.getDisplayName());
                listener.handleDeviceUpdated(device);
            }
        }

        if (!addedDeviceList.isEmpty()) {
            // notify listener that device list changed
            listener.handleDevicesUpdated(deviceList);

            for (Device addedDevice : addedDeviceList) {
                // fetch more details for these devices
                try {
                    JadbDevice.State state = addedDevice.jadbDevice.getState();
                    if (state == JadbDevice.State.Device) {
                        log.trace("handleDeviceUpdate: ONLINE: {} -> {}", addedDevice.serial, state);
                        addedDevice.status = "fetching details..";
                        listener.handleDeviceUpdated(addedDevice);
                        addedDevice.isOnline = true;
                        addedDevice.lastUpdateMs = System.currentTimeMillis();
                        fetchDeviceDetails(addedDevice, true, listener);
                    } else {
                        log.debug("handleDeviceUpdate: NOT_READY: {} -> {}", addedDevice.serial, state);
                        addedDevice.status = state.name();
                        listener.handleDeviceUpdated(addedDevice);
                    }
                } catch (Exception e) {
                    //  command failed: device still authorizing
                    //  command failed: device unauthorized.
                    //  This adb server's $ADB_VENDOR_KEYS is not set
                    //  Try 'adb kill-server' if that seems wrong.
                    //  Otherwise check for a confirmation dialog on your device.
                    log.debug("handleDeviceUpdate: NOT_READY_EXCEPTION: {} -> {}", addedDevice.serial, e.getMessage());
                    addedDevice.status = e.getMessage();
                    listener.handleDeviceUpdated(addedDevice);
                }
            }

            // run periodic task to update device state
            if (deviceRefreshRuture == null) {
                deviceRefreshRuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                    log.trace("handleDeviceUpdate: REFRESH");
                    for (Device device : deviceList) {
                        if (device.isOnline) {
                            fetchDeviceDetails(device, false, listener);
                        }
                    }
                }, 5, 5, TimeUnit.MINUTES);
            }
        }
    }

    public void refreshDevices(DeviceListener listener) {
        synchronized (deviceList) {
            for (Device device : deviceList) {
                fetchDeviceDetails(device, true, listener);
            }
        }
    }

    /**
     * fetch device details (phone #, name, model, disk space, battery level, etc)
     *
     * @param fullRefresh - true to fetch everythign; false to only fetch values that would change often (battery, disk)
     */
    private void fetchDeviceDetails(Device device, boolean fullRefresh, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            Timer timer = new Timer();
            log.debug("fetchDeviceDetails: {}", device.serial);
            // only change isBusy flag if not already set (ie: user is mirring device)
            boolean prevBusyState = device.isBusy;
            if (!prevBusyState) {
                device.isBusy = true;
                listener.handleDeviceUpdated(device);
            }
            if (fullRefresh) {
                // -- phone number --
                device.phone = runShellServiceCall(device, COMMAND_SERVICE_PHONE1);
                if (TextUtils.isEmpty(device.phone)) device.phone = runShellServiceCall(device, COMMAND_SERVICE_PHONE2);
                // -- IMEI --
                device.imei = runShellServiceCall(device, COMMAND_SERVICE_IMEI);

                // -- device properties (model, OS) --
                try {
                    device.propMap = new PropertyManager(device.jadbDevice).getprop();
                } catch (Exception e) {
                    log.error("fetchDeviceDetails: PROP Exception:{}", e.getMessage());
                }

                // -- custom properties --
                OutputStream outputStream = new ByteArrayOutputStream();
                RemoteFile file = new RemoteFile(FILE_CUSTOM_PROP);
                try {
                    device.jadbDevice.pull(file, outputStream);
                    String customPropStr = outputStream.toString();
                    String[] customPropArr = customPropStr.split("\\n+");
                    for (String customProp : customPropArr) {
                        String[] propArr = customProp.split("=", 2);
                        if (propArr.length < 2) continue;
                        String propKey = propArr[0];
                        String propValue = propArr[1];
                        // old versions replaced spaces with "~"
                        propValue = propValue.replaceAll("~", " ");
                        if (device.customPropertyMap == null) device.customPropertyMap = new HashMap<>();
                        device.customPropertyMap.put(propKey, propValue);
                    }
                } catch (Exception e) {
                    // NOTE: this is normal as file won't exist unless set
                    //log.trace("fetchDeviceDetails: PULL Exception:{}", e.getMessage());
                }
            }

            // -- disk free space --
            List<String> sizeLines = runShell(device, COMMAND_DISK_SIZE);
            if (!sizeLines.isEmpty()) {
                // only interested in last line
                String last = sizeLines.get(sizeLines.size() - 1);
                // /dev/fuse         115249236 14681484 100436680  13% /storage/emulated
                //                                      ^^^^^^^^^
                String size = TextUtils.split(last, 3);
                try {
                    // size is in 1k blocks
                    device.freeSpace = Long.parseLong(size) * 1000L;
                } catch (Exception e) {
                    log.trace("fetchDeviceDetails: FREE_SPACE Exception:{}", e.getMessage());
                }
            }

            // -- version of installed apps --
            List<String> customApps = SettingsDialog.getCustomApps();
            for (String customApp : customApps) {
                // shell dumpsys package $PACKAGE | grep versionName | sed 's/    versionName=//')
                List<String> appResultList = runShell(device, "dumpsys package " + customApp);
                for (String appLine : appResultList) {
                    // "    versionName=24.05.16.160",
                    int index = appLine.indexOf("versionName=");
                    if (index > 0) {
                        String versionName = appLine.substring(index + "versionName=".length());
                        if (device.customAppVersionList == null) device.customAppVersionList = new HashMap<>();
                        device.customAppVersionList.put(customApp, versionName);
                        //log.trace("fetchDeviceDetails: {} = {}", customApp, versionName);
                    }
                }
            }

            // -- battery level, charging status, etc --
            List<String> batteryList = runShell(device, COMMAND_DUMPSYS_BATTERY);
            for (String batteryLine : batteryList) {
                String[] batteryArr = batteryLine.split(": ", 2);
                if (batteryArr.length < 2) continue;
                String name = batteryArr[0].trim();
                String value = batteryArr[1].trim();
                switch (name) {
                    case "level":
                        //  level: 100
                        try {
                            device.batteryLevel = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            log.debug("fetchDeviceDetails: BAD_INT: {}, {}", value, e.getMessage());
                        }
                    case "AC powered":
                        //  AC powered: true
                        if (Boolean.parseBoolean(value)) device.powerStatus = Device.PowerStatus.POWER_AC;
                        break;
                    case "USB powered":
                        //  USB powered: false
                        if (Boolean.parseBoolean(value)) device.powerStatus = Device.PowerStatus.POWER_USB;
                        break;
                    case "Wireless powered":
                        //  Wireless powered: false
                        if (Boolean.parseBoolean(value)) device.powerStatus = Device.PowerStatus.POWER_WIRELESS;
                        break;
                    case "Dock powered":
                        //  Dock powered: false
                        if (Boolean.parseBoolean(value)) device.powerStatus = Device.PowerStatus.POWER_DOCK;
                        break;
                }
            }
            device.lastUpdateMs = System.currentTimeMillis();

            if (log.isTraceEnabled()) log.trace("fetchDeviceDetails: {}: full:{}, {}", timer, fullRefresh, GsonHelper.toJson(device));

            if (!prevBusyState) device.isBusy = false;
            listener.handleDeviceUpdated(device);

            if (fullRefresh) {
                // keep track of wireless devices
                ConnectDialog.addWirelessDevice(device);
            }
        });
    }

    /**
     * run a 'shell service call ..." command and parse the results into a String
     */
    private String runShellServiceCall(Device device, String command) {
        List<String> resultList = runShell(device, command);
        // Result: Parcel(
        // 0x00000000: 00000000 0000000b 00350031 00300034 '........1.2.2.2.'
        // 0x00000010: 00310039 00390034 00310032 00000034 '3.3.3.4.4.4.4...')
        StringBuilder result = null;
        if (resultList.size() > 1) {
            for (int i = 1; i < resultList.size(); i++) {
                String line = resultList.get(i);
                int stPos = line.indexOf('\'');
                if (stPos >= 0) {
                    int endPos = line.indexOf('\'', stPos + 1);
                    if (endPos >= 0) {
                        line = line.substring(stPos + 1, endPos);
                        line = line.replaceAll("[^-?0-9]+", "");
                        if (result == null) result = new StringBuilder();
                        result.append(line);
                    }
                }
            }
        }
        //log.trace("runShellServiceCall: RESULTS: {}", result);
        return result != null ? result.toString() : null;
    }

    /**
     * run a shell command and return multi-line output
     */
    private List<String> runShell(Device device, String command) {
        List<String> resultList = new ArrayList<>();
        List<String> commandList = TextUtils.splitSafe(command);
        InputStream inputStream = null;
        try {
            String firstCommand = commandList.get(0);
            List<String> subList = commandList.subList(1, commandList.size());
            //log.trace("runShell: COMMAND:{}, ARGS:{}", firstCommand, GsonHelper.toJson(subList));
            inputStream = device.jadbDevice.executeShell(firstCommand, subList.toArray(new String[0]));
            BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = input.readLine()) != null) {
                //log.trace("runShell: {}", line);
                resultList.add(line);
            }
        } catch (Exception e) {
            log.error("runShell: cmd:{}, Exception: {}", command, e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return resultList;
    }

    private Device getDevice(String serial) {
        synchronized (deviceList) {
            for (Device device : deviceList) {
                if (TextUtils.equals(device.serial, serial)) {
                    return device;
                }
            }
        }
        return null;
    }

    /**
     * run scrcpy app
     */
    public void mirrorDevice(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            log.debug("mirrorDevice: {}", device.getDisplayName());
            File scriptFile = getScriptFile(SCRIPT_MIRROR);
            AppResult appResult = runApp(scriptFile.getAbsolutePath(), true, device.serial);

            // TODO: figure out how to determine if scrcpy was run successfully..
            // - scrcpy will log to stderr even when successful
            listener.onTaskComplete(appResult.isSuccess, TextUtils.join(appResult.stdErr, "\n"));
        });
    }

    private String findApp(String app) {
        String path = System.getenv("PATH");
        log.debug("findApp: PATH:{}", path);
        String[] pathArr = path.split(":");
        for (String p : pathArr) {
            String fullPath = checkFile(p, app);
            if (fullPath != null) return fullPath;
        }
        // default to just app name alone - no path.. hopefully ProcessBuilder will find it
        log.debug("findApp: NOT_FOUND: {}", app);
        // try some other common locations
        if (Utils.isMac()) {
            String[] arr = new String[]{
                    "/opt/homebrew/bin",
                    "/usr/local/bin",
            };
            for (String s : arr) {
                String fullPath = checkFile(s, app);
                if (fullPath != null) return fullPath;
            }
        }

        return app;
    }

    private String checkFile(String path, String app) {
        File f = new File(path, app);
        if (f.exists()) {
            log.debug("checkFile: GOT:{}", f.getAbsolutePath());
            return f.getAbsolutePath();
        }
        return null;
    }

    public void captureScreenshot(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            Preferences preferences = Preferences.userRoot();
            String downloadFolder = preferences.get(ExploreScreen.PREF_DOWNLOAD_FOLDER, System.getProperty("user.home"));
            // 20211215-1441PM-1.png
            String name = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".png";
            try {
                Timer timer = new Timer();
                BufferedImage image = device.jadbDevice.screencap();
                // save to file
                File outputfile = new File(downloadFolder, name);
                ImageIO.write(image, "png", outputfile);
                log.debug("captureScreenshot: DONE:{}, {}x{}, {}", timer, image.getWidth(), image.getHeight(), outputfile.getAbsolutePath());
                // open with default viewer
                Desktop.getDesktop().open(outputfile);
                listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("captureScreenshot: {}", e.getMessage());
                listener.onTaskComplete(false, e.getMessage());
            }
        });
    }

    public void setProperty(Device device, String key, String value, TaskListener listener) {
        commandExecutorService.submit(() -> {
            if (device.customPropertyMap == null) device.customPropertyMap = new HashMap<>();
            // update property
            device.customPropertyMap.put(key, value);
            // turn into key=value string
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : device.customPropertyMap.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            RemoteFile remote = new RemoteFile(FILE_CUSTOM_PROP);
            // write to properties file on device
            try {
                InputStream stream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
                device.jadbDevice.push(stream, System.currentTimeMillis() / 1000, JadbDevice.DEFAULT_MODE, remote);
                log.debug("setProperty: {}, key:{}, value:{}, DONE", device.serial, key, value);
                if (listener != null) listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("setProperty: {}, {}={}Exception:{}", device.serial, key, value, e.getMessage());
                if (listener != null) listener.onTaskComplete(false, e.getMessage());
            }
        });
    }

    public void installApp(Device device, File file, TaskListener listener) {
        commandExecutorService.submit(() -> {
            try {
                PackageManager packageManager = new PackageManager(device.jadbDevice);
                packageManager.install(file);
                if (listener != null) listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("installApp: {}, {}", file.getAbsolutePath(), e.getMessage());
                device.status = "failed: " + e.getMessage();
                if (listener != null) listener.onTaskComplete(true, null);
            }
        });
    }

    public void copyFile(Device device, File file, String dest, TaskListener listener) {
        commandExecutorService.submit(() -> {
            log.debug("copyFile: {} -> {}", file.getAbsolutePath(), dest);
            try {
                RemoteFile remoteFile = new RemoteFileRecord(dest, file.getName(), 0, 0, 0);
                device.jadbDevice.push(file, remoteFile);
                listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("copyFile: {} -> {}, Exception:{}", file.getAbsolutePath(), dest, e.getMessage());
                listener.onTaskComplete(false, null);
            }
        });
    }

    public void restartDevice(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            runShell(device, COMMAND_REBOOT);
            // TODO: detect success/fail
            listener.onTaskComplete(true, null);
        });
    }

    public void runCustomCommand(Device device, String customCommand, TaskListener listener) {
        commandExecutorService.submit(() -> {
            List<String> results = runShell(device, customCommand);
            log.debug("runCustomCommand: DONE:{}", GsonHelper.toJson(results));
            String displayStr = TextUtils.join(results, "\n");
            if (listener != null) listener.onTaskComplete(true, displayStr);
        });
    }

    public void openTerminal(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            File scriptFile = getScriptFile(SCRIPT_TERMINAL);
            runApp(scriptFile.getAbsolutePath(), true, device.serial);
        });
    }

    public interface DeviceFileListener {
        void handleFiles(List<DeviceFile> fileList, String error);
    }

    public void listFiles(Device device, String path, boolean useRoot, DeviceFileListener listener) {
        commandExecutorService.submit(() -> {
            try {
                String safePath = path + "/";
                if (safePath.indexOf(' ') > 0) {
                    safePath = "\"" + safePath + "\"";
                }
                log.trace("listFiles: {} {}", safePath, useRoot ? "(ROOT)" : "");
                String command = "ls -al " + safePath;
                if (useRoot) command = "su -c " + command;
                List<String> dirList = runShell(device, command);
                List<DeviceFile> fileList = new ArrayList<>();
                for (String dir : dirList) {
                    DeviceFile file = DeviceFile.fromEntry(dir);
                    if (file != null) fileList.add(file);
                    else if (TextUtils.containsAny(dir, true, "inaccessible or not found", "permission denied")) {
                        log.debug("listFiles: ERROR: {}", dir);
                        // check for common errors:
                        // <COMMAND>: inaccessible or not found
                        // ls: <FOLDER>: Permission denied
                        listener.handleFiles(null, dir);
                        return;
                    }
                }
                //log.trace("listFiles: FILES:{}, PATH:{}, {}", fileList.size(), safePath, GsonHelper.toJson(fileList));
                listener.handleFiles(fileList, null);
            } catch (Exception e) {
                log.error("listFiles: {}, Exception:{}", path, e.getMessage());
                log.debug("listFiles: ", e);
                listener.handleFiles(null, e.getMessage());
            }
        });
    }

    public interface TaskListener {
        void onTaskComplete(boolean isSuccess, String error);
    }

    public void downloadFile(Device device, String path, DeviceFile file, File saveFile, TaskListener listener) {
        log.debug("downloadFile: {}/{} -> {}", path, file.name, saveFile.getAbsolutePath());
        commandExecutorService.submit(() -> {
            downloadFileInternal(device, path, file, saveFile);
        });
    }

    /**
     * recursive method to download a file or folder
     */
    private void downloadFileInternal(Device device, String path, DeviceFile file, File saveFile) {
        if (file.isDirectory || file.isSymbolicLink) {
            // create local folder
            if (!saveFile.exists()) {
                boolean isOk = saveFile.mkdir();
                log.trace("downloadFileInternal: DIR:{}, mkdir:{}", saveFile.getAbsolutePath(), isOk);
            }
            // get list of files in folder
            String dirPath = path + "/" + file.name;
            listFiles(device, dirPath, false, (fileList, error) -> {
                if (fileList == null || error != null) return;
                for (DeviceFile deviceFile : fileList) {
                    File subFile = new File(saveFile, deviceFile.name);
                    downloadFileInternal(device, path, deviceFile, subFile);
                }
            });
        } else {
            // pull file
            log.trace("downloadFileInternal: {}/{} -> {}", path, file.name, saveFile.getAbsolutePath());
            RemoteFile remoteFile = new RemoteFileRecord(path, file.name, 0, 0, 0);
            try {
                device.jadbDevice.pull(remoteFile, saveFile);
            } catch (Exception e) {
                log.error("downloadFileInternal: {}/{}, Exception:{}", path, file.name, e.getMessage());
            }
        }
    }

    public void deleteFile(Device device, String path, DeviceFile file, TaskListener listener) {
        commandExecutorService.submit(() -> {
            String command = "rm -rf " + path + "/" + file.name;
            if (file.isDirectory) command += "/";
            List<String> resultList = runShell(device, command);
            log.debug("deleteFile: {} -> {}", command, GsonHelper.toJson(resultList));
            // TODO: determine success/fail
            listener.onTaskComplete(true, null);
        });
    }

    public void createFolder(Device device, String path, TaskListener listener) {
        commandExecutorService.submit(() -> {
            List<String> resultList = runShell(device, "mkdir \"" + path + "\"");
            log.debug("createFolder: {} -> {}", path, GsonHelper.toJson(resultList));
            // TODO: determine success/fail
            listener.onTaskComplete(true, null);
        });
    }

    public void connectDevice(String ip, int port, TaskListener listener) {
        commandExecutorService.submit(() -> {
            try {
                log.debug("connectDevice: {}:{}", ip, port);
                connection.connectToTcpDevice(new InetSocketAddress(ip, port));
                listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("connectDevice: {}:{}, Exception:{}", ip, port, e.getMessage());
                listener.onTaskComplete(false, null);
            }
        });
    }

    public void disconnectDevice(String serial, TaskListener listener) {
        commandExecutorService.submit(() -> {
            String[] deviceArr = TextUtils.split(serial, ":");
            if (deviceArr.length < 2) {
                log.error("disconnectDevice: bad device:{}", serial);
                return;
            }
            String ip = deviceArr[0];
            try {
                int port = Integer.parseInt(deviceArr[1]);
                log.debug("disconnectDevice: {}:{}", ip, port);
                connection.disconnectFromTcpDevice(new InetSocketAddress(ip, port));
                listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("connectDevice: {}, Exception:{}", serial, e.getMessage());
                listener.onTaskComplete(false, null);
            }
        });
    }

    public void sendInputText(Device device, String text, TaskListener listener) {
        commandExecutorService.submit(() -> {
            log.debug("sendInputText: {}", text);
            try {
                device.jadbDevice.inputText(text);
                if (listener != null) listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("sendInputText: {}, Exception:{}", text, e.getMessage());
                if (listener != null) listener.onTaskComplete(false, null);
            }
        });
    }

    public void sendInputKeyCode(Device device, int keyEvent, TaskListener listener) {
        commandExecutorService.submit(() -> {
            log.debug("sendInputKeyCode: {}", keyEvent);
            try {
                device.jadbDevice.inputKeyEvent(keyEvent);
                if (listener != null) listener.onTaskComplete(true, null);
            } catch (Exception e) {
                log.error("sendInputKeyCode: {}, Exception:{}", keyEvent, e.getMessage());
                if (listener != null) listener.onTaskComplete(false, null);
            }
        });
    }

    public interface DeviceLogListener {
        void handleLogEntries(List<LogEntry> logEntryList);

        void handleProcessMap(Map<String, String> processMap);
    }

    public void startLogging(Device device, Long startTime, DeviceLogListener listener) {
        stopLogging(device);
        commandExecutorService.submit(() -> {
            log.debug("startLogging: {}", startTime);
            isLogging.set(true);
            InputStream inputStream = null;
            try {
                String[] args = new String[]{"-v", "threadtime"};
                inputStream = device.jadbDevice.executeShell("logcat", args);
                BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));

                long lastUpdateMs = System.currentTimeMillis();
                List<LogEntry> logList = new ArrayList<>();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                int year = Calendar.getInstance().get(Calendar.YEAR);
                String line;
                while ((line = input.readLine()) != null) {
                    LogEntry logEntry = new LogEntry(line, dateFormat, year);
                    if (logEntry.date == null) continue;
                    else if (startTime != null && startTime > logEntry.timestamp) {
                        log.trace("startLogging: too old: {} ({}) vs {}", logEntry.timestamp, logEntry.date, startTime);
                        continue;
                    }

                    logList.add(logEntry);

                    // only update every X ms
                    if (System.currentTimeMillis() - lastUpdateMs >= 100 && !logList.isEmpty()) {
                        // update
                        listener.handleLogEntries(logList);
                        logList.clear();
                        lastUpdateMs = System.currentTimeMillis();

                        // check if logging is still running
                        if (!isLogging.get()) break;
                    }
                }
            } catch (Exception e) {
                log.error("startLogging: {}", e.getMessage());
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        });

        // run a periodic task to fetch running apps from device so logs can replace PID with app name
        commandExecutorService.submit(() -> {
            Map<String, String> pidMap = getProcessMap(device);
            if (!pidMap.isEmpty()) {
                listener.handleProcessMap(pidMap);
            }
            // check if logging is still running and schedule next lookup
            if (isLogging.get()) {
                scheduleNextProcessCheck(device, listener);
            }
        });
    }

    private void scheduleNextProcessCheck(Device device, DeviceLogListener listener) {
        // make sure logging is still running
        if (!isLogging.get()) return;

        // run in 30 seconds
        scheduledExecutorService.schedule(() -> {
            // make sure logging is still running
            if (!isLogging.get()) return;

            Map<String, String> pidMap = getProcessMap(device);
            listener.handleProcessMap(pidMap);
            scheduleNextProcessCheck(device, listener);
        }, 30, TimeUnit.SECONDS);
    }

    private Map<String, String> getProcessMap(Device device) {
        List<String> pidList = runShell(device, COMMAND_LIST_PROCESSES);
        // 7617 com.android.traceur
        // 7677 [csf_sync_update]
        Map<String, String> pidMap = new HashMap<>();
        for (String line : pidList) {
            String[] lineArr = line.trim().split(" ", 2);
            if (lineArr.length < 2) continue;
            String pid = lineArr[0];
            String app = lineArr[1];
            pidMap.put(pid, app);
        }
        return pidMap;
    }

    public void stopLogging(Device device) {
        if (isLogging.get()) {
            log.debug("stopLogging: ");
            isLogging.set(false);
        }
    }

    public boolean isLogging(Device device) {
        return isLogging.get();
    }

    public void handleExit() {
        List<Process> processCopyList;
        synchronized (processList) {
            processCopyList = new ArrayList<>(processList);
        }

        for (Process process : processCopyList) {
            if (process.isAlive()) {
                log.debug("handleExit: killing: {}", process);
                process.destroy();
            }
        }
    }

    /**
     * 1-time copy of all resources/scripts/*.sh files to temp folder and make them executable
     */
    private void copyResourcesToFiles() {
        try {
            File file = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (file.getName().endsWith(".jar")) {
                // running via JAR file
                int numScripts = 0;
                JarFile jarFile = new JarFile(file);
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();
                    String name = jarEntry.getName();
                    if (TextUtils.endsWith(name, ".sh", ".bat")) {
                        int pos = name.indexOf("scripts/");
                        if (pos >= 0) {
                            name = name.substring(pos + "scripts/".length());
                            copyResourceToFile(name, jarFile.getInputStream(jarEntry));
                            numScripts++;
                        }
                    }
                }
                log.trace("copyResourcesToFiles: {}, JAR: {}, tmp:{}", numScripts, file, tempFolder);
            } else {
                log.trace("copyResourcesToFiles: IDE: {}, tmp:{}", file, tempFolder);
                // running via IntelliJ IDE
                URL url = getClass().getResource("/scripts");
                Path path = Paths.get(url.toURI());
                Files.walk(path, 1).forEach(p -> {
                    String filename = p.toString();
                    if (TextUtils.endsWith(filename, ".sh", ".bat")) {
                        try {
                            String name = p.toFile().getName();
                            InputStream inputStream = Files.newInputStream(p);
                            copyResourceToFile(name, inputStream);
                        } catch (IOException e) {
                            log.debug("copyResourcesToFiles: IOException:{}", e.getMessage());
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.error("copyResourcesToFiles: Exception:", e);
        }
    }

    private void copyResourceToFile(String name, InputStream is) {
        File tempFile = new File(tempFolder, name);
        //log.trace("copyResource: {} to {}", name, tempFile.getAbsolutePath());
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            for (String line; (line = r.readLine()) != null; ) {
                sb.append(line).append('\n');
            }
            r.close();
            is.close();

            Files.write(tempFile.toPath(), sb.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            tempFile.setExecutable(true);
        } catch (Exception e) {
            log.error("copyResource: Exception:", e);
        }
    }

    public File getScriptFile(String scriptName) {
        if (Utils.isWindows()) scriptName += ".bat";
        else scriptName += ".sh";
        File tempFile = new File(tempFolder, scriptName);
        if (!tempFile.exists()) {
            log.error("runScript: script doesn't exist! {}", tempFile.getAbsoluteFile());
            // try re-creating the files again
            copyResourcesToFiles();
            if (!tempFile.exists()) {
                log.error("runScript: script STILL doesn't exist! {}", tempFile.getAbsoluteFile());
                return null;
            }
        }
        return tempFile;
    }

    public static class AppResult {
        boolean isSuccess;
        List<String> stdOut;
        List<String> stdErr;
    }

    /**
     * run external application
     *
     * @param app           - app name (full path necessary if app isn't in env PATH)
     * @param isLongRunning - true to allow app to run; false to kill it if still running after 30 seconds
     */
    public AppResult runApp(String app, boolean isLongRunning, String... args) {
        AppResult result = new AppResult();
        Timer timer = new Timer();
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(app);
            if (args != null) {
                commandList.addAll(Arrays.asList(args));
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(commandList);
            Process process = processBuilder.start();
            synchronized (processList) {
                processList.add(process);
            }

            int exitValue = 0;
            if (isLongRunning) {
                // wait until process exits
                exitValue = process.waitFor();
            } else {
                // only allow up to X seconds for process to finish
                boolean isExited = process.waitFor(30, TimeUnit.SECONDS);
                if (isExited) {
                    exitValue = process.exitValue();
                } else {
                    log.error("runScript: {}: NOT FINISHED: {}, args:{}", timer, app, GsonHelper.toJson(args));
                }
            }
            result.isSuccess = exitValue == 0;
            result.stdOut = readInputStream(process.getInputStream());
            result.stdErr = readInputStream(process.getErrorStream());
            synchronized (processList) {
                processList.remove(process);
            }

            if (result.isSuccess) {
                if (log.isTraceEnabled())
                    log.trace("runScript: DONE: {}: {}, STDOUT:{}, STDERR:{}", timer, app, GsonHelper.toJson(result.stdOut), GsonHelper.toJson(result.stdErr));
            } else {
                log.error("runScript: {}: ERROR: {}, rc:{}, STDOUT:{}, STDERR:{}", timer, app, exitValue, GsonHelper.toJson(result.stdOut), GsonHelper.toJson(result.stdErr));
            }
            return result;
        } catch (Exception e) {
            result.isSuccess = false;
            result.stdErr = List.of("Exception: " + e.getMessage());
            log.error("runScript: {}: Exception: {}, {}, {}", timer, app, e.getClass().getSimpleName(), e.getMessage());
        }
        return result;
    }

    private List<String> readInputStream(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        List<String> resultList = new ArrayList<>();
        try {
            while (true) {
                line = reader.readLine();
                if (line == null) break;
                else if (line.isEmpty()) continue;
                //log.debug("runScript: {}", line);
                resultList.add(line);
            }
            reader.close();
        } catch (IOException e) {
            log.error("readInputStream: Exception: {}", e.getMessage());
        }
        return resultList;
    }
}
