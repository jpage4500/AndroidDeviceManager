package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.*;

public class DeviceManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceManager.class);

    private static volatile DeviceManager instance;

    private final List<Device> deviceList;
    private final String tempFolder;
    private final List<Process> processList;

    private final ScheduledExecutorService listExecutorService;
    private final ExecutorService detailsExecutorService;
    private final ExecutorService commandExecutorService;

    private ScheduledFuture<?> listDevicesFuture;

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

    private void getDeviceDetails(Device device, DeviceListener listener) {
        detailsExecutorService.submit(() -> {
            getDeviceDetailsInternal(device, listener);
        });
    }

    public void mirrorDevice(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "mirring...";
            listener.handleDeviceUpdated(device);
            String name = device.serial;
            if (device.phone != null) name += ": " + device.phone;
            else if (device.model != null) name += ": " + device.model;
            runScript("mirror.sh", device.serial, name);
            log.debug("mirrorDevice: DONE");
            device.status = null;
            listener.handleDeviceUpdated(device);
        });
    }

    public void captureScreenshot(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "screenshot...";
            listener.handleDeviceUpdated(device);
            runScript("screenshot.sh", device.serial);
            device.status = null;
            listener.handleDeviceUpdated(device);
            log.debug("captureScreenshot: DONE");
        });
    }

    public void setProperty(Device device, String key, String value) {
        if (value == null) value = "";
        String safeValue = value.replaceAll(" ", "~");
        commandExecutorService.submit(() -> {
            runScript("set-property.sh", device.serial, key, safeValue);
            log.debug("setProperty: {}, key:{}, value:{}, DONE", device.serial, key, safeValue);
        });
    }

    public void installApp(Device device, File file, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            String path = file.getAbsolutePath();
            device.status = "installing...";
            listener.handleDeviceUpdated(device);
            List<String> resultList = runScript("install-apk.sh", device.serial, path);
            log.debug("installApp: RESULTS: {}", GsonHelper.toJson(resultList));
            device.status = null;
            listener.handleDeviceUpdated(device);
            log.debug("installApp: {}, {}, DONE", device.serial, path);
        });
    }

    public void copyFile(Device device, File file, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            String path = file.getAbsolutePath();
            device.status = "copying...";
            listener.handleDeviceUpdated(device);
            List<String> resultList = runScript("copy-file.sh", device.serial, path);
            log.debug("copyFile: RESULTS: {}", GsonHelper.toJson(resultList));
            device.status = null;
            listener.handleDeviceUpdated(device);
            log.debug("copyFile: {}, {}, DONE", device.serial, path);
        });
    }

    public void restartDevice(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "restarting...";
            listener.handleDeviceUpdated(device);
            runScript("restart.sh", device.serial);
            log.debug("restartDevice: DONE");
            device.status = null;
            listener.handleDeviceUpdated(device);
        });
    }

    public void runCustomCommand(Device device, String customCommand, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "running...";
            listener.handleDeviceUpdated(device);
            List<String> resultList = runScript("custom-command.sh", device.serial, customCommand);
            log.debug("runCustomCommand: DONE: {}", GsonHelper.toJson(resultList));
            device.status = GsonHelper.toJson(resultList);
            listener.handleDeviceUpdated(device);
        });
    }

    public void openTerminal(Device device, DeviceListener listener) {
        commandExecutorService.submit(() -> {
            device.status = "terminal...";
            listener.handleDeviceUpdated(device);
            List<String> resultList = runScript("terminal.sh", device.serial);
            log.debug("openTerminal: DONE: {}", GsonHelper.toJson(resultList));
            device.status = null;
            listener.handleDeviceUpdated(device);
        });
    }

    public void handleExit() {
        List<Process> processCopyList = new ArrayList<>();
        synchronized (processList) {
            processCopyList.addAll(processList);
        }

        for (Process process : processCopyList) {
            if (process.isAlive()) {
                log.debug("handleExit: killing: {}", process);
                process.destroy();
            }
        }
    }

    private void listDevicesInternal(DeviceListener listener) {
        List<String> results = runScript("device-list.sh");
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
        }

        if (isChanged) {
            listener.handleDevicesUpdated(deviceList);
        }

        // kick off device details request if necessary
        for (Device device : deviceList) {
            if (device.isOnline && !device.hasFetchedDetails) {
                getDeviceDetails(device, listener);
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

    private void getDeviceDetailsInternal(Device device, DeviceListener listener) {
        device.status = "details...";
        listener.handleDeviceUpdated(device);
        List<String> results = runScript("device-details.sh", device.serial);
        if (results != null) {
            boolean isUpdated = false;
            for (String line : results) {
                String[] lineArr = line.split(": ");
                if (lineArr.length <= 1) continue;
                String key = lineArr[0];
                String val = lineArr[1];
                switch (key) {
                    case "phone":
                        device.phone = val;
                        isUpdated = true;
                        break;
                    case "imei":
                        device.imei = val;
                        isUpdated = true;
                        break;
                    case "carrier":
                        device.carrier = val;
                        isUpdated = true;
                        break;
                    case "custom1":
                        device.custom1 = val.replaceAll("~", " ");
                        isUpdated = true;
                        break;
                    case "custom2":
                        device.custom2 = val.replaceAll("~", " ");
                        isUpdated = true;
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
                log.debug("copyResourcesToFiles: JAR: {}", file);
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
                        }
                    }
                }
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
        log.debug("copyResource: {} to {}", name, tempFile.getAbsolutePath());
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

    private List<String> runScript(String scriptName, String... args) {
        log.trace("runScript: {}, args:{}", scriptName, GsonHelper.toJson(args));
        File tempFile = new File(tempFolder, scriptName);
        if (!tempFile.exists()) {
            log.error("runScript: script doesn't exist! {}", tempFile.getAbsoluteFile());
            return null;
        }

        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(tempFile.getAbsolutePath());
            if (args != null) {
                commandList.addAll(Arrays.asList(args));
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(commandList);
            Process process = processBuilder.start();
            synchronized (processList) {
                processList.add(process);
            }
            int exitVal = process.waitFor();
            if (exitVal != 0) {
                log.error("runScript: error:{}", exitVal);
            }
            List<String> resultList = readInputStream(process.getInputStream());
            //if (resultList.size() > 0) log.error("runScript: RESULTS: {}", GsonHelper.toJson(resultList));
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
            log.error("runScript: Exception: {}", e.getMessage());
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
