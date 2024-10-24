package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.ui.dialog.ConnectDialog;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.utils.*;
import com.jpage4500.devicemanager.utils.Timer;
import se.vidstige.jadb.*;
import se.vidstige.jadb.managers.PackageManager;
import se.vidstige.jadb.managers.PropertyManager;

import javax.imageio.ImageIO;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DeviceManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceManager.class);

    // adb commands
    public static final String COMMAND_DEVICE_NICKNAME = "settings get global device_name";
    public static final String COMMAND_SERVICE_PHONE1 = "service call iphonesubinfo 15 s16 com.android.shell";
    public static final String COMMAND_SERVICE_PHONE2 = "service call iphonesubinfo 12 s16 com.android.shell";
    public static final String COMMAND_SERVICE_IMEI = "service call iphonesubinfo 1 s16 com.android.shell";
    public static final String COMMAND_REBOOT = "reboot";
    public static final String COMMAND_DISK_SIZE = "df";
    public static final String COMMAND_LIST_PROCESSES = "ps -A -o PID,ARGS"; // | grep u0_
    public static final String COMMAND_DUMPSYS_BATTERY = "dumpsys battery";

    public static final String APP_SCRCPY = "scrcpy";
    public static final String APP_ADB = "adb";

    // scripts that app will run
    private static final String SCRIPT_START_SERVER = "start-server";
    private static final String SCRIPT_TERMINAL = "terminal";
    private static final String SCRIPT_MIRROR = "mirror";
    private static final String SCRIPT_RECORD = "record-screen";
    private static final String SCRIPT_CUSTOM = "run-custom";

    public static final String FILE_CUSTOM_PROP = "/sdcard/android_device_manager.properties";

    public static final String ERR_ROOT_NOT_AVAILABLE = "root not available";
    public static final String ERR_PERMISSION_DENIED = "permission denied";
    public static final String ERR_NOT_A_DIRECTORY = "Not a directory";
    public static final String SHELL_BOOT_COMPLETED = "getprop sys.boot_completed";

    private static volatile DeviceManager instance;

    private final List<Device> deviceList;
    private final String tempFolder;
    private final List<Process> processList;

    private final ExecutorService commandExecutorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> deviceRefreshRuture;

    private final AtomicBoolean isLogging = new AtomicBoolean(false);

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

        // single device was removed
        void handleDeviceRemoved(Device device);

        void handleException(Exception e);
    }

    public void connectAdbServer(boolean allowRetry, DeviceManager.DeviceListener listener) {
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
                        // change all devices to offline
                        for (Device device : deviceList) device.isOnline = false;
                        listener.handleException(e);
                    }
                }).run();
            } catch (Exception e) {
                log.error("connectAdbServer: Exception: {}", e.getMessage());
                // likley because adb server isn't running.. try to start it now
                startServer((isSuccess, error) -> {
                    if (isSuccess && allowRetry) connectAdbServer(false, listener);
                    else {
                        // change all devices to offline
                        for (Device device : deviceList) device.isOnline = false;
                        listener.handleException(e);
                    }
                });
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
        for (Iterator<Device> iterator = deviceList.iterator(); iterator.hasNext(); ) {
            Device device = iterator.next();
            boolean isFound = false;
            for (JadbDevice jadbDevice : devices) {
                if (device.serial.equals(jadbDevice.getSerial())) {
                    isFound = true;
                    break;
                }
            }
            if (!isFound) {
                if (log.isTraceEnabled()) log.trace("handleDeviceUpdate: DEVICE_OFFLINE: {}", device.getDisplayName());
                iterator.remove();
                // -- DEVICE REMOVED --
                device.isOnline = false;
                device.lastUpdateMs = System.currentTimeMillis();
                listener.handleDeviceRemoved(device);
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
                        log.trace("handleDeviceUpdate: ONLINE: {}", addedDevice.serial);
                        addedDevice.isOnline = true;
                        addedDevice.status = null;
                        addedDevice.lastUpdateMs = System.currentTimeMillis();
                        listener.handleDeviceUpdated(addedDevice);
                        fetchDeviceDetails(addedDevice, true, listener);
                    } else {
                        log.debug("handleDeviceUpdate: NOT_READY: {} -> {}", addedDevice.serial, state);
                        addedDevice.status = state.name();
                        listener.handleDeviceUpdated(addedDevice);
                    }
                } catch (Exception e) {
                    String errMsg = e.getMessage();
                    //  command failed: device offline
                    //  command failed: device still authorizing
                    //  command failed: device unauthorized.
                    //  This adb server's $ADB_VENDOR_KEYS is not set
                    //  Try 'adb kill-server' if that seems wrong.
                    //  Otherwise check for a confirmation dialog on your device.
                    log.debug("handleDeviceUpdate: NOT_READY_EXCEPTION: {} -> {}", addedDevice.serial, errMsg);
                    addedDevice.status = errMsg;
                    // TODO: check error message before setting device to offline?
                    addedDevice.isOnline = false;
                    listener.handleDeviceUpdated(addedDevice);
                }
            }

            // run periodic task to update device state
            if (deviceRefreshRuture == null) {
                deviceRefreshRuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
                    //log.trace("handleDeviceUpdate: REFRESH");
                    for (Device device : deviceList) {
                        fetchDeviceDetails(device, false, listener);
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
        if (!device.isOnline) return;
        commandExecutorService.submit(() -> {
            Timer timer = new Timer();
            // show device as 'busy'
            device.busyCounter.incrementAndGet();
            listener.handleDeviceUpdated(device);

            // NOTE: if device just restarted, the initial fullRefresh will fail so try again next time
            if (fullRefresh || device.nickname == null) {
                // -- device nickname --
                fetchNickname(device);

                // -- phone number --
                String phone = runShellServiceCall(device, COMMAND_SERVICE_PHONE1);
                if (TextUtils.notEmpty(phone)) device.phone = phone;
                if (TextUtils.isEmpty(device.phone)) {
                    // alternative way of getting phone number
                    phone = runShellServiceCall(device, COMMAND_SERVICE_PHONE2);
                    if (TextUtils.notEmpty(phone)) device.phone = phone;
                }

                // -- IMEI --
                String imei = runShellServiceCall(device, COMMAND_SERVICE_IMEI);
                if (TextUtils.notEmpty(imei)) device.imei = imei;

                // -- device properties (model, OS) --
                try {
                    device.propMap = new PropertyManager(device.jadbDevice).getprop();
                } catch (Exception e) {
                    log.error("fetchDeviceDetails: PROP Exception:{}", e.getMessage());
                }

                // -- custom properties --
                fetchCustomProperties(device);
            }

            // -- disk free space --
            fetchFreeDiskSpace(device);

            // -- version of installed apps --
            fetchInstalledAppVersions(device);

            // -- battery level, charging status, etc --
            fetchBatteryInfo(device);

            fetchDeviceBooted(device);

            device.lastUpdateMs = System.currentTimeMillis();

            if (fullRefresh) {
                if (log.isTraceEnabled()) log.trace("fetchDeviceDetails: FULL_REFRESH:{}: {}", timer, GsonHelper.toJson(device));
                // keep track of wireless devices
                ConnectDialog.addWirelessDevice(device);
            } else {
                if (log.isTraceEnabled()) log.trace("fetchDeviceDetails: REFRESH:{}: {}", timer, GsonHelper.toJson(device));
            }
            int busyCount = device.busyCounter.decrementAndGet();
            if (busyCount == 0) listener.handleDeviceUpdated(device);

            // if devicce isn't fully booted yet, schedule another refresh
            if (!device.isBooted) {
                scheduledExecutorService.schedule(() -> {
                    log.trace("fetchDeviceDetails: try again for {}", device.getDisplayName());
                    fetchDeviceDetails(device, true, listener);
                }, 10, TimeUnit.SECONDS);
            }
        });
    }

    /**
     * check if device is fully booted
     */
    private void fetchDeviceBooted(Device device) {
        ShellResult result = runShell(device, SHELL_BOOT_COMPLETED);
        device.isBooted = (result.isSuccess && TextUtils.equals(result.getResult(0), "1"));
    }

    private void fetchBatteryInfo(Device device) {
        ShellResult result = runShell(device, COMMAND_DUMPSYS_BATTERY);
        for (String batteryLine : result.resultList) {
            String[] batteryArr = batteryLine.split(": ", 2);
            if (batteryArr.length < 2) continue;
            String name = batteryArr[0].trim();
            String value = batteryArr[1].trim();
            switch (name) {
                case "level":
                    //  level: 100
                    try {
                        int level = Integer.parseInt(value);
                        // some Android TV devices list battery level as 0
                        if (level > 0 && level <= 100) {
                            device.batteryLevel = level;
                        }
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
    }

    private void fetchInstalledAppVersions(Device device) {
        List<String> customApps = SettingsDialog.getCustomApps();
        for (String customApp : customApps) {
            String versionName = getAppVersion(device, customApp);
            if (device.customAppVersionList == null) device.customAppVersionList = new HashMap<>();
            device.customAppVersionList.put(customApp, versionName);
        }
    }

    private void fetchFreeDiskSpace(Device device) {
        ShellResult result = runShell(device, COMMAND_DISK_SIZE);
        if (result.isSuccess && !result.resultList.isEmpty()) {
            // only interested in last line
            String last = result.resultList.get(result.resultList.size() - 1);
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
    }

    private void fetchCustomProperties(Device device) {
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

    private void fetchNickname(Device device) {
        ShellResult result = runShell(device, COMMAND_DEVICE_NICKNAME);
        if (result.isSuccess && !result.resultList.isEmpty()) {
            String nickname = result.resultList.get(0).trim();
            // look for error: "cmd: Can't find service: settings"
            if (TextUtils.containsIgnoreCase(nickname, "Can't find service")) {
                log.trace("fetchNickname: ERROR: {}", nickname);
                return;
            }
            device.nickname = nickname;
        }
    }

    /**
     * run a 'shell service call ..." command and parse the results into a String
     */
    private String runShellServiceCall(Device device, String command) {
        ShellResult result = runShell(device, command);
        if (!result.isSuccess) return null;
        // look for errors like:
        // "service: Service iphonesubinfo does not exist"
        String resultDesc = TextUtils.join(result.resultList, ",");
        if (TextUtils.containsAny(resultDesc, true, "does not exist")) {
            log.trace("runShellServiceCall: {}: ERROR: {}", command, resultDesc);
            result.isSuccess = false;
            return null;
        }

        // Result: Parcel(
        // 0x00000000: 00000000 0000000b 00350031 00300034 '........1.2.2.2.'
        // 0x00000010: 00310039 00390034 00310032 00000034 '3.3.3.4.4.4.4...')
        StringBuilder sb = null;
        if (result.resultList.size() > 1) {
            for (int i = 1; i < result.resultList.size(); i++) {
                String line = result.resultList.get(i);
                int stPos = line.indexOf('\'');
                if (stPos >= 0) {
                    int endPos = line.indexOf('\'', stPos + 1);
                    if (endPos >= 0) {
                        line = line.substring(stPos + 1, endPos);
                        line = line.replaceAll("[^-?0-9]+", "");
                        if (sb == null) sb = new StringBuilder();
                        sb.append(line);
                    }
                }
            }
        }
        //log.trace("runShellServiceCall: RESULTS: {}", result);
        return sb != null ? sb.toString() : null;
    }

    /**
     * @return copy of device list
     */
    public List<Device> getDevices() {
        synchronized (deviceList) {
            return new ArrayList<>(deviceList);
        }
    }

    public static class ShellResult {
        boolean isSuccess;
        List<String> resultList;

        public String getResult(int index) {
            if (resultList != null && resultList.size() > index) return resultList.get(index);
            else return null;
        }

        @Override
        public String toString() {
            return "success: " + isSuccess + ", results: " + GsonHelper.toJson(resultList);
        }
    }

    /**
     * run a shell command and return multi-line output
     */
    private ShellResult runShell(Device device, String command) {
        ShellResult result = new ShellResult();
        result.resultList = new ArrayList<>();
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
                result.resultList.add(line);
            }
            result.isSuccess = true;
            //log.trace("runShell: cmd:{}, {}", command, GsonHelper.toJson(result.resultList));
        } catch (Exception e) {
            log.error("runShell: cmd:{}, Exception: {}", command, e.getMessage());
            result.isSuccess = false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return result;
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
     * run scrcpy app to mirror device
     */
    public void mirrorDevice(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            log.debug("mirrorDevice: {}", device.getDisplayName());
            AppResult appResult = null;
            File scriptFile = getScriptFile(SCRIPT_MIRROR);
            if (scriptFile != null) {
                appResult = runApp(scriptFile.getAbsolutePath(), true, device.serial, device.getDisplayName());
            }
            if (appResult == null || !appResult.isSuccess) {
                String app = findApp(APP_SCRCPY);
                if (app == null) app = APP_SCRCPY;
                int port = Utils.getRandomNumber(2000, 65000);
                // NOTE: adb must be in PATH (or ADB env variable set)
                appResult = runApp(app, true, "-s", device.serial,
                        "-p", String.valueOf(port),
                        "--window-title", device.getDisplayName(),
                        "--show-touches", "--stay-awake", "--no-audio");
            }

            // TODO: figure out how to determine if scrcpy was run successfully..
            // - scrcpy will log to stderr even when successful
            listener.onTaskComplete(appResult.isSuccess, TextUtils.join(appResult.stdErr, "\n"));
        });
    }

    /**
     * run scrcpy app to mirror device
     */
    public void recordScreen(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            String downloadFolder = Utils.getDownloadFolder();
            String prefix = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            File file = FileUtils.findAvailableFile(downloadFolder, prefix, ".mp4");
            if (file == null) return;
            log.debug("recordScreen: {}, file:{}", device.getDisplayName(), file.getAbsolutePath());
            AppResult appResult = null;
            File scriptFile = getScriptFile(SCRIPT_RECORD);
            if (scriptFile != null) {
                appResult = runApp(scriptFile.getAbsolutePath(), true, device.serial, file.getAbsolutePath(), device.getDisplayName());
            }
            if (appResult == null || !appResult.isSuccess) {
                // TODO: run scrcpy directly
                appResult = new AppResult();
//                String app = findApp(APP_SCRCPY);
//                if (app == null) app = APP_SCRCPY;
//                int port = Utils.getRandomNumber(2000, 65000);
//                // NOTE: adb must be in PATH (or ADB env variable set)
//                appResult = runApp(app, true, "-s", device.serial,
//                        "-p", String.valueOf(port),
//                        "--window-title", device.getDisplayName(),
//                        "--show-touches", "--stay-awake", "--no-audio");
            }
            if (appResult.isSuccess) {
                // open with default viewer
                Utils.openFile(file);
            }

            // TODO: figure out how to determine if scrcpy was run successfully..
            // - scrcpy will log to stderr even when successful
            listener.onTaskComplete(appResult.isSuccess, TextUtils.join(appResult.stdErr, "\n"));
        });
    }

    /**
     * try to locate app in system environment or some common locations
     *
     * @return full path or null if not found
     */
    private String findApp(String app) {
        String path = System.getenv("PATH");
        //log.trace("findApp: PATH:{}", path);
        String[] pathArr = path.split(File.pathSeparator);
        // Windows-only - add .exe to app
        if (Utils.isWindows() && !TextUtils.endsWith(".exe")) app += ".exe";
        for (String p : pathArr) {
            String fullPath = checkFile(p, app);
            if (fullPath != null) return fullPath;
        }
        // try some other common locations
        String[] arr = new String[]{};
        if (Utils.isMac()) {
            arr = new String[]{
                    "/opt/homebrew/bin",
                    "/usr/local/bin",
            };
        }
        for (String s : arr) {
            String fullPath = checkFile(s, app);
            if (fullPath != null) return fullPath;
        }
        log.trace("findApp: NOT_FOUND: {}", app);
        return null;
    }

    private String checkFile(String path, String app) {
        File f = new File(path, app);
        if (f.exists()) {
            log.trace("checkFile: GOT:{}", f.getAbsolutePath());
            return f.getAbsolutePath();
        }
        return null;
    }

    public void captureScreenshot(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            String downloadFolder = Utils.getDownloadFolder();
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
                Utils.openFile(outputfile);
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
                log.error("setProperty: {}, {}={}, Exception:{}", device.serial, key, value, e.getMessage());
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
                if (listener != null) listener.onTaskComplete(false, e.getMessage());
            }
        });
    }

    private void copyFilesInternal(Device device, List<File> fileList, String dest, ProgressListener progressListener) {
        for (File file : fileList) {
            String filename = file.getName();
            String destFilename = dest + "/" + filename;
            progressListener.onProgress(0, 0, filename);
            if (file.isDirectory()) {
                ShellResult result = runShell(device, "mkdir \"" + destFilename + "\"");
                log.trace("copyFilesInternal: FOLDER: {}: {}", destFilename, result);
                // copy all children
                File[] childrenArr = file.listFiles();
                if (childrenArr != null) {
                    copyFilesInternal(device, List.of(childrenArr), destFilename, progressListener);
                }
            } else {
                log.trace("copyFilesInternal: FILE: {}", destFilename);
                try {
                    RemoteFile remoteFile = new RemoteFileRecord(dest, filename, 0, 0, 0);
                    device.jadbDevice.push(file, remoteFile);
                } catch (Exception e) {
                    log.error("copyFile: {} -> {}, Exception:{}", file.getAbsolutePath(), dest, e.getMessage());
                }
            }
        }
    }

    public void copyFiles(Device device, List<File> fileList, String dest, ProgressListener progressListener, TaskListener listener) {
        commandExecutorService.submit(() -> {
            // come up with total files to copy
            FileUtils.FileStats stats = FileUtils.getFileStats(fileList);
            AtomicInteger count = new AtomicInteger();
            copyFilesInternal(device, fileList, dest, (numCompleted, numTotal, msg) -> {
                int i = count.incrementAndGet();
                progressListener.onProgress(i, stats.numTotal, msg);
            });
            listener.onTaskComplete(true, null);
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
            ShellResult result = runShell(device, customCommand);
            boolean isSuccess = result.isSuccess;
            log.trace("runCustomCommand: DONE: success:{}, {}", isSuccess, GsonHelper.toJson(result.resultList));
            String displayStr = TextUtils.join(result.resultList, "\n");
            // check if command runs but fails
            if (TextUtils.containsIgnoreCase(displayStr, "inaccessible or not found")) {
                isSuccess = false;
            }
            if (listener != null) listener.onTaskComplete(isSuccess, displayStr);
        });
    }

    public void openTerminal(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            File scriptFile = getScriptFile(SCRIPT_TERMINAL);
            runApp(scriptFile.getAbsolutePath(), true, device.serial);
        });
    }

    /**
     * start ADB server
     */
    public void startServer(TaskListener listener) {
        commandExecutorService.submit(() -> {
            AppResult appResult = null;
            File scriptFile = getScriptFile(SCRIPT_START_SERVER);
            if (scriptFile != null) {
                appResult = runApp(scriptFile.getAbsolutePath(), false);
            }
            if (appResult == null || !appResult.isSuccess) {
                String app = findApp(APP_ADB);
                // NOTE: adb must be in PATH (or ADB env variable set)
                appResult = runApp(app, true, "start-server");
            }
            listener.onTaskComplete(appResult.isSuccess, GsonHelper.toJson(appResult.stdErr));
        });
    }

    public interface DeviceFileListener {
        void handleFiles(List<DeviceFile> fileList, String error);
    }

    public void listFiles(Device device, String path, boolean useRoot, DeviceFileListener listener) {
        commandExecutorService.submit(() -> {
            try {
                String safePath = path;
                // make sure folder ends with "/"
                if (!TextUtils.endsWith(safePath, "/")) safePath += "/";
                if (safePath.indexOf(' ') > 0) {
                    safePath = "'" + safePath + "'";
                }
                log.trace("listFiles: {} {}", safePath, useRoot ? "(ROOT)" : "");
                String command = "ls -alZ " + safePath;
                if (useRoot) command = "su -c " + command;
                ShellResult result = runShell(device, command);
                List<DeviceFile> fileList = new ArrayList<>();
                for (int i = 0; i < result.resultList.size(); i++) {
                    String dir = result.resultList.get(i);
                    DeviceFile file = DeviceFile.fromEntry(dir);
                    if (file != null) fileList.add(file);
                    else if (i == 0) {
                        // not a valid file/dir listing; check for known errors
                        if (TextUtils.contains(dir, "su:")) {
                            log.debug("listFiles: NO_ROOT:{}", dir);
                            listener.handleFiles(null, ERR_ROOT_NOT_AVAILABLE);
                            return;
                        } else if (TextUtils.containsAny(dir, true, "permission denied")) {
                            log.debug("listFiles: NO_PERMISSION:{}", dir);
                            listener.handleFiles(null, ERR_PERMISSION_DENIED);
                            return;
                        } else if (TextUtils.containsAny(dir, true, "Not a directory", "No such file or directory")) {
                            log.debug("listFiles: NOT_DIR:{}, {}", dir, GsonHelper.toJson(result.resultList));
                            listener.handleFiles(null, ERR_NOT_A_DIRECTORY);
                            return;
                        }
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

    public interface ProgressListener {
        void onProgress(int numCompleted, int numTotal, String msg);
    }

    public interface TaskListener {
        void onTaskComplete(boolean isSuccess, String error);
    }

    public void downloadFile(Device device, String path, DeviceFile file, File saveFile, TaskListener listener) {
        log.debug("downloadFile: {}/{} -> {}", path, file.name, saveFile.getAbsolutePath());
        commandExecutorService.submit(() -> downloadFileInternal(device, path, file, saveFile));
    }

    /**
     * recursive method to download a file or folder
     */
    private void downloadFileInternal(Device device, String path, DeviceFile file, File saveFile) {
        if (file.isDirectory) {
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
            String command = "rm -rf \"" + path + "/" + file.name + "\"";
            ShellResult result = runShell(device, command);
            log.debug("deleteFile: {} -> {}", command, result);
            // TODO: determine success/fail
            listener.onTaskComplete(true, null);
        });
    }

    public void createFolder(Device device, String path, TaskListener listener) {
        commandExecutorService.submit(() -> {
            ShellResult result = runShell(device, "mkdir \"" + path + "\"");
            log.debug("createFolder: {} -> {}", path, result);
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
            String command = "input text \"" + text + "\"";
            ShellResult result = runShell(device, command);
            log.trace("sendInputText: {} -> {}", text, result);
            // assume
            if (listener != null) listener.onTaskComplete(true, null);
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
                    else if (startTime != null && (logEntry.timestamp == null || startTime > logEntry.timestamp)) {
                        //log.trace("startLogging: too old: {} ({}) vs {}", logEntry.timestamp, logEntry.date, startTime);
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
        ShellResult result = runShell(device, COMMAND_LIST_PROCESSES);
        // 7617 com.android.traceur
        // 7677 [csf_sync_update]
        Map<String, String> pidMap = new HashMap<>();
        for (String line : result.resultList) {
            String[] lineArr = line.trim().split(" ");
            if (lineArr.length < 2) continue;
            String pid = lineArr[0];
            String app = lineArr[1];
            int atPos = app.indexOf('@');
            if (atPos > 0) {
                app = app.substring(0, atPos);
            }
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

        if (deviceRefreshRuture != null) deviceRefreshRuture.cancel(true);
        commandExecutorService.shutdownNow();
        scheduledExecutorService.shutdownNow();
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
        //File.createTempFile(name);
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

        @Override
        public String toString() {
            return GsonHelper.toJson(this);
        }
    }

    public void runCustomScript(TaskListener listener, String path, String... args) {
        commandExecutorService.submit(() -> {
            log.debug("runCustomScript: {}", path);
            AppResult appResult;
            File scriptFile = getScriptFile(SCRIPT_CUSTOM);
            if (scriptFile != null) {
                List<String> argsList = new ArrayList<>();
                argsList.add(path);
                argsList.addAll(Arrays.asList(args));
                appResult = runApp(scriptFile.getAbsolutePath(), true, argsList.toArray(new String[0]));
                String stdOut = TextUtils.join(appResult.stdOut, "\n");
                String stdErr = TextUtils.join(appResult.stdErr, "\n");
                listener.onTaskComplete(appResult.isSuccess, stdOut + "\n" + stdErr);
            } else {
                listener.onTaskComplete(false, "script not found: " + SCRIPT_CUSTOM);
            }
        });
    }

    /**
     * run external application
     *
     * @param app           - app name (full path necessary if app isn't in env PATH)
     * @param isLongRunning - true to allow app to run; false to kill it if still running after 30 seconds
     */
    public AppResult runApp(String app, boolean isLongRunning, String... args) {
        AppResult result = new AppResult();
        if (app == null) return result;
        Timer timer = new Timer();
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(app);
            if (args != null) {
                commandList.addAll(Arrays.asList(args));
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            // when running SCRCPY, make sure ADB env var is set
            updateProcessBuilderEnvironment(app, processBuilder);
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
                    log.error("runApp: {}: NOT FINISHED: {}, args:{}", timer, app, GsonHelper.toJson(args));
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
                    log.trace("runApp: SUCCESS: {}: {}, STDOUT:{}, STDERR:{}", timer, app, GsonHelper.toJson(result.stdOut), GsonHelper.toJson(result.stdErr));
            } else {
                log.error("runApp: {}: ERROR: {}, rc:{}, STDOUT:{}, STDERR:{}", timer, app, exitValue, GsonHelper.toJson(result.stdOut), GsonHelper.toJson(result.stdErr));
            }
            return result;
        } catch (Exception e) {
            result.isSuccess = false;
            result.stdErr = List.of("Exception: " + e.getMessage());
            log.error("runApp: {}: Exception: {}, {}, {}", timer, app, e.getClass().getSimpleName(), e.getMessage());
        }
        return result;
    }

    private void updateProcessBuilderEnvironment(String app, ProcessBuilder processBuilder) {
        // only interested in scrcpy for now
        if (!TextUtils.containsAny(app, true, APP_SCRCPY)) return;

        Map<String, String> environment = processBuilder.environment();
        String path = environment.get("PATH");
        String adbPath = environment.get("ADB");
        log.trace("runApp: ADB:{}, PATH:{}", adbPath, path);

        if (TextUtils.isEmpty(adbPath)) {
            adbPath = findApp(APP_ADB);
            if (adbPath != null) {
                log.trace("updateProcessBuilderEnvironment: ADB={}", adbPath);
                environment.put("ADB", adbPath);
            }
        }
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

    public interface InstalledAppListener {
        void onComplete(HashSet<String> appSet);
    }

    /**
     * fetch all installed apps (package names)
     */
    public void getInstalledApps(Device device, InstalledAppListener listener) {
        commandExecutorService.submit(() -> {
            try {
                HashSet<String> appSet = device.jadbDevice.listInstalledPackages();
                listener.onComplete(appSet);
            } catch (Exception e) {
                log.error("getInstalledApps: {}", e.getMessage());
                listener.onComplete(null);
            }
        });
    }

    public interface InstalledAppVersionListener {
        void onComplete(String version);
    }

    /**
     * fetch version for a given app package
     */
    public void fetchAppVersion(Device device, String appPkg, InstalledAppVersionListener listener) {
        commandExecutorService.submit(() -> {
            String appVersion = getAppVersion(device, appPkg);
            listener.onComplete(appVersion);
        });
    }

    private String getAppVersion(Device device, String appPkg) {
        // shell dumpsys package $PACKAGE | grep versionName | sed 's/    versionName=//')
        ShellResult result = runShell(device, "dumpsys package " + appPkg);
        for (String appLine : result.resultList) {
            // "    versionName=24.05.16.160",
            int index = appLine.indexOf("versionName=");
            if (index > 0) {
                String versionName = appLine.substring(index + "versionName=".length());
                log.trace("getAppVersion: {} -> {}", appPkg, versionName);
                return versionName;
            }
        }
        return null;
    }
}
