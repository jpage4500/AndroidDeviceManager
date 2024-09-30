package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

import static com.jpage4500.devicemanager.utils.PreferenceUtils.Pref;

public class ConnectDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ConnectDialog.class);

    private JTextField serverField;
    private JTextField portField;

    // used to persist the most recent X wireless devices
    private static class WirelessDevice {
        String serial;
        String model;
        String nickname;
    }

    public static void showConnectDialog(Component frame, DeviceManager.TaskListener listener) {
        ConnectDialog screen = new ConnectDialog();
        Object[] choices = {"Connect", "Cancel"};
        int rc = JOptionPane.showOptionDialog(frame, screen, "Connect to device", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, choices, null);
        if (rc != JOptionPane.YES_OPTION) return;

        String ip = screen.serverField.getText();
        int port;
        try {
            port = Integer.parseInt(screen.portField.getText());
        } catch (NumberFormatException e) {
            log.error("Invalid port: " + screen.portField.getText());
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

        DefaultListModel<String> listModel = new DefaultListModel<>();
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
            listModel.addElement(label);
        }
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        int displayRows = listModel.size();
        if (displayRows > 6) displayRows = 6;
        else if (displayRows < 3) displayRows = 3;
        list.setVisibleRowCount(displayRows);
        list.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                JList list = (JList) e.getComponent();
                list.clearSelection();
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        add(scroll, "growx, span 2, wrap");

        add(new JSeparator(), "growx, spanx, wrap");

        serverField = new JTextField(lastIp);
        portField = new JTextField(String.valueOf(lastPort));

        serverField.setHorizontalAlignment(SwingConstants.RIGHT);
        portField.setHorizontalAlignment(SwingConstants.RIGHT);

        list.addListSelectionListener(e -> {
            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex == -1) return;
            WirelessDevice selectedDevice = recentDeviceList.get(selectedIndex);
            int pos = selectedDevice.serial.indexOf(':');
            serverField.setText(selectedDevice.serial.substring(0, pos));
            portField.setText(selectedDevice.serial.substring(pos + 1));
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int selectedIndex = list.getSelectedIndex();
                if (selectedIndex == -1) return;
                switch (e.getExtendedKeyCode()) {
                    case KeyEvent.VK_DELETE:
                    case KeyEvent.VK_BACK_SPACE:
                        WirelessDevice selectedDevice = recentDeviceList.get(selectedIndex);
                        log.debug("keyPressed: remove index:{}, device:{}", selectedIndex, GsonHelper.toJson(selectedDevice));
                        listModel.remove(selectedIndex);
                        removeWirelessDevice(selectedDevice);
                        break;
                }
            }
        });

        add(new JLabel("IP"), "");
        add(serverField, "al right, width 100:150, wrap");

        add(new JLabel("Port"), "");
        portField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE) {
                    // always allowed
                    return;
                }
                int length = portField.getText().length();
                int selectedLen = TextUtils.length(portField.getSelectedText());
                if (length - selectedLen >= 5) {
                    e.consume();
                } else if (!(c >= '0' && c <= '9')) {
                    e.consume();
                }
            }
        });
        add(portField, "al right, width 100:150, wrap");
    }

    public static List<WirelessDevice> getRecentWirelessDevices() {
        String recentDeviceStr = PreferenceUtils.getPreference(Pref.PREF_RECENT_WIRELESS_DEVICES);
        return GsonHelper.stringToList(recentDeviceStr, WirelessDevice.class);
    }

    public static void addWirelessDevice(Device device) {
        if (!device.isWireless()) return;
        String model = device.getProperty(Device.PROP_MODEL);

        List<WirelessDevice> deviceList = getRecentWirelessDevices();
        deviceList.removeIf(wirelessDevice -> TextUtils.equals(wirelessDevice.serial, device.serial));
        WirelessDevice wd = new WirelessDevice();
        wd.serial = device.serial;
        wd.model = model;
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

