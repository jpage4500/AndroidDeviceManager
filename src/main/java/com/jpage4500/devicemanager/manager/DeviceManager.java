package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.ui.SettingsScreen;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DeviceManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceManager.class);

    private static final String SCRIPT_DEVICE_LIST = "device-list.sh";
    private static final String SCRIPT_DEVICE_DETAILS = "device-details.sh";
    private static final String SCRIPT_TERMINAL = "terminal.sh";
    private static final String SCRIPT_CUSTOM_COMMAND = "custom-command.sh";
    private static final String SCRIPT_RESTART = "restart.sh";
    private static final String SCRIPT_COPY_FILE = "copy-file.sh";
    private static final String SCRIPT_INSTALL_APK = "install-apk.sh";
    private static final String SCRIPT_SET_PROPERTY = "set-property.sh";
    private static final String SCRIPT_SCREENSHOT = "screenshot.sh";
    private static final String SCRIPT_MIRROR = "mirror.sh";
    private static final String SCRIPT_LIST_FILES = "list-files.sh";
    private static final String SCRIPT_DOWNLOAD_FILE = "download-file.sh";
    private static final String SCRIPT_DELETE_FILE = "delete-file.sh";
    private static final String SCRIPT_START_LOGGING = "start-logging.sh";

    // how often to poll (adb devices -l)
    private static final int POLLING_INTERVAL_SECS = 10;
    // how often to do a full refresh of connected devices
    private static final long FULL_REFRESH_MS = TimeUnit.MINUTES.toMillis(10);

    private static volatile DeviceManager instance;

    private final List<Device> deviceList;
    private final String tempFolder;
    private final List<Process> processList;

    private final ScheduledExecutorService listExecutorService;
    private final ExecutorService detailsExecutorService;
    private final ExecutorService commandExecutorService;

    private ScheduledFuture<?> listDevicesFuture;
    private Process logProcess;

    private long lastRefreshMs;

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

        listExecutorService = Executors.newScheduledThreadPool(1);
        detailsExecutorService = Executors.newFixedThreadPool(10);
        commandExecutorService = Executors.newFixedThreadPool(10);

        tempFolder = System.getProperty("java.io.tmpdir");
        copyResourcesToFiles();
    }

    public interface DeviceListener {
        // device list was refreshed
        void handleDevicesUpdated(List<Device> deviceList);

        // single device was updated
        void handleDeviceUpdated(Device device);
    }

    /**
     * starts a scheduled task to list devices every [X] seconds
     *
     * @param pollingInterval polling interval in seconds
     */
    public void startDevicePolling(DeviceListener listener, long pollingInterval) {
        stopDevicePolling();
        listDevicesFuture = listExecutorService.scheduleWithFixedDelay(() -> {
            listDevicesInternal(listener);
        }, 0, pollingInterval, TimeUnit.SECONDS);
    }

    public void stopDevicePolling() {
        if (listDevicesFuture != null && !listDevicesFuture.isCancelled()) {
            listDevicesFuture.cancel(true);
        }
    }

    private void getDeviceDetails(Device device, List<String> appList, DeviceListener listener) {
        detailsExecutorService.submit(() -> {
            getDeviceDetailsInternal(device, appList, listener);
        });
    }

    public void mirrorDevice(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "mirring...";
            listener.handleDeviceUpdated(device);
            String name = device.serial;
            if (device.phone != null) name += ": " + device.phone;
            else if (device.model != null) name += ": " + device.model;
            runScript(SCRIPT_MIRROR, true, true, device.serial, name);
            log.debug("mirrorDevice: DONE");
            device.status = null;
            listener.handleDeviceUpdated(device);
        });
    }

    public void captureScreenshot(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "screenshot...";
            listener.handleDeviceUpdated(device);
            runScript(SCRIPT_SCREENSHOT, device.serial);
            device.status = null;
            listener.handleDeviceUpdated(device);
            log.debug("captureScreenshot: DONE");
        });
    }

    public void runUserScript(Device device, File file, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "runUserScript...";
            listener.handleDeviceUpdated(device);
            runScript(file, false, true, device.serial, tempFolder);
            device.status = null;
            listener.handleDeviceUpdated(device);
            log.debug("runUserScript: DONE");
        });
    }

    public void setProperty(Device device, String key, String value) {
        if (value == null) value = "";
        String safeValue = value.replaceAll(" ", "~");
        commandExecutorService.submit(() -> {
            runScript(SCRIPT_SET_PROPERTY, device.serial, key, safeValue);
            log.debug("setProperty: {}, key:{}, value:{}, DONE", device.serial, key, safeValue);
        });
    }

    public void installApp(Device device, File file, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            String path = file.getAbsolutePath();
            device.status = "installing...";
            if (listener != null) listener.handleDeviceUpdated(device);
            runScript(SCRIPT_INSTALL_APK, device.serial, path);
            device.status = null;
            if (listener != null) listener.handleDeviceUpdated(device);
            log.debug("installApp: {}, {}, DONE", device.serial, path);
        });
    }

    public void copyFile(Device device, File file, String dest, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            String path = file.getAbsolutePath();
            device.status = "copying...";
            if (listener != null) listener.handleDeviceUpdated(device);
            runScript(SCRIPT_COPY_FILE, device.serial, path);
            device.status = null;
            if (listener != null) listener.handleDeviceUpdated(device);
            log.debug("copyFile: {}, {}, DONE", device.serial, path);
        });
    }

    public void restartDevice(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "restarting...";
            listener.handleDeviceUpdated(device);
            runScript(SCRIPT_RESTART, device.serial);
            log.debug("restartDevice: DONE");
            device.status = null;
            listener.handleDeviceUpdated(device);
        });
    }

    public void runCustomCommand(Device device, String customCommand, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "running...";
            listener.handleDeviceUpdated(device);
            List<String> resultList = runScript(SCRIPT_CUSTOM_COMMAND, device.serial, customCommand);
            device.status = GsonHelper.toJson(resultList);
            listener.handleDeviceUpdated(device);
        });
    }

    public void openTerminal(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "terminal...";
            listener.handleDeviceUpdated(device);
            runScript(SCRIPT_TERMINAL, true, true, device.serial);
            device.status = null;
            listener.handleDeviceUpdated(device);
        });
    }

    public interface DeviceFileListener {
        void handleFiles(List<DeviceFile> fileList);
    }

    public void listFiles(Device device, String path, DeviceFileListener listener) {
        if (TextUtils.notEmpty(path) && !TextUtils.endsWith(path, "/")) {
            path += "/";
        }
        String finalPath = path;
        commandExecutorService.submit(() -> {
            device.status = "list files...";
            List<String> resultList = runScript(SCRIPT_LIST_FILES, false, false, device.serial, finalPath);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
            List<DeviceFile> fileList = new ArrayList<>();
            for (String result : resultList) {
                DeviceFile file = new DeviceFile();
                // handle errors such as: "Permission denied", "Not a directory"
                if (TextUtils.containsIgnoreCase(result, "Permission denied")) {
                    file.name = "Permission denied";
                    fileList.add(file);
                    break;
                } else if (TextUtils.containsIgnoreCase(result, "Not a directory")) {
                    file.name = "Not a directory";
                    fileList.add(file);
                    break;
                }

                String[] resultArr = result.split("\\s+");
                if (resultArr.length < 8) continue;

                // -- permissions (0) --
                String permissions = resultArr[0];
                if (permissions.startsWith("d")) {
                    file.isDir = true;
                } else if (permissions.startsWith("l")) {
                    file.isLink = true;
                }

                // -- file size (4) --
                String sizeStr = resultArr[4];
                if (TextUtils.equalsIgnoreCase(sizeStr, "?")) continue;
                file.size = TextUtils.getNumberLong(sizeStr, 0);

                // -- file name (7+) --
                StringBuilder name = new StringBuilder();
                for (int pos = 7; pos < resultArr.length; pos++) {
                    if (name.length() > 0) name.append(" ");
                    name.append(resultArr[pos]);
                }
                if (file.isLink) {
                    // remove "-> XYZ" from name
                    int linkPos = name.indexOf(" ->");
                    if (linkPos > 0) {
                        name.delete(linkPos, name.length());
                    }
                }
                if (TextUtils.equalsAny(name, true, ".", "..")) continue;
                file.name = name.toString();

                // -- date (5): "2023-06-12" --
                String dateStr = resultArr[5];
                // -- time (6): "19:00" --
                String timeStr = resultArr[6];

                try {
                    file.date = dateFormat.parse(dateStr + " " + timeStr);
                } catch (ParseException e) {
                    log.debug("listFiles: Exception: date:{}", dateStr + " " + timeStr);
                }

                fileList.add(file);
            }

            // always add ".." to every result as long as starting path isn't ""
            if (TextUtils.notEmpty(finalPath)) {
                DeviceFile upFile = new DeviceFile();
                upFile.name = "..";
                upFile.isDir = true;
                fileList.add(0, upFile);
            }
            if (listener != null) listener.handleFiles(fileList);

            device.status = null;
        });
    }

    public interface TaskListener {
        void onTaskComplete(boolean isSuccess);
    }

    public void downloadFile(Device device, String srcPath, String srcName, String dest, TaskListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "downloading...";
            List<String> resultList = runScript(SCRIPT_DOWNLOAD_FILE, device.serial, srcPath, srcName, dest);
            // TODO
            if (listener != null) listener.onTaskComplete(true);
            device.status = null;
        });
    }

    public void deleteFile(Device device, String srcPath, String srcName, TaskListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "deleting...";
            List<String> resultList = runScript(SCRIPT_DELETE_FILE, device.serial, srcPath, srcName);
            // TODO
            if (listener != null) listener.onTaskComplete(true);
            device.status = null;
        });
    }

    public interface DeviceLogListener {
        void handleLogEntries(List<LogEntry> logEntryList);

        void handleLogEntry(LogEntry logEntry);
    }

    public void stopLogging(Device device) {
        log.debug("stopLogging: ");
        if (logProcess != null && logProcess.isAlive()) {
            logProcess.destroy();
        }
    }

    public void startLogging(Device device, DeviceLogListener listener) {
        commandExecutorService.submit(() -> {
            log.debug("startLogging: ");

            // 10-16 11:34:17.824
            SimpleDateFormat inputFormat = new SimpleDateFormat("MM-dd HH:mm:ss");
            // display format
            SimpleDateFormat outputFormat = new SimpleDateFormat("MM-dd HH:mm:ss");

            try {
                List<String> commandList = new ArrayList<>();
                File tempFile = getScriptFile(SCRIPT_START_LOGGING);
                commandList.add(tempFile.getAbsolutePath());
                commandList.add(device.serial);

                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command(commandList);
                logProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(logProcess.getInputStream()));
                String line;
                List<LogEntry> logList = new ArrayList<>();
                long lastUpdateMs = System.currentTimeMillis();
                try {
                    while (true) {
                        line = reader.readLine();
                        if (line == null) break;
                        else if (line.isEmpty()) continue;
                        LogEntry logEntry = new LogEntry(line, inputFormat, outputFormat);
                        logList.add(logEntry);

                        // only update every X ms
                        if (System.currentTimeMillis() - lastUpdateMs >= 100) {
                            // update
                            listener.handleLogEntries(logList);
                            logList.clear();
                            lastUpdateMs = System.currentTimeMillis();
                        }
                    }
                    reader.close();
                } catch (IOException e) {
                    log.error("startLogging: Exception: {}", e.getMessage());
                }
            } catch (Exception e) {
                log.error("startLogging: Exception: {}:{}", e.getClass().getSimpleName(), e.getMessage());
                log.error("startLogging", e);
            }
            log.debug("startLogging: DONE");
        });
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

    private void listDevicesInternal(DeviceListener listener) {
        boolean isFullRefreshNeeded = System.currentTimeMillis() - lastRefreshMs > FULL_REFRESH_MS;
        lastRefreshMs = System.currentTimeMillis();
        List<String> results = runScript(SCRIPT_DEVICE_LIST, false, false);
        if (results == null) {
            listener.handleDevicesUpdated(null);
            return;
        }
        List<Device> resultList = new ArrayList<>();
        // List of devices attached
        // 04QAX0NRLM             device usb:34603008X product:bonito model:Pixel_3a_XL device:bonito transport_id:3
        // 192.168.0.28:35031     offline product:x1quex model:SM_G981U1 device:x1q transport_id:7
        // 5858444a4e483498       unauthorized usb:34603008X transport_id:3
        for (String result : results) {
            if (result.length() == 0 || result.startsWith("List")) continue;

            String[] deviceArr = result.split(" ");
            if (deviceArr.length <= 1) continue;
            // TODO: check possible values for serialNumber including wireless
            String serialNumber = deviceArr[0];
            Device device = new Device();
            device.serial = serialNumber;

            resultList.add(device);

            // get any other values returned
            device.isOnline = true;
            for (String keyVal : deviceArr) {
                if (keyVal.startsWith("model:")) {
                    device.model = keyVal.substring("model:".length());
                } else if (TextUtils.equalsIgnoreCase(keyVal, "offline")) {
                    device.status = "offline";
                    device.isOnline = false;
                } else if (TextUtils.equalsIgnoreCase(keyVal, "unauthorized")) {
                    device.status = "unauthorized";
                    device.isOnline = false;
                }
            }
        }

        boolean isChanged = false;
        synchronized (deviceList) {
            // iterate through found list
            for (Device resultDevice : resultList) {
                boolean isFound = false;
                // iterate through master list
                for (Device device : deviceList) {
                    if (TextUtils.equals(resultDevice.serial, device.serial)) {
                        isFound = true;
                        // check if anything has changed.. if so, update resultDevice
                        // NOTE: "adb devices -l" only returns a few fields (serial, model, online status)
                        if (resultDevice.isOnline != device.isOnline) {
                            device.isOnline = resultDevice.isOnline;
                            isChanged = true;
                        }
                    }
                }
                if (!isFound) {
                    // new device
                    deviceList.add(resultDevice);
                    isChanged = true;
                }
            }

            // next, find any devices that don't exist anymore
            for (Iterator<Device> iterator = deviceList.iterator(); iterator.hasNext(); ) {
                Device device = iterator.next();
                boolean isFound = false;
                for (Device resultDevice : resultList) {
                    if (TextUtils.equals(resultDevice.serial, device.serial)) {
                        isFound = true;
                    }
                }
                if (!isFound) {
                    // REMOVE device from list
                    log.debug("listDevicesInternal: REMOVING:{}", GsonHelper.toJson(device));
                    iterator.remove();
                    isChanged = true;
                }
            }

            if (isChanged) {
                listener.handleDevicesUpdated(deviceList);
            }

            List<String> appList = null;
            // kick off device details request if necessary
            for (Device device : deviceList) {
                if (device.isOnline && (isFullRefreshNeeded || !device.hasFetchedDetails)) {
                    if (appList == null) {
                        appList = SettingsScreen.getCustomApps();
                    }
                    getDeviceDetails(device, appList, listener);
                }
            }
        }
    }

    private Device getDeviceForSerial(String serialNumber) {
        synchronized (deviceList) {
            for (Device device : deviceList) {
                if (TextUtils.equals(device.serial, serialNumber)) {
                    return device;
                }
            }
        }
        return null;
    }

    public void refreshDevices(DeviceListener listener) {
        // TODO: probably a better way to force a refresh
        stopDevicePolling();

        synchronized (deviceList) {
            for (Device device : deviceList) {
                device.hasFetchedDetails = false;
            }
        }

        startDevicePolling(listener, POLLING_INTERVAL_SECS);
    }

    private void getDeviceDetailsInternal(Device device, List<String> appList, DeviceListener listener) {
        device.status = "details...";
        listener.handleDeviceUpdated(device);
        List<String> args = new ArrayList<>();
        args.add(device.serial);
        args.addAll(appList);
        List<String> results = runScript(SCRIPT_DEVICE_DETAILS, args.toArray(new String[]{}));
        if (results != null) {
            for (String line : results) {
                String[] lineArr = line.split(": ");
                if (lineArr.length <= 1) continue;
                String key = lineArr[0].trim();
                String val = lineArr[1].trim();
                switch (key) {
                    case "phone":
                        device.phone = val;
                        break;
                    case "imei":
                        device.imei = val;
                        break;
                    case "carrier":
                        device.carrier = val;
                        break;
                    case "release":
                        device.release = val;
                        break;
                    case "sdk":
                        device.sdk = val;
                        break;
                    case "free":
                        device.freeSpace = val;
                        break;
                    case "custom1":
                        device.custom1 = val.replaceAll("~", " ");
                        break;
                    case "custom2":
                        device.custom2 = val.replaceAll("~", " ");
                        break;
                    default:
                        // check custom app list
                        if (appList.contains(key)) {
                            //log.debug("getDeviceDetailsInternal: GOT:{} = {}", key, val);
                            if (device.customAppList == null) device.customAppList = new HashMap<>();
                            device.customAppList.put(key, val);
                        }
                        break;
                }
            }

            device.status = null;
            listener.handleDeviceUpdated(device);
        }
        device.hasFetchedDetails = true;
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
                    if (name.endsWith(".sh")) {
                        int pos = name.indexOf("scripts/");
                        if (pos >= 0) {
                            name = name.substring(pos + "scripts/".length());
                            copyResourceToFile(name, jarFile.getInputStream(jarEntry));
                            numScripts++;
                        }
                    }
                }
                log.debug("copyResourcesToFiles: {}, JAR: {}", numScripts, file);
            } else {
                // running via IntelliJ IDE
                URL url = getClass().getResource("/scripts");
                Path path = Paths.get(url.toURI());
                Files.walk(path, 1).forEach(p -> {
                    String filename = p.toString();
                    if (filename.endsWith(".sh")) {
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

    public List<String> runScript(String scriptName, String... args) {
        return runScript(scriptName, false, true, args);
    }

    public List<String> runScript(String scriptName, boolean isLongRunning, boolean logResults, String... args) {
        //if (logResults) log.trace("runScript: {}, args:{}", scriptName, GsonHelper.toJson(args));
        File tempFile = getScriptFile(scriptName);
        return runScript(tempFile, isLongRunning, logResults, args);
    }

    public List<String> runScript(File script, boolean isLongRunning, boolean logResults, String... args) {
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(script.getAbsolutePath());
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
                    log.error("runScript: NOT FINISHED: {}, args:{}", script.getAbsolutePath(), GsonHelper.toJson(args));
                }
            }
            if (exitValue != 0) {
                log.error("runScript: ERROR:{}, {}, args:{}", exitValue, script.getAbsolutePath(), GsonHelper.toJson(args));
            }

            List<String> resultList = readInputStream(process.getInputStream());
            if (resultList.size() > 0 && log.isTraceEnabled() && logResults) {
                log.trace("runScript: RESULTS: {}", GsonHelper.toJson(resultList));
            }
            List<String> errorList = readInputStream(process.getErrorStream());
            synchronized (processList) {
                processList.remove(process);
            }
            if (errorList.size() > 0) {
                log.error("runScript: ERROR: {}", GsonHelper.toJson(errorList));
                return errorList;
            }
            return resultList;
        } catch (Exception e) {
            log.error("runScript: Exception: {}:{}, script:{}", e.getClass().getSimpleName(), e.getMessage(), script.getAbsolutePath());
        }
        return null;
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
