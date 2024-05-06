package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.utils.GsonHelper;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class ConnectScreen extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ConnectScreen.class);
    public static final String PREF_RECENT_WIRELESS_DEVICES = "PREF_RECENT_WIRELESS_DEVICES";
    public static final String PREF_LAST_DEVICE_IP = "PREF_LAST_DEVICE_IP";
    public static final String PREF_LAST_DEVICE_PORT = "PREF_LAST_DEVICE_PORT";

    private JTextField serverField;
    private JTextField portField;

    public static void showConnectDialog(Component frame, DeviceManager.TaskListener listener) {
        ConnectScreen screen = new ConnectScreen(frame);
        Object[] choices = {"Connect", "Cancel"};
        int rc = JOptionPane.showOptionDialog(frame, screen, "Connect to device", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, choices, null);
        if (rc != JOptionPane.YES_OPTION) return;

        String ip = screen.serverField.getText();
        String port = screen.portField.getText();
        Preferences preferences = Preferences.userRoot();
        preferences.put(PREF_LAST_DEVICE_IP, ip);
        preferences.put(PREF_LAST_DEVICE_PORT, port);

        DeviceManager deviceManager = DeviceManager.getInstance();
        deviceManager.connectDevice(ip + ":" + port, listener);
    }

    public ConnectScreen(Component frame) {
        setLayout(new MigLayout("fillx", "[][]"));

        Preferences preferences = Preferences.userRoot();
        String recentDeviceStr = preferences.get(PREF_RECENT_WIRELESS_DEVICES, null);
        List<Device> recentDeviceList = GsonHelper.stringToList(recentDeviceStr, Device.class);
        String lastIp = preferences.get(PREF_LAST_DEVICE_IP, "192.168.0.1");
        String lastPort = preferences.get(PREF_LAST_DEVICE_PORT, "5555");

        add(new JLabel("Recent Devices"), "growx, span 2, wrap");

        List<String> listData = new ArrayList<>();
        for (Device device : recentDeviceList) {
            listData.add(device.serial + " - " + device.model);
        }
        JList<String> list = new JList<>(listData.toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        list.setVisibleRowCount(3);
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
        portField = new JTextField(lastPort);

        serverField.setHorizontalAlignment(SwingConstants.RIGHT);
        portField.setHorizontalAlignment(SwingConstants.RIGHT);

        list.addListSelectionListener(e -> {
            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex == -1) return;
            Device selectedDevice = recentDeviceList.get(selectedIndex);
            int pos = selectedDevice.serial.indexOf(':');
            serverField.setText(selectedDevice.serial.substring(0, pos));
            portField.setText(selectedDevice.serial.substring(pos + 1));
        });

        add(new JLabel("IP"), "");
        add(serverField, "al right, wrap");

        add(new JLabel("Port"), "");
        portField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE) {
                    // always allowed
                    return;
                }
                int length = portField.getText().length();
                if (length >= 5) {
                    e.consume();
                } else if (!(c >= '0' && c <= '9')) {
                    e.consume();
                }
            }
        });
        add(portField, "al right, wrap");
    }
}

