package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.RemoteClientInfo;
import com.jpage4500.devicemanager.ui.RemoteScreenWindow;
import com.jpage4500.devicemanager.ui.dialog.ConnectDialog;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.utils.*;
import com.jpage4500.devicemanager.utils.Timer;
import se.vidstige.jadb.*;
import se.vidstige.jadb.managers.PackageManager;
import se.vidstige.jadb.managers.PropertyManager;

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
    public static final String COMMAND_DISK_SIZE = "df /data";
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

    // how frequently to update logs
    public static final int LOG_INTERVAL_MS = 100;
    // how frequently to refresh device list
    public static final int DEVICE_REFRESH_MINS = 60;

    public static final String CUSTOM_KEY_VERSION = "VER";
    public static final String CUSTOM_KEY_PROP = "PROP";
    public static final String CUSTOM_KEY_QUERY = "QUERY";
    public static final String QUERY_ROW_0 = "Row: 0 ";

    private static volatile DeviceManager instance;

    private DeviceListener deviceListener;
    private final List<Device> deviceList;
    private final String tempFolder;
    private final List<Process> processList;

    // thread pool for running commands (screen mirror, terminal, etc)
    private final ExecutorService commandExecutorService;
    // thread pool for fetching device details
    private final ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> deviceRefreshRuture;

    private final Map<String, AtomicBoolean> loggingStateMap = new HashMap<>();
    private final List<String> queuedDetailList = new ArrayList<>();

    private JadbConnection connection;

    // remote connection manager
    private RemoteConnectionManager remoteConnectionManager;
    // remote server manager
    private RemoteServerManager remoteServerManager;

    public interface DeviceListener {
        // device list was refreshed
        void handleDevicesUpdated(List<Device> deviceList);

        // single device was updated
        void handleDeviceUpdated(Device device);

        // single device was removed
        void handleDeviceRemoved(Device device);

        void handleException(Exception e);
    }

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
        scheduledExecutorService = Executors.newScheduledThreadPool(10);

        tempFolder = Utils.getTempFolder();
        copyResourcesToFiles();
    }

    public void initialize(DeviceListener listener) {
        this.deviceListener = listener;

        // remote connection manager
        remoteConnectionManager = new RemoteConnectionManager(new RemoteConnectionManager.RemoteConnectionListener() {
            @Override
            public void onRemoteConnection(RemoteConnection connection) {
                List<Device> deviceList = getDeviceForConnection(connection);
                deviceList.forEach(device -> {
                    device.isOnline = true;
                });
                notifyDevicesUpdated();
            }

            @Override
            public void onRemoteConnectionLost(RemoteConnection connection) {
                // mark remote devices as offline
                List<Device> deviceList = getDeviceForConnection(connection);
                deviceList.forEach(device -> device.isOnline = false);
                // TODO: save this logic.. will need to remove devices eventually
                // remove devices from this server
//                synchronized (deviceList) {
//                    deviceList.removeIf(d -> d.remoteConnection == connection);
//                }
                notifyDevicesUpdated();
            }

            @Override
            public void onRemoteDevicesUpdated(RemoteConnection connection, List<Device> devices) {
                // merge remote devices into device list
                synchronized (deviceList) {
                    // TODO: update instead of replace
                    // remove old devices from this server
                    deviceList.removeIf(device -> device.remoteConnection == connection);
                    // add new devices
                    deviceList.addAll(devices);
                }
                notifyDevicesUpdated();
            }
        });

        // remote server manager (auto-starts if previously enabled)
        remoteServerManager = new RemoteServerManager(new RemoteServerManager.ServerListener() {
            @Override
            public void onServerStarted(int port) {

            }

            @Override
            public void onServerStopped() {

            }

            @Override
            public void onClientConnected(RemoteClientInfo client) {

            }

            @Override
            public void onClientDisconnected(RemoteClientInfo client) {

            }

            @Override
            public void onError(Exception e) {

            }
        });
    }

    private void notifyDevicesUpdated() {
        if (deviceListener != null) {
            deviceListener.handleDevicesUpdated(getDevices());
        }
    }

    private List<Device> getDeviceForConnection(RemoteConnection connection) {
        List<Device> list = new ArrayList<>();
        synchronized (deviceList) {
            for (Device device : deviceList) {
                if (device.remoteConnection == connection) {
                    list.add(device);
                }
            }
        }
        return list;
    }

    public void connectAdbServer(boolean allowRetry) {
        connection = new JadbConnection();
        commandExecutorService.submit(() -> {
            try {
                String hostVersion = connection.getHostVersion();
                log.debug("connectAdbServer: v:{}", hostVersion);
                connection.createDeviceWatcher(new DeviceDetectionListener() {
                    @Override
                    public void onDetect(List<JadbDevice> devices) {
                        handleDeviceUpdate(devices);
                    }

                    @Override
                    public void onException(Exception e) {
                        log.error("connectAdbServer: onException: {}", e.getMessage());
                        // change all devices to offline
                        synchronized (deviceList) {
                            deviceList.forEach(device -> {
                                if (device.remoteConnection == null) device.isOnline = false;
                            });
                        }
                        if (deviceListener != null) deviceListener.handleException(e);
                    }
                }).run();
            } catch (Exception e) {
                log.error("connectAdbServer: Exception: {}", e.getMessage());
                // likley because adb server isn't running.. try to start it now
                startServer((isSuccess, error) -> {
                    if (isSuccess && allowRetry) connectAdbServer(false);
                    else {
                        // change all devices to offline
                        synchronized (deviceList) {
                            deviceList.forEach(device -> {
                                if (device.remoteConnection == null) device.isOnline = false;
                            });
                        }
                        if (deviceListener != null) deviceListener.handleException(e);
                    }
                });
            }
        });
    }

    /**
     * called when a device is added/updated/removed
     * NOTE: run on background thread
     */
    private void handleDeviceUpdate(List<JadbDevice> devices) {
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
        synchronized (deviceList) {
            for (Iterator<Device> iterator = deviceList.iterator(); iterator.hasNext(); ) {
                Device device = iterator.next();
                // ignore remote devices
                if (device.remoteConnection != null) continue;
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
                    if (deviceListener != null) deviceListener.handleDeviceRemoved(device);
                }
            }
        }

        if (!addedDeviceList.isEmpty()) {
            // notify listener that device list changed
            notifyDevicesUpdated();

            for (Device addedDevice : addedDeviceList) {
                // fetch more details for these devices
                try {
                    JadbDevice.State state = addedDevice.jadbDevice.getState();
                    if (state == JadbDevice.State.Device) {
                        log.trace("handleDeviceUpdate: ONLINE: {}", addedDevice.serial);
                        addedDevice.isOnline = true;
                        addedDevice.status = null;
                        addedDevice.lastUpdateMs = System.currentTimeMillis();
                        notifyDeviceUpdated(addedDevice);
                        fetchDeviceDetails(addedDevice, true);
                    } else {
                        log.debug("handleDeviceUpdate: NOT_READY: {} -> {}", addedDevice.serial, state);
                        addedDevice.status = state.name();
                        notifyDeviceUpdated(addedDevice);
                    }
                } catch (Exception e) {
                    String errMsg = e.getMessage();
                    //  command failed: device offline
                    //  command failed: device still authorizing
                    //  command failed: device unauthorized.
                    //  this adb server's $ADB_VENDOR_KEYS is not set
                    //  try 'adb kill-server' if that seems wrong.
                    //  otherwise check for a confirmation dialog on your device.
                    log.debug("handleDeviceUpdate: NOT_READY_EXCEPTION: {} -> {}", addedDevice.serial, errMsg);
                    addedDevice.status = errMsg;
                    // TODO: check error message before setting device to offline?
                    addedDevice.isOnline = false;
                    notifyDeviceUpdated(addedDevice);
                }
            }

            if (deviceRefreshRuture == null) {
                updateRefreshTime();
            }
        }
    }

    public void updateRefreshTime() {
        if (deviceRefreshRuture != null) {
            deviceRefreshRuture.cancel(true);
        }
        int refreshTimeMins = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_REFRESH_TIME_MINS, DeviceManager.DEVICE_REFRESH_MINS);
        // run periodic task to update device state
        log.debug("updateRefreshTime: schedule refresh every {} mins", refreshTimeMins);
        deviceRefreshRuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            refreshDevices(false);
        }, refreshTimeMins, refreshTimeMins, TimeUnit.MINUTES);
    }

    public void refreshDevices(boolean fullRefresh) {
        synchronized (deviceList) {
            for (Device device : deviceList) {
                if (device.remoteConnection == null) {
                    fetchDeviceDetails(device, fullRefresh);
                }
            }
        }
    }

    /**
     * fetch device details (phone #, name, model, disk space, battery level, etc)
     *
     * @param fullRefresh - true to fetch everything; false to only fetch values that would change often (battery, disk)
     */
    public void fetchDeviceDetails(Device device, boolean fullRefresh) {
        if (!device.isOnline) return;
        else if (!addDeviceToQueue(device)) return;

        scheduledExecutorService.submit(() -> {
            Timer timer = new Timer();
            // show device as 'busy'
            device.setBusy(true);
            notifyDeviceUpdated(device);

            // check if device is fully booted
            fetchDeviceBooted(device);
            if (!device.isBooted) {
                boolean isBusy = device.setBusy(false);
                if (!isBusy) notifyDeviceUpdated(device);

                // if device isn't fully booted yet, schedule another refresh
                scheduledExecutorService.schedule(() -> {
                    log.trace("fetchDeviceDetails: try again for {}", device.getDisplayName());
                    fetchDeviceDetails(device, true);
                }, 5, TimeUnit.SECONDS);

                removeDeviceFromQueue(device);
                return;
            }

            // NOTE: if device just restarted, the initial fullRefresh will fail so try again next time
            if (fullRefresh || device.nickname == null) {
                // -- device properties (model, OS) --
                try {
                    Map<String, String> propMap = new PropertyManager(device.jadbDevice).getprop();
                    device.parseProperties(propMap);
                } catch (Exception e) {
                    log.error("fetchDeviceDetails: PROP Exception:{}", e.getMessage());
                }

                // -- device nickname --
                fetchNickname(device);

                try {
                    // -- phone number --
                    // NOTE: there's no consistent way to get a phone number via adb
                    // - the best I've found is a script from: https://github.com/micro5k/microg-unofficial-installer/blob/main/utils/device-info.sh
                    String phone = runShellServiceCall(device, COMMAND_SERVICE_PHONE1);
                    if (TextUtils.length(phone) > 7) {
                        device.phone = phone;
                    } else {
                        // alternative way of getting phone number
                        phone = runShellServiceCall(device, COMMAND_SERVICE_PHONE2);
                        if (TextUtils.length(phone) > 7) device.phone = phone;
                    }

                    // -- IMEI --
                    String imei = runShellServiceCall(device, COMMAND_SERVICE_IMEI);
                    if (TextUtils.notEmpty(imei)) {
                        device.imei = imei;
                        notifyDeviceUpdated(device);
                    }
                } catch (Exception e) {
                    // not a phone (tablet, TV, etc)
                }

                // -- custom properties --
                fetchCustomProperties(device);
            }

            // -- disk free space --
            fetchFreeDiskSpace(device);

            // -- custom apps --
            fetchCustomColumns(device);

            // -- battery level, charging status, etc --
            fetchBatteryInfo(device);

            device.lastUpdateMs = System.currentTimeMillis();

            if (fullRefresh) {
                if (log.isTraceEnabled()) log.trace("fetchDeviceDetails: FULL_REFRESH:{}: {}", timer, GsonHelper.toJson(device));
                // keep track of wireless devices
                ConnectDialog.addWirelessDevice(device);
            } else {
                if (log.isTraceEnabled()) log.trace("fetchDeviceDetails: REFRESH:{}: {}", timer, GsonHelper.toJson(device));
            }
            boolean isBusy = device.setBusy(false);
            if (!isBusy) notifyDeviceUpdated(device);
            removeDeviceFromQueue(device);
        });
    }

    /**
     * prevent multiple fetches for same device
     *
     * @return true if added to queue and false if already queued
     */
    private boolean addDeviceToQueue(Device device) {
        synchronized (queuedDetailList) {
            // don't queue if already queued
            if (queuedDetailList.contains(device.serial)) {
                log.debug("addDeviceToQueue: ALREADY_QUEUED: {}", device.getDisplayName());
                return false;
            }
            queuedDetailList.add(device.serial);
        }
        return true;
    }

    private void removeDeviceFromQueue(Device device) {
        synchronized (queuedDetailList) {
            queuedDetailList.remove(device.serial);
        }
    }

    private void notifyDeviceUpdated(Device device) {
        if (deviceListener != null) {
            deviceListener.handleDeviceUpdated(device);
        }
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
                        if (level > 0 && level <= LOG_INTERVAL_MS) {
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
                    //  wireless powered: false
                    if (Boolean.parseBoolean(value)) device.powerStatus = Device.PowerStatus.POWER_WIRELESS;
                    break;
                case "Dock powered":
                    //  dock powered: false
                    if (Boolean.parseBoolean(value)) device.powerStatus = Device.PowerStatus.POWER_DOCK;
                    break;
            }
        }
        notifyDeviceUpdated(device);
    }

    private void fetchCustomColumns(Device device) {
        List<String> entryList = SettingsDialog.getCustomColumns();
        if (entryList.isEmpty()) return;

        // schedule these commands to be run after all other device details are fetched
        scheduledExecutorService.submit(() -> {
            Timer timer = new Timer();
            device.setBusy(true);
            notifyDeviceUpdated(device);
            int beforeSize = device.customAppVersionList != null ? device.customAppVersionList.size() : 0;

            // cache results from same/similar query URL
            Map<String, String> queryCache = new HashMap<>();

            for (String entry : entryList) {
                if (TextUtils.isEmpty(entry) || TextUtils.startsWithAny(entry, false, "#", "//")) continue;
                String[] entryArr = entry.split(":", 3);
                String label = entryArr.length >= 1 ? entryArr[0].trim() : entry;
                String type = entryArr.length >= 2 ? entryArr[1].trim() : CUSTOM_KEY_VERSION;
                String val = entryArr.length >= 3 ? entryArr[2].trim() : null;

                log.trace("fetchCustomColumns: label:{}, type:{}, val:{}", label, type, val);

                String value = null;
                if (TextUtils.equalsIgnoreCase(type, CUSTOM_KEY_VERSION)) {
                    value = getAppVersion(device, val);
                } else if (TextUtils.equalsIgnoreCase(type, CUSTOM_KEY_PROP)) {
                    ShellResult result = runShell(device, "getprop " + val);
                    //log.trace("fetchCustomColumns: {} -> {}", val, result);
                    if (result.isSuccess) {
                        value = result.getResult(0);
                    }
                } else if (TextUtils.equalsIgnoreCase(type, CUSTOM_KEY_QUERY)) {
                    String baseUrl = val;
                    String searchFor = null;
                    // allow for more complex queries like: "content://com.test.app/query#key"
                    int pos = TextUtils.indexOf(val, "#");
                    if (pos >= 0) {
                        baseUrl = val.substring(0, pos).trim();
                        searchFor = val.substring(pos + 1).trim();
                    }
                    String line = null;
                    if (!queryCache.containsKey(baseUrl)) {
                        //  adb shell content query --uri content://com.test.provider/queryForValue
                        //  Row: 0 key=value, key=value, key=value
                        ShellResult result = runShell(device, "content query --uri " + baseUrl);
                        if (result.isSuccess) {
                            line = result.getResult(0);
                            // key=value, key=value, key=value
                            pos = TextUtils.indexOf(line, QUERY_ROW_0);
                            if (pos >= 0) {
                                // remove "Row: 0 "
                                line = line.substring(pos + QUERY_ROW_0.length());
                                queryCache.put(baseUrl, line);
                            }
                        }
                    } else {
                        line = queryCache.get(baseUrl);
                    }
                    if (line == null) {
                        log.error("fetchCustomColumns: NOT_FOUND: {}, {}", baseUrl, device.getDisplayName());
                        continue;
                    }

                    //log.trace("fetchCustomColumns: {}", line);
                    String[] pairs = line.split(",");
                    for (String pair : pairs) {
                        pair = pair.trim();
                        String[] keyValue = pair.split("=", 2);
                        if (keyValue.length == 2) {
                            String key = keyValue[0].trim();
                            String valueForKey = keyValue[1].trim();
                            if (searchFor != null && TextUtils.equalsIgnoreCase(key, searchFor)) {
                                value = valueForKey;
                                break;
                            } else if (searchFor == null) {
                                value = valueForKey;
                                break;
                            }
                        }
                    }
                } else {
                    log.trace("fetchCustomColumns: unknown type:{}", type);
                }

                if (value != null) {
                    if (device.customAppVersionList == null) device.customAppVersionList = new HashMap<>();
                    device.customAppVersionList.put(label, value);
                    notifyDeviceUpdated(device);
                }
            }
            int afterSize = device.customAppVersionList != null ? device.customAppVersionList.size() : 0;
            if (beforeSize != afterSize) {
                log.trace("fetchCustomColumns: {}, {}", timer, GsonHelper.toJson(device.customAppVersionList));
            }
            device.setBusy(false);
            notifyDeviceUpdated(device);
        });
    }

    private void fetchFreeDiskSpace(Device device) {
        ShellResult result = runShell(device, COMMAND_DISK_SIZE);
        if (result.isSuccess && !result.resultList.isEmpty()) {
            // get last line
            String line = result.resultList.get(result.resultList.size() - 1);
            // Filesystem            1K-blocks    Used Available Use% Mounted on
            // /dev/block/mmcblk0p15  27545632 4090224  23455408  15% /data
            String size = TextUtils.split(line, 3);
            try {
                // size is in 1k blocks
                device.freeSpace = Long.parseLong(size) * 1000L;
                notifyDeviceUpdated(device);
                return;
            } catch (Exception e) {
                log.trace("fetchDeviceDetails: FREE_SPACE Exception:{}", e.getMessage());
            }
            if (device.freeSpace == null || device.freeSpace == 0) {
                log.trace("fetchFreeDiskSpace: NOT_FOUND: {}", GsonHelper.toJson(result.resultList));
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
            notifyDeviceUpdated(device);
        }
    }

    /**
     * run a 'shell service call ..." command and parse the results into a String
     */
    private String runShellServiceCall(Device device, String command) throws Exception {
        ShellResult result = runShell(device, command);
        if (!result.isSuccess) return null;
        // look for errors like:
        // "service: Service iphonesubinfo does not exist"
        String resultDesc = TextUtils.join(result.resultList, ",");
        if (TextUtils.containsAny(resultDesc, true, "does not exist")) {
            log.trace("runShellServiceCall: {}: ERROR: {}", command, resultDesc);
            throw new Exception(resultDesc);
        }

        // -- good result --
        // Result: Parcel(
        // 0x00000000: 00000000 0000000b 00350031 00300034 '........1.2.2.2.'
        // 0x00000010: 00310039 00390034 00310032 00000034 '3.3.3.4.4.4.4...')
        // -- bad result --
        // Result: Parcel(00000000 ffffffff   '........')
        StringBuilder sb = new StringBuilder();
        for (String line : result.resultList) {
            // look for first single quote (')
            int stPos = line.indexOf('\'');
            if (stPos >= 0) {
                // look for last single quote (')
                int endPos = line.indexOf('\'', stPos + 1);
                if (endPos >= 0) {
                    line = line.substring(stPos + 1, endPos);
                    // remove any non-numeric characters
                    line = line.replaceAll("[^-?0-9]+", "");
                    sb.append(line);
                }
            }
        }
        //log.trace("runShellServiceCall: RESULTS: {}", result);
        return sb.isEmpty() ? null : sb.toString();
    }

    /**
     * @return copy of device list
     */
    public List<Device> getDevices() {
        synchronized (deviceList) {
            return new ArrayList<>(deviceList);
        }
    }

    /**
     * Get remote connection manager
     */
    public RemoteConnectionManager getRemoteConnectionManager() {
        return remoteConnectionManager;
    }

    /**
     * Get remote server manager
     */
    public RemoteServerManager getRemoteServerManager() {
        return remoteServerManager;
    }

    public static class Result {
        public boolean isSuccess;
        public String result;

        public Result(boolean isSuccess, String result) {
            this.isSuccess = isSuccess;
            this.result = result;
        }
    }

    public static class ShellResult {
        public boolean isSuccess;
        public List<String> resultList;

        public ShellResult() {
        }

        public ShellResult(boolean isSuccess, List<String> resultList) {
            this.isSuccess = isSuccess;
            this.resultList = resultList;
        }

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
     * Routes to remote server if device is remote
     */
    public ShellResult runShell(Device device, String command) {
        if (device.remoteConnection != null) {
            // remote device
            return device.remoteConnection.executeCommand(device.serial, command);
        }

        // local device execution
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

    public Device getDevice(String serial) {
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
            // handle remote devices differently
            if (device.remoteConnection != null) {
                RemoteScreenWindow window = new RemoteScreenWindow(device, listener);
                window.setVisible(true);
                return;
            }
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
                    "--show-touches", "--stay-awake");
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
        if (device.remoteConnection != null) {
            listener.onTaskComplete(false, "Remote devices not supported");
            return;
        }

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
        // windows-only - add .exe to app
        if (Utils.isWindows() && !TextUtils.endsWith(".exe")) app += ".exe";
        for (String p : pathArr) {
            String fullPath = checkFile(p, app);
            if (fullPath != null) return fullPath;
        }
        // try some other common locations
        String[] arr = new String[]{};
        if (!Utils.isWindows()) {
            arr = new String[]{
                "/opt/homebrew/bin",
                "/usr/local/bin",
                "/home/linuxbrew/.linuxbrew/bin/scrcpy"
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

    public interface ScreenshotListener {
        void onScreenshot(BufferedImage image);
    }

    public void captureScreenshot(Device device, ScreenshotListener listener) {
        commandExecutorService.submit(() -> {
            BufferedImage bufferedImage = captureScreenshotInternal(device);
            listener.onScreenshot(bufferedImage);
        });
    }

    public BufferedImage captureScreenshotInternal(Device device) {
        if (device.remoteConnection != null) {
            // remote device
            return device.remoteConnection.fetchScreenshot(device.serial);
        } else {
            // local device
            try {
                return device.jadbDevice.screencap();
            } catch (Exception e) {
                log.error("captureScreenshotInternal: {}", e.getMessage());
            }
        }
        return null;
    }

    public void setProperty(Device device, String key, String value, TaskListener listener) {
        commandExecutorService.submit(() -> {
            boolean isOk = setPropertyInternal(device, key, value);
            listener.onTaskComplete(isOk, null);
        });
    }

    public boolean setPropertyInternal(Device device, String key, String value) {
        if (device.remoteConnection != null) {
            boolean isOk = device.remoteConnection.setProperty(device.serial, key, value);
            if (isOk) {
                if (device.customPropertyMap == null) device.customPropertyMap = new HashMap<>();
                // update property
                if (TextUtils.isEmpty(value)) device.customPropertyMap.remove(key);
                else device.customPropertyMap.put(key, value);
            }
            return isOk;
        }

        // local device
        if (device.customPropertyMap == null) device.customPropertyMap = new HashMap<>();
        // update property
        if (TextUtils.isEmpty(value)) device.customPropertyMap.remove(key);
        else device.customPropertyMap.put(key, value);
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
            return true;
        } catch (Exception e) {
            log.error("setProperty: {}, {}={}, Exception:{}", device.serial, key, value, e.getMessage());
            return false;
        }
    }

    /**
     * install file to given device
     */
    public void installApp(Device device, File file, TaskListener listener) {
        commandExecutorService.submit(() -> {
            Result result = installAppInternal(device, file);
            if (listener != null) listener.onTaskComplete(result.isSuccess, result.result);
        });
    }

    /**
     * special version of installApp which can install 1 file to multiple devices remotely without needing to upload the file multiple times
     */
    public void installApp(RemoteConnection connection, List<Device> deviceList, File file, TaskListener listener) {
        commandExecutorService.submit(() -> {
            // convert list to serials
            List<String> serialList = new ArrayList<>();
            for (Device device : deviceList) serialList.add(device.serial);
            Result result = connection.installApp(serialList, file);
            if (listener != null) listener.onTaskComplete(result.isSuccess, result.result);
        });
    }

    public Result installAppInternal(Device device, File file) {
        if (device.remoteConnection != null) {
            return device.remoteConnection.installApp(List.of(device.serial), file);
        }
        Timer timer = new Timer();
        log.trace("installAppInternal: file:{}, size:{}", file.getName(), Utils.bytesToDisplayString(file.length()));
        try {
            PackageManager packageManager = new PackageManager(device.jadbDevice);
            packageManager.install(file);
            log.trace("installAppInternal: DONE:{}", timer);
            return new Result(true, null);
        } catch (Exception e) {
            log.error("installAppInternal: {}: ERROR: {}, file:{}", timer, e.getMessage(), file.getAbsolutePath());
            device.status = "failed: " + e.getMessage();
            return new Result(false, e.getMessage());
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

                // check if device is remote
                if (device.remoteConnection != null) {
                    device.remoteConnection.uploadFile(device.serial, dest, filename, file);
                } else {
                    // local device - use JADB
                    try {
                        RemoteFile remoteFile = new RemoteFileRecord(dest, filename, 0, 0, 0);
                        device.jadbDevice.push(file, remoteFile);
                    } catch (Exception e) {
                        log.error("copyFile: {} -> {}, Exception:{}", file.getAbsolutePath(), dest, e.getMessage());
                    }
                }
            }
        }
    }

    public void restartDevice(Device device, TaskListener listener) {
        commandExecutorService.submit(() -> {
            runShell(device, COMMAND_REBOOT);
            // TODO: detect success/fail
            listener.onTaskComplete(true, null);
        });
    }

    public interface DevicePropertyListener {
        void onTaskComplete(boolean isSuccess, Map<String, String> map);
    }

    public void fetchDeviceProperties(Device device, DevicePropertyListener listener) {
        commandExecutorService.submit(() -> {
            Map<String, String> map;
            if (device.remoteConnection != null) {
                map = device.remoteConnection.fetchDeviceProperties(device.serial);
            } else {
                map = fetchDevicePropertiesInternal(device);
            }
            listener.onTaskComplete(false, map);
        });
    }

    public Map<String, String> fetchDevicePropertiesInternal(Device device) {
        try {
            return new PropertyManager(device.jadbDevice).getprop();
        } catch (Exception e) {
            log.error("fetchDevicePropertiesInternal: PROP Exception:{}", e.getMessage());
            return null;
        }
    }

    public void runCustomCommand(Device device, String customCommand, CommandListener listener) {
        commandExecutorService.submit(() -> {
            ShellResult result = runShell(device, customCommand);
            boolean isSuccess = result.isSuccess;
            log.trace("runCustomCommand: DONE: success:{}, {}", isSuccess, GsonHelper.toJson(result.resultList));
            if (listener != null) listener.onTaskComplete(result);
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

    public static class FileResponse {
        public List<DeviceFile> fileList;
        public String error;

        public FileResponse(List<DeviceFile> fileList, String error) {
            this.fileList = fileList;
            this.error = error;
        }
    }

    /**
     * fetch list of files for a given folder on device
     */
    public void fetchFileList(Device device, String path, boolean useRoot, DeviceFileListener listener) {
        commandExecutorService.submit(() -> {
            FileResponse fileResponse = fetchFileListInternal(device, path, useRoot);
            listener.handleFiles(fileResponse.fileList, fileResponse.error);
        });
    }

    /**
     * synchronous version of fetchFileList
     */
    protected FileResponse fetchFileListInternal(Device device, String path, boolean useRoot) {
        // check if device is remote - use remote API
        if (device.remoteConnection != null) {
            // NOTE: root actions not supported remotely
            return device.remoteConnection.fetchFileList(device.serial, path);
        }

        if (path == null) path = "";
        String safePath = path;
        // make sure folder ends with "/"
        if (!TextUtils.endsWith(safePath, "/")) safePath += "/";
        if (safePath.indexOf(' ') > 0) {
            safePath = "'" + safePath + "'";
        }
        log.trace("fetchFileListInternal: {} {}", safePath, useRoot ? "(ROOT)" : "");
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
                    log.debug("fetchFileListInternal: NO_ROOT:{}", dir);
                    return new FileResponse(null, ERR_ROOT_NOT_AVAILABLE);
                } else if (TextUtils.containsAny(dir, true, "permission denied")) {
                    log.debug("fetchFileListInternal: NO_PERMISSION:{}", dir);
                    return new FileResponse(null, ERR_PERMISSION_DENIED);
                } else if (TextUtils.containsAny(dir, true, "Not a directory", "No such file or directory")) {
                    log.debug("fetchFileListInternal: NOT_DIR:{}, {}", dir, GsonHelper.toJson(result.resultList));
                    return new FileResponse(null, ERR_NOT_A_DIRECTORY);
                }
            }
        }
        //log.trace("listFiles: FILES:{}, PATH:{}, {}", fileList.size(), safePath, GsonHelper.toJson(fileList));
        return new FileResponse(fileList, null);
    }

    public interface ProgressListener {
        void onProgress(int numCompleted, int numTotal, String msg);
    }

    public interface TaskListener {
        void onTaskComplete(boolean isSuccess, String error);
    }

    public interface CommandListener {
        void onTaskComplete(ShellResult result);
    }

    /**
     * download a file or folder from device
     */
    public void downloadFile(Device device, String path, DeviceFile file, File saveFile, TaskListener listener) {
        log.debug("downloadFile: {}/{} -> {}", path, file.name, saveFile.getAbsolutePath());
        commandExecutorService.submit(() -> {
            boolean isOk = downloadFileInternal(device, path, file, saveFile);
            // test if file was created
            listener.onTaskComplete(isOk, null);
        });
    }

    /**
     * recursive method to download a file or folder
     *
     * @return true if download was successful & file/folder exists
     */
    protected boolean downloadFileInternal(Device device, String path, DeviceFile file, File saveFile) {
        if (file.isDirectory) {
            // create local folder
            if (!saveFile.exists()) {
                boolean isOk = saveFile.mkdir();
                if (!isOk) {
                    log.error("downloadFileInternal: DIR:{}, mkdir:{}", saveFile.getAbsolutePath(), isOk);
                    return false;
                }
                log.trace("downloadFileInternal: DIR:{}, mkdir:{}", saveFile.getAbsolutePath(), isOk);
            }
            // get list of files in folder
            String dirPath = path + "/" + file.name;
            FileResponse fileResponse = fetchFileListInternal(device, dirPath, false);
            if (fileResponse.fileList == null || fileResponse.error != null) {
                log.error("downloadFileInternal: DIR:{}, ERROR:{}", dirPath, fileResponse.error);
                return false;
            }
            // download every file in folder
            for (DeviceFile deviceFile : fileResponse.fileList) {
                File subFile = new File(saveFile, deviceFile.name);
                downloadFileInternal(device, path, deviceFile, subFile);
            }
            return true;
        } else {
            // download file
            log.trace("downloadFileInternal: {}/{} -> {}", path, file.name, saveFile.getAbsolutePath());

            if (device.remoteConnection != null) {
                // remote device
                device.remoteConnection.downloadFile(device.serial, path, file.name, saveFile);
            } else {
                // local device
                RemoteFile remoteFile = new RemoteFileRecord(path, file.name, 0, 0, 0);
                try {
                    device.jadbDevice.pull(remoteFile, saveFile);
                } catch (Exception e) {
                    log.error("downloadFileInternal: {}/{}, Exception:{}", path, file.name, e.getMessage());
                    return false;
                }
            }

            return saveFile.exists() && saveFile.length() > 0;
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
        /**
         * new log entries were added
         */
        void handleLogEntries(List<LogEntry> logEntryList);

        /**
         * update process map (map of all running apps/processes and their process ID)
         */
        void handleProcessMap(Map<String, String> processMap);
    }

    private AtomicBoolean getLoggingState(String serial, boolean createIfNotFound) {
        AtomicBoolean loggingState = loggingStateMap.get(serial);
        if (loggingState == null && createIfNotFound) {
            loggingState = new AtomicBoolean(true);
            loggingStateMap.put(serial, loggingState);
        }
        return loggingState;
    }

    /**
     * start capturing device logs
     *
     * @param lastLogTime - last log entry (if logging had started previousl) - 10-16 11:34:17.824
     */
    public void startLogging(Device device, String lastLogTime, String filterText, DeviceLogListener listener) {
        stopLogging(device);

        // handle remote device via WebSocket
        if (device.remoteConnection != null) {
            log.debug("startLogging: REMOTE: device: {}, filter:{}", device.serial, filterText);
            device.remoteConnection.startLogging(device.serial, lastLogTime, filterText, listener);
            return;
        }

        // local device - existing implementation
        commandExecutorService.submit(() -> {
            String logStartTime = lastLogTime;
            log.debug("startLogging: {}, from:{}", device.serial, lastLogTime);
            AtomicBoolean loggingState = getLoggingState(device.serial, true);
            loggingState.set(true);
            InputStream inputStream = null;
            try {
                String[] args = new String[]{"-v", "threadtime"};
                inputStream = device.jadbDevice.executeShell("logcat", args);
                BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));

                long lastUpdateMs = System.currentTimeMillis();
                List<LogEntry> logList = new ArrayList<>();
                String line;
                long id = 0;
                int numSkipped = 0;
                while ((line = input.readLine()) != null) {
                    LogEntry logEntry = new LogEntry(line, id++);
                    if (logStartTime != null && logEntry.date != null) {
                        // start capturing logs after logStartTime
                        if (logStartTime.compareTo(logEntry.date) > 0) {
                            numSkipped++;
                            continue;
                        }
                        log.trace("startLogging: READY: skipped:{}, from:{}", numSkipped, logStartTime);
                        // stop looking once we hit a new log entry
                        logStartTime = null;
                    }
                    logList.add(logEntry);

                    // only update every X ms
                    if (System.currentTimeMillis() - lastUpdateMs >= LOG_INTERVAL_MS && !logList.isEmpty()) {
                        // update
                        listener.handleLogEntries(logList);
                        logList.clear();
                        lastUpdateMs = System.currentTimeMillis();

                        // check if logging is still running
                        if (!loggingState.get()) {
                            loggingStateMap.remove(device.serial);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("startLogging: {}", e.getMessage(), e);
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
            if (isLogging(device.serial)) {
                scheduleNextProcessCheck(device, listener);
            }
        });
    }

    private void scheduleNextProcessCheck(Device device, DeviceLogListener listener) {
        // make sure logging is still running
        if (!isLogging(device.serial)) return;

        // run in 30 seconds
        scheduledExecutorService.schedule(() -> {
            // make sure logging is still running
            if (!isLogging(device.serial)) return;

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
        List<String> resultList = result.resultList;
        for (int i = 0; i < resultList.size(); i++) {
            String line = resultList.get(i);
            if (i == 0 && TextUtils.startsWith(line, "bad pid")) {
                // older devices may not support the ps args.. try another route
                return getProcessMapAlternative(device);
            }
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

    private Map<String, String> getProcessMapAlternative(Device device) {
        ShellResult result = runShell(device, "ps");
        // USER      PID   PPID  VSIZE  RSS   WCHAN              PC  NAME
        // root      1     0     21964  2880  SyS_epoll_ 00004d9054 S /init
        // root      2     0     0      0       kthreadd 0000000000 S kthreadd
        // root      3     2     0      0     smpboot_th 0000000000 S ksoftirqd/0
        // bluetooth 30891 236   1061340 16884 SyS_epoll_ 00f5597304 S com.android.bluetooth
        // u0_a74    30946 235   1468852 33996 SyS_epoll_ 7fa5ce1870 S com.ttxapps.wifiadb
        // u0_a55    31235 235   1464020 32072 SyS_epoll_ 7fa5ce1870 S com.cyanogenmod.lockclock
        Map<String, String> pidMap = new HashMap<>();
        List<String> resultList = result.resultList;
        int indexPid = -1;
        int indexApp = -1;
        for (String line : resultList) {
            List<String> pidList = TextUtils.splitSafe(line);
            if (pidList.size() < 2) continue;

            if (indexApp == -1 || indexPid == -1) {
                for (int i = 0; i < pidList.size(); i++) {
                    String label = pidList.get(i);
                    if (TextUtils.equals(label, "PID")) {
                        indexPid = i;
                    } else if (TextUtils.equals(label, "NAME")) {
                        indexApp = i;
                        // special case
                        if (pidList.size() == 8) indexApp++;
                    }
                }
            } else if (indexPid < pidList.size() && indexApp < pidList.size()) {
                String pid = pidList.get(indexPid);
                String app = pidList.get(indexApp);
                int atPos = app.indexOf('@');
                if (atPos > 0) {
                    app = app.substring(0, atPos);
                }
                pidMap.put(pid, app);
            }
        }
        return pidMap;
    }

    public void stopLogging(Device device) {
        // handle remote device
        if (device.remoteConnection != null) {
            device.remoteConnection.stopLogging(device.serial);
            return;
        }

        // local device
        AtomicBoolean loggingState = getLoggingState(device.serial, false);
        if (loggingState != null && loggingState.get()) {
            log.debug("stopLogging: {}", device.serial);
            loggingState.set(false);
        }
    }

    public boolean isLogging(Device device) {
        // handle remote device
        if (device.remoteConnection != null) {
            return device.remoteConnection.isLogging(device.serial);
        }
        // local device
        return isLogging(device.serial);
    }

    private boolean isLogging(String serial) {
        AtomicBoolean loggingState = getLoggingState(serial, false);
        return loggingState != null && loggingState.get();
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

        if (remoteConnectionManager != null) {
            remoteConnectionManager.shutdown();
        }
        if (remoteServerManager != null) {
            remoteServerManager.stopServer();
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
        //file.createTempFile(name);
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

    /**
     * Wake device screen
     */
    public boolean wakeDevice(Device device) {
        log.debug("wakeDevice: {}", device.serial);
        // check if screen is awake
        DeviceManager.ShellResult result = runShell(device, "dumpsys power");
        if (result.isSuccess && result.resultList != null) {
            boolean isScreenOn = true;
            for (String line : result.resultList) {
                if (line.contains("mWakefulness=")) {
                    // mWakefulness=Dozing; mWakefulness=Asleep
                    log.debug("wakeDevice: {}", line);
                    if (TextUtils.containsAny(line, true, "Asleep", "Dozing")) {
                        isScreenOn = false;
                        break;
                    }
                }
            }
            if (!isScreenOn) {
                // wake up the device
                runShell(device, "input keyevent " + AndroidKeyMapper.KEYCODE_WAKEUP);
                Utils.sleep(1000);
                // keep screen on during mirroring
                result = runShell(device, "svc power stayon true");
                if (!result.isSuccess) {
                    // fallback: keep screen on while AC or USB (1|2 = 3)
                    result = runShell(device, "settings put global stay_on_while_plugged_in 3");
                }
            }
        }
        return result.isSuccess;
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
