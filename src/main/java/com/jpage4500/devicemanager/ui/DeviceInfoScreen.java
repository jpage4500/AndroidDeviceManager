package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.BatteryInfo;
import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.GaugePanel;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.ui.views.PropertyListPanel;
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
import java.util.ArrayList;
import java.util.List;
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

    // loaded once: the rows are rebuilt on every device refresh, and re-scaling the png each time
    // would be wasted work
    private static final ImageIcon LINK_ICON = UiUtils.getImageIcon(Icons.ARROW_RIGHT, UiUtils.IMG_SIZE_SMALL, UiUtils.IMG_SIZE_SMALL, Colors.COLOR_CARD_ACCENT);

    private final JPanel detailPanel;

    public DeviceInfoScreen(App app, Device device) {
        super(app, device, "device-info-" + device.serial, 420, 640);
        detailPanel = new JPanel(new MigLayout("wrap 1, fillx, insets 10, gapy 10", "[grow]"));
        // cards are drawn a shade lighter than this, which is what makes them read as cards
        detailPanel.setBackground(Colors.COLOR_CARD_PAGE);

        setupMenuBar();
        JScrollPane scrollPane = new JScrollPane(detailPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        // detailPanel is only as tall as its rows, so the space below it is the viewport's to paint
        scrollPane.getViewport().setBackground(Colors.COLOR_CARD_PAGE);
        setContentPane(scrollPane);
        bindEscapeToClose();
        updateDevice(device);
    }

    @Override
    public void updateDevice(Device device) {
        super.updateDevice(device);
        refreshDetails();
    }

    @Override
    protected String buildTitle() {
        return deviceTitle("Device");
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

        addProperties();
        // free space and battery level are fractions of a whole, so they get a gauge each rather than
        // a row of text - "22.4 GB free" doesn't say whether the device is nearly full
        addStorageGauge();
        addBatteryGauge();
        addLinks();

        detailPanel.revalidate();
        detailPanel.repaint();
    }

    private void addProperties() {
        PropertyListPanel properties = new PropertyListPanel();
        properties.addProperty("Serial", device.serial);
        properties.addProperty("Nickname", device.nickname);
        properties.addProperty("Model", device.model);
        properties.addProperty("Phone", device.phone);
        properties.addProperty("IMEI", device.imei);
        properties.addProperty("Carrier", device.carrier);
        properties.addProperty("OS", device.os + " (SDK " + device.sdk + ")");
        //properties.addProperty("SDK", device.sdk);
        properties.addProperty("Custom1", device.getCustomProperty(Device.CUST_PROP_1));
        properties.addProperty("Custom2", device.getCustomProperty(Device.CUST_PROP_2));
        // an offline device may not have reported anything yet
        if (!properties.isEmpty()) detailPanel.add(properties, "growx");
    }

    /**
     * internal storage, as the percent in use; opens the file browser when clicked
     * <p>
     * the total size comes from the same "df" line as the free space, but an unexpected format there
     * only costs the bar - the free space is still shown
     */
    private void addStorageGauge() {
        if (device.freeSpace == null || device.freeSpace <= 0) return;
        // same X.XG format the device list's free space column uses, so the 2 values read as a pair
        List<String> partList = new ArrayList<>();
        addPart(partList, "Free", FileUtils.bytesToGigDisplayString(device.freeSpace));
        if (device.totalSpace != null) addPart(partList, "Total", FileUtils.bytesToGigDisplayString(device.totalSpace));

        GaugePanel gauge = new GaugePanel(Icons.MEMORY, Colors.COLOR_CARD_ACCENT, "Internal Storage",
            device.getStorageUsedPercent(), TextUtils.join(partList, ", "));
        gauge.setToolTipText("Browse device files");
        gauge.setClickAction(() -> app.showFileBrowser(device));
        detailPanel.add(gauge, "growx");
    }

    /**
     * battery level, with the rest of "dumpsys battery" on the lines below it; opens the level and
     * temperature charts when clicked
     * <p>
     * the icon is the same level-colored one the device list uses, so a nearly empty battery is red
     * here too
     */
    private void addBatteryGauge() {
        if (device.batteryLevel == null) return;
        List<String> firstLine = new ArrayList<>();
        List<String> secondLine = new ArrayList<>();
        BatteryInfo batteryInfo = device.batteryInfo;
        if (batteryInfo != null) {
            addPart(firstLine, "Voltage", batteryInfo.getVoltageDisplay());
            addPart(firstLine, "Temp", batteryInfo.getTempDisplay());
            addPart(secondLine, "Current", batteryInfo.getCurrentDisplay());
        }
        if (device.powerStatus != Device.PowerStatus.POWER_NONE) {
            // POWER_USB -> USB
            addPart(secondLine, "Charging", TextUtils.split(device.powerStatus.name(), "_", 1));
        }

        GaugePanel gauge = new GaugePanel(Device.getBatteryIcon(device.batteryLevel), null, "Battery",
            device.batteryLevel, TextUtils.join(firstLine, ", "), TextUtils.join(secondLine, ", "));
        gauge.setToolTipText("Battery level and temperature history");
        // reads the history off the device on demand
        gauge.setClickAction(() -> app.showBattery(device));
        detailPanel.add(gauge, "growx");
    }

    /**
     * the deeper views, as one card of rows
     */
    private void addLinks() {
        PropertyListPanel links = new PropertyListPanel();
        addLink(links, "Device Properties", this::showDeviceProperties);
        addLink(links, "Installed Apps / Versions", this::showInstalledApps);
        detailPanel.add(links, "growx");
    }

    /**
     * add "Voltage: 4136 mV" to a detail line, or nothing when the device didn't report a value
     */
    private void addPart(List<String> partList, String label, String value) {
        if (TextUtils.notEmpty(value)) partList.add(label + ": " + value);
    }

    private void addLink(PropertyListPanel panel, String label, Runnable action) {
        HoverLabel hoverLabel = new HoverLabel(label, LINK_ICON);
        hoverLabel.setForeground(Colors.COLOR_CARD_ACCENT);
        // it's a JButton underneath, which would otherwise center its label in the full card width
        hoverLabel.setHorizontalAlignment(SwingConstants.LEFT);
        UiUtils.addLeftClickListener(hoverLabel, mouseEvent -> action.run());
        panel.addRow(hoverLabel);
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

}
