package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.BatteryInfo;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import com.jpage4500.devicemanager.utils.Utils;

import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

import java.awt.*;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

/**
 * details for a single device (serial, model, battery, ..) plus links into the deeper views
 * <p>
 * one window per device (see {@link AppController}) so several can be opened side by side and
 * compared. unlike the dialog this replaced, the rows are rebuilt whenever the device is refreshed, so
 * values like battery level and free space stay current while the window is open.
 */
public class DeviceInfoScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(DeviceInfoScreen.class);

    private static final String PACKAGE_PREFIX = "package:";

    private final JPanel detailPanel;

    public DeviceInfoScreen(App app, Device device) {
        super(app, device, "device-info-" + device.serial, 400, 480);
        detailPanel = new JPanel(new MigLayout("wrap 1", "[grow]"));

        setupMenuBar();
        JScrollPane scrollPane = new JScrollPane(detailPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        setContentPane(scrollPane);
        bindEscapeToClose();
        updateDevice(device);
    }

    public void updateDevice(Device device) {
        this.device = device;
        if (device.isOnline) {
            setTitle("Device: " + device.getDisplayName());
        } else {
            setTitle("OFFLINE [" + device.getDisplayName() + "]");
        }
        refreshDetails();
    }

    private void setupMenuBar() {
        JMenu windowMenu = buildWindowMenu();

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    /**
     * rebuild the rows from the current device state
     * <p>
     * the old dialog snapshotted these values; as a window that stays open it's worth re-reading them
     * every refresh so battery level, temperature and free space don't go stale on screen
     */
    private void refreshDetails() {
        detailPanel.removeAll();

        addDetail("Serial", device.serial);
        addDetail("Nickname", device.nickname);
        addDetail("Model", device.model);
        addDetail("Phone", device.phone);
        addDetail("IMEI", device.imei);
        addDetail("Carrier", device.carrier);
        addDetail("OS", device.os);
        addDetail("SDK", device.sdk);
        addDetail("Free Space", FileUtils.bytesToDisplayString(device.freeSpace));
        addDetail("Custom1", device.getCustomProperty(Device.CUST_PROP_1));
        addDetail("Custom2", device.getCustomProperty(Device.CUST_PROP_2));

        // battery
        BatteryInfo batteryInfo = device.batteryInfo;
        if (device.batteryLevel != null) addDetail("Battery", device.batteryLevel + "%");
        if (device.powerStatus != Device.PowerStatus.POWER_NONE) {
            // POWER_USB -> USB
            addDetail("Charging", TextUtils.split(device.powerStatus.name(), "_", 1));
        }
        if (batteryInfo != null) {
            addDetail("Temperature", batteryInfo.getTempDisplay());
            addDetail("Voltage", batteryInfo.getVoltageDisplay());
            addDetail("Current", batteryInfo.getCurrentDisplay());
        }

        ImageIcon icon = UiUtils.getImageIcon(Icons.ARROW_RIGHT, UiUtils.IMG_SIZE_SMALL);

        // battery level/temperature charts; reads the history off the device on demand
        addLink("Battery Details", icon, () -> app.showBattery(device));
        addLink("Device Properties", icon, this::showDeviceProperties);
        addLink("Installed Apps / Versions", icon, this::showInstalledApps);

        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void addDetail(String label, String value) {
        if (!TextUtils.isEmpty(value)) {
            detailPanel.add(new JLabel(label + ": " + value), "wrap");
        }
    }

    private void addLink(String label, ImageIcon icon, Runnable action) {
        HoverLabel hoverLabel = new HoverLabel(label, icon);
        UiUtils.addLeftClickListener(hoverLabel, mouseEvent -> action.run());
        detailPanel.add(hoverLabel, "wrap");
    }

    private void showDeviceProperties() {
        if (!device.isOnline) return;
        // fetch all device properties & display
        DeviceManager.getInstance().fetchDeviceProperties(device, (isSuccess, propMap) -> {
            TreeMap<String, String> sortedPropMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            sortedPropMap.putAll(propMap);
            DialogHelper.showListDialog(this, "Device Properties", sortedPropMap, null);
        });
    }

    private void showInstalledApps() {
        DeviceManager.getInstance().getInstalledApps(device, appSet -> {
            final Map<String, String> appMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            // convert set to map
            for (String app : appSet) appMap.put(app, null);
            DialogHelper.showListDialog(this, "Installed Apps", appMap, new DialogHelper.ListListener() {
                @Override
                public void handleDoubleClick(String key, String value) {
                    log.trace("showInstalledApps: click: {}", key);
                    DeviceManager.getInstance().fetchAppVersion(device, key, version -> {
                        String text = String.format("%s = %s", key, version);
                        DialogHelper.showTextDialog(DeviceInfoScreen.this, key, text);
                    });
                }

                @Override
                public void handleRightClick(String key, String value, JPopupMenu popupMenu) {
                    UiUtils.addPopupMenuItem(popupMenu, "Download App", actionEvent -> extractApk(key));
                }
            });
        });
    }

    private void extractApk(String key) {
        String command = "pm path " + key;
        DeviceManager deviceManager = DeviceManager.getInstance();
        deviceManager.runCustomCommand(device, command, (result) -> {
            if (!result.isSuccess) {
                String msg = "Unable to download " + key + "\n\n" + result;
                DialogHelper.showDialog(this, "Error", msg);
                return;
            }
            // download to new folder
            String downloadFolder = Utils.getDownloadFolder();
            File appFolder = new File(downloadFolder, key);
            appFolder.mkdirs();

            for (String path : result.resultList) {
                if (!TextUtils.startsWith(path, PACKAGE_PREFIX)) {
                    log.trace("extractApk: BAD LINE: {}", path);
                    continue;
                }
                path = path.substring(PACKAGE_PREFIX.length());
                int pos = path.lastIndexOf('/');
                if (pos < 1) continue;
                DeviceFile file = new DeviceFile();
                file.name = path.substring(pos + 1);
                path = path.substring(0, pos);

                File saveFile = new File(appFolder, file.name);
                deviceManager.downloadFile(device, path, file, saveFile, false, (isSuccess, error) -> {
                    log.trace("extractApk: {}: {}", isSuccess, error);
                });
            }
        });
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        if (state == WindowState.CLOSING) {
            closeWindow();
        }
    }

    @Override
    public void closeWindow() {
        log.trace("closeWindow: {}", device.getDisplayName());
        saveFrameSize();
        app.onDeviceInfoClosed(device.serial);
        dispose();
    }
}
