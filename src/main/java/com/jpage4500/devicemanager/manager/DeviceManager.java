package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DeviceManager {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeviceManager.class);

    private static volatile DeviceManager instance;

    private final List<Device> deviceList;
    private final String tempFolder;
    private final List<Process> processList;

    private final ScheduledExecutorService listExecutorService;
    private final ExecutorService detailsExecutorService;
    private final ExecutorService commandExecutorService;

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

    public interface DeviceListUpdatedListener {
        void handleDevicesUpdated(List<Device> deviceList);

        void handleDeviceUpdated(Device device);
    }

    /**
     * starts a scheduled task to list devices every [10] seconds
     */
    public void listDevices(DeviceListUpdatedListener listener) {
        listExecutorService.scheduleWithFixedDelay(() -> {
            listDevicesInternal(listener);
        }, 0, 10, TimeUnit.SECONDS);
    }

    private void getDeviceDetails(Device device, DeviceListUpdatedListener listener) {
        detailsExecutorService.submit(() -> {
            getDeviceDetailsInternal(device, listener);
        });
    }

    public void mirrorDevice(Device device) {
        commandExecutorService.submit(() -> {
            runScript("mirror.sh", device.serial);
            log.debug("mirrorDevice: DONE");
        });
    }

    public void captureScreenshot(Device device) {
        commandExecutorService.submit(() -> {
            runScript("screenshot.sh", device.serial);
            log.debug("captureScreenshot: DONE");
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

    private void listDevicesInternal(DeviceListUpdatedListener listener) {
        List<String> results = runScript("device-list.sh");
        if (results == null) {
            listener.handleDevicesUpdated(null);
            return;
        }
        List<Device> resultList = new ArrayList<>();
        // List of devices attached
        // 04QAX0NRLM             device usb:34603008X product:bonito model:Pixel_3a_XL device:bonito transport_id:3
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
            for (String keyVal : deviceArr) {
                if (keyVal.startsWith("model:")) {
                    device.model = keyVal.substring("model:".length());
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
                        // TODO: update device? should contain the same details (nothing new found in 'adb devices -l')
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
            if (!device.hasFetchedDetails) {
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

    private void getDeviceDetailsInternal(Device device, DeviceListUpdatedListener listener) {
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
                        device.custom1 = val;
                        isUpdated = true;
                        break;
                    case "custom2":
                        device.custom2 = val;
                        isUpdated = true;
                        break;
                }
            }

            if (isUpdated) {
                listener.handleDeviceUpdated(device);
            }
        }
        device.hasFetchedDetails = true;
    }

    private void copyResourcesToFiles() {
        try {
            URL url = getClass().getResource("/scripts");
            Path path = Paths.get(url.toURI());
            Files.walk(path, 1).forEach(p -> {
                String filename = p.toString();
                if (filename.endsWith(".sh")) {
                    copyResourceToFile(p);
                }
            });
        } catch (Exception e) {
            log.error("copyResourcesToFiles: {}", e.getMessage());
        }
    }

    private void copyResourceToFile(Path path) {
        String name = path.toFile().getName();
        File tempFile = new File(tempFolder, name);
        log.debug("copyResource: {} to {}", name, tempFile.getAbsolutePath());
        try {
            InputStream is = getClass().getResourceAsStream("/scripts/" + name);
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            for (String line; (line = r.readLine()) != null; ) {
                sb.append(line).append('\n');
            }
            r.close();
            is.close();

            Files.write(tempFile.toPath(), sb.toString().getBytes(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);

            tempFile.setExecutable(true);
        } catch (Exception e) {
            log.error("copyResource: Exception:{}", e.getMessage());
        }
    }

    private List<String> runScript(String scriptName, String... args) {
        log.debug("runScript: {}, args:{}", scriptName, GsonHelper.toJson(args));
        File tempFile = new File(tempFolder, scriptName);
        if (!tempFile.exists()) {
            log.error("runScript: script doesn't exist! {}", tempFile.getAbsolutePath());
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
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            List<String> resultList = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                log.debug("runScript: {}", line);
                resultList.add(line);
            }
            reader.close();
            int exitVal = process.waitFor();
            if (exitVal != 0) {
                log.error("runScript: error:{}", exitVal);
            }
            synchronized (processList) {
                processList.remove(process);
            }
            return resultList;
        } catch (Exception e) {
            log.error("runScript: {}", e.getMessage());
        }
        return null;
    }
}
