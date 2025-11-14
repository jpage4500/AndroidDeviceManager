package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import io.resourcepool.ssdp.client.SsdpClient;
import io.resourcepool.ssdp.model.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.InetAddress;
import java.util.List;

import static com.jpage4500.devicemanager.utils.PreferenceUtils.Pref;

public class ConnectDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ConnectDialog.class);

    private JButton okButton;
    private HintTextField serverField;
    private HintTextField portField;
    private JList<String> deviceList;
    private DefaultListModel<String> deviceListModel;
    private HoverLabel scanButton;

    private SsdpClient ssdpClient;
    private Timer scanTimer;
    private int scanDots = 0;

    // used to persist the most recent X wireless devices
    private static class WirelessDevice {
        String serial;
        String model;
        String nickname;
    }

    public static void showConnectDialog(Component frame, DeviceManager.TaskListener listener) {
        ConnectDialog dialog = new ConnectDialog();
        dialog.okButton = DialogHelper.createDialogButton("Connect");
        JButton cancelButton = DialogHelper.createDialogButton("Cancel");

        int rc = JOptionPane.showOptionDialog(frame, dialog, "Connect to device", JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, new Object[]{dialog.okButton, cancelButton}, dialog.okButton);
        boolean isOk = (rc == JOptionPane.OK_OPTION);

        dialog.stopDevicesScan();

        if (!isOk) return;

        String ip = dialog.serverField.getCleanText();
        String portStr = dialog.portField.getCleanText();
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            log.error("Invalid port: " + portStr);
            return;
        }

        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_LAST_DEVICE_IP, ip);
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LAST_DEVICE_PORT, port);

        DeviceManager deviceManager = DeviceManager.getInstance();
        deviceManager.connectDevice(ip, port, listener);
    }

    private ConnectDialog() {
        setLayout(new MigLayout("fillx", "[][]"));

        List<WirelessDevice> recentDeviceList = getRecentWirelessDevices();

        String lastIp = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_LAST_DEVICE_IP);
        int lastPort = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LAST_DEVICE_PORT, 5555);

        if (TextUtils.isEmpty(lastIp)) lastIp = "192.168.0.1";

        add(new JLabel("Recent Devices"), "growx, span 2, wrap");

        List<Device> connectedDeviceList = DeviceManager.getInstance().getDevices();

        deviceListModel = new DefaultListModel<>();
        for (WirelessDevice device : recentDeviceList) {
            // only show devices that aren't currently connected
            boolean isConnected = false;
            for (Device connectedDevice : connectedDeviceList) {
                if (connectedDevice.serial.equals(device.serial)) {
                    isConnected = true;
                    break;
                }
            }
            if (isConnected) continue;

            String label = "";
            if (TextUtils.notEmpty(device.nickname)) label += device.nickname;
            else label += device.model;
            label += " - " + device.serial;
            deviceListModel.addElement(label);
        }

        deviceList = new JList<>(deviceListModel);
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceList.setCellRenderer(new AlternatingBackgroundColorRenderer());
        int displayRows = deviceListModel.size();
        if (displayRows > 6) displayRows = 6;
        else if (displayRows < 3) displayRows = 3;
        deviceList.setVisibleRowCount(displayRows);
        deviceList.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                JList list = (JList) e.getComponent();
                list.clearSelection();
            }
        });

        JScrollPane scroll = new JScrollPane(deviceList);
        add(scroll, "growx, span 2, wrap");

        add(new JSeparator(), "growx, spanx, wrap");

        // -- SERVER --
        serverField = new HintTextField("IP Address", text -> {
            enableOkButton();
        });
        serverField.setText(lastIp);

        // -- PORT --
        portField = new HintTextField("Port", null);
        portField.setText(String.valueOf(lastPort));
        portField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE) {
                    // always allowed
                    enableOkButton();
                    return;
                }
                int length = portField.getText().length();
                int selectedLen = TextUtils.length(portField.getSelectedText());
                if (length - selectedLen >= 5) {
                    e.consume();
                } else if (!(c >= '0' && c <= '9')) {
                    e.consume();
                }
                enableOkButton();
            }
        });

        serverField.setHorizontalAlignment(SwingConstants.RIGHT);
        portField.setHorizontalAlignment(SwingConstants.RIGHT);

        deviceList.addListSelectionListener(e -> {
            int selectedIndex = deviceList.getSelectedIndex();
            if (selectedIndex == -1) return;
            WirelessDevice selectedDevice = recentDeviceList.get(selectedIndex);
            int pos = selectedDevice.serial.indexOf(':');
            serverField.setText(selectedDevice.serial.substring(0, pos));
            portField.setText(selectedDevice.serial.substring(pos + 1));
        });
        deviceList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int selectedIndex = deviceList.getSelectedIndex();
                if (selectedIndex == -1) return;
                switch (e.getExtendedKeyCode()) {
                    case KeyEvent.VK_DELETE:
                    case KeyEvent.VK_BACK_SPACE:
                        WirelessDevice selectedDevice = recentDeviceList.get(selectedIndex);
                        log.debug("keyPressed: remove index:{}, device:{}", selectedIndex, GsonHelper.toJson(selectedDevice));
                        deviceListModel.remove(selectedIndex);
                        removeWirelessDevice(selectedDevice);
                        break;
                }
            }
        });

        add(new JLabel("IP"), "");
        add(serverField, "al right, width 100:150, wrap");

        add(new JLabel("Port"), "");
        add(portField, "al right, width 100:150, wrap");

        scanButton = new HoverLabel("Scan for devices");
        scanButton.setForeground(Color.BLUE);
        scanButton.addActionListener(e -> startDevicesScan());
        add(scanButton, "gaptop 5, span 2");
    }

    private void startDevicesScan() {
        if (ssdpClient != null) {
            stopDevicesScan();
            return;
        }
        ssdpClient = SsdpClient.create();
        DiscoveryRequest all = SsdpRequest.discoverAll();
        ssdpClient.discoverServices(all, new DiscoveryListener() {
            @Override
            public void onServiceDiscovered(SsdpService service) {
                log.trace("onServiceDiscovered: found: {}", service);
                InetAddress remoteIp = service.getRemoteIp();
                String serviceType = service.getServiceType();
                if (remoteIp != null && TextUtils.containsAny(serviceType, true, "android", "shield")) {
                    String hostName = remoteIp.getHostName();
                    boolean isFound = false;
                    for (int i = 0; i < deviceListModel.getSize(); i++) {
                        String label = deviceListModel.getElementAt(i);
                        if (TextUtils.contains(label, hostName)) {
                            isFound = true;
                            break;
                        }
                    }
                    if (!isFound) {
                        String entry = "SSDP - " + hostName + ":5555";
                        deviceListModel.addElement(entry);
                    }
                }
            }

            @Override
            public void onServiceAnnouncement(SsdpServiceAnnouncement announcement) {
                log.trace("onServiceAnnouncement: {}", announcement);
            }

            @Override
            public void onFailed(Exception e) {
                log.trace("onFailed: {}", e.getMessage());
            }
        });
        scanButton.setText("Stop Scanning");
        scanDots = 0;
        scanTimer = new Timer(500, e -> {
            scanDots = (scanDots + 1) % 5;
            StringBuilder label = new StringBuilder("Stop Scanning");
            for (int i = 0; i < scanDots; i++) label.append(".");
            scanButton.setText(label.toString());
        });
        scanTimer.start();
    }

    private void stopDevicesScan() {
        if (ssdpClient != null) {
            ssdpClient.stopDiscovery();
            ssdpClient = null;
            scanButton.setText("Scan for devices");
        }
        if (scanTimer != null) {
            scanTimer.stop();
            scanTimer = null;
        }
    }

    private void enableOkButton() {
        if (okButton == null) return;
        if (serverField == null || portField == null) return;
        String ip = serverField.getCleanText();
        String port = portField.getCleanText();
        boolean isEnabled = (!TextUtils.isEmptyAny(ip, port));
        if (isEnabled) {
            try {
                Integer.parseInt(port);
            } catch (NumberFormatException e) {
                isEnabled = false;
            }
        }
        okButton.setEnabled(isEnabled);
    }

    public static List<WirelessDevice> getRecentWirelessDevices() {
        String recentDeviceStr = PreferenceUtils.getPreference(Pref.PREF_RECENT_WIRELESS_DEVICES);
        return GsonHelper.stringToList(recentDeviceStr, WirelessDevice.class);
    }

    public static void addWirelessDevice(Device device) {
        if (!device.isWireless()) return;
        List<WirelessDevice> deviceList = getRecentWirelessDevices();
        deviceList.removeIf(wirelessDevice -> TextUtils.equals(wirelessDevice.serial, device.serial));
        WirelessDevice wd = new WirelessDevice();
        wd.serial = device.serial;
        wd.model = device.model;
        wd.nickname = device.nickname;
        // add to top of list
        deviceList.add(0, wd);

        if (deviceList.size() > 10) {
            deviceList.remove(deviceList.size() - 1);
        }
        PreferenceUtils.setPreference(Pref.PREF_RECENT_WIRELESS_DEVICES, GsonHelper.toJson(deviceList));
    }

    public static void removeWirelessDevice(WirelessDevice wirelessDevice) {
        List<WirelessDevice> deviceList = getRecentWirelessDevices();
        deviceList.removeIf(device -> TextUtils.equals(device.serial, wirelessDevice.serial));
        PreferenceUtils.setPreference(Pref.PREF_RECENT_WIRELESS_DEVICES, GsonHelper.toJson(deviceList));
    }
}
