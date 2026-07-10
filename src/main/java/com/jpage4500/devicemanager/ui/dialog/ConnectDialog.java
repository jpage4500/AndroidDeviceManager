package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.CheckboxCellRenderer;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class ConnectDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ConnectDialog.class);

    // connection status constants
    private static final String STATUS_CONNECTED = "Connected";
    private static final String STATUS_DISCONNECTED = "Disconnected";
    private static final String STATUS_CONNECTING = "Connecting...";
    private static final String STATUS_DISCONNECTING = "Disconnecting...";

    // default values
    private static final int DEFAULT_PORT = 5555;
    private static final int MAX_RECENT_DEVICES = 10;
    private static final int REFRESH_DELAY_MS = 2000;
    private static final int DEVICE_NAME_POLL_INTERVAL_MS = 1000; // Poll every 1 second
    private static final int DEVICE_NAME_POLL_MAX_ATTEMPTS = 30; // Max 30 seconds

    // table column widths
    private static final int COL_WIDTH_CHECKBOX = 40;
    private static final int COL_WIDTH_NAME = 150;
    private static final int COL_WIDTH_IP = 120;
    private static final int COL_WIDTH_PORT = 60;
    private static final int COL_WIDTH_STATUS = 100;
    private static final int TABLE_WIDTH = 550;
    private static final int TABLE_HEIGHT = 200;
    private static final int TABLE_ROW_HEIGHT = 30;

    public static final String DEFAULT_HOST = "192.168.0.100";

    private HintTextField serverField;
    private HintTextField portField;
    private JButton connectButton;
    private JTable deviceTable;
    private DeviceTableModel tableModel;

    // used to persist the most recent X wireless devices
    private static class WirelessDevice {
        String serial;
        String model;
        String nickname;
        boolean isConnected;
        String connectionStatus = STATUS_DISCONNECTED;

        public String getName() {
            return TextUtils.firstValid(nickname, model, serial);
        }

        @Override
        public String toString() {
            return GsonHelper.toJson(this);
        }
    }

    public static void showConnectDialog(Component frame, DeviceManager.TaskListener listener) {
        ConnectDialog dialog = new ConnectDialog();
        DialogHelper.showCustomDialog(frame, dialog, "Connect to ADB Wireless Device", new String[]{});
    }

    private ConnectDialog() {
        setLayout(new MigLayout("fillx, insets 10", "[grow]"));

        // create table
        tableModel = new DeviceTableModel();
        deviceTable = new JTable(tableModel);
        deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.setRowHeight(TABLE_ROW_HEIGHT);
        deviceTable.getColumnModel().getColumn(DeviceColumn.CONNECTED.ordinal()).setPreferredWidth(COL_WIDTH_CHECKBOX);
        deviceTable.getColumnModel().getColumn(DeviceColumn.NAME.ordinal()).setPreferredWidth(COL_WIDTH_NAME);
        deviceTable.getColumnModel().getColumn(DeviceColumn.IP.ordinal()).setPreferredWidth(COL_WIDTH_IP);
        deviceTable.getColumnModel().getColumn(DeviceColumn.PORT.ordinal()).setPreferredWidth(COL_WIDTH_PORT);
        deviceTable.getColumnModel().getColumn(DeviceColumn.STATUS.ordinal()).setPreferredWidth(COL_WIDTH_STATUS);

        // custom renderer for checkbox column
        deviceTable.getColumnModel().getColumn(DeviceColumn.CONNECTED.ordinal()).setCellRenderer(new CheckboxCellRenderer());
        deviceTable.getColumnModel().getColumn(DeviceColumn.STATUS.ordinal()).setCellRenderer(new ConnectionStatusRenderer());

        // add right-click context menu for disconnected devices
        UiUtils.addRightClickListener(deviceTable, e -> {
            int row = deviceTable.rowAtPoint(e.getPoint());
            if (row >= 0) {
                deviceTable.setRowSelectionInterval(row, row);
                WirelessDevice device = tableModel.getDeviceAt(row);
                if (device != null && !device.isConnected) {
                    // only show menu for disconnected devices
                    JPopupMenu popup = new JPopupMenu();
                    UiUtils.addPopupMenuItem(popup, "Delete", Icons.DELETE, event -> handleDeleteDevice(device));
                    popup.show(deviceTable, e.getX(), e.getY());
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(deviceTable);
        scrollPane.setPreferredSize(new Dimension(TABLE_WIDTH, TABLE_HEIGHT));
        add(scrollPane, "growx, wrap");

        add(new JSeparator(), "growx, wrap, gaptop 10, gapbottom 10");

        // manual connect section
        String lastIp = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_LAST_DEVICE_IP, DEFAULT_HOST);
        int lastPort = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_LAST_DEVICE_PORT, DEFAULT_PORT);

        JPanel manualPanel = new JPanel(new MigLayout("fillx, insets 0", "[][grow][][grow][]"));

        manualPanel.add(new JLabel("IP:"), "");

        serverField = new HintTextField(DEFAULT_HOST, text -> updateConnectButton());
        serverField.setText(lastIp);
        serverField.setHorizontalAlignment(SwingConstants.LEFT);
        // ENTER on the IP field connects (when enabled)
        serverField.addActionListener(e -> handleEnterKey());
        // when dialog is shown, focus the IP field and select the last octet (ie: the "109" in 192.168.0.109)
        // so the user can quickly type just the last octet and connect
        serverField.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                serverField.removeAncestorListener(this);
                SwingUtilities.invokeLater(() -> {
                    serverField.requestFocusInWindow();
                    selectIpLastOctet();
                });
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
        manualPanel.add(serverField, "growx");

        manualPanel.add(new JLabel("Port:"), "gapleft 10");

        portField = new HintTextField(String.valueOf(DEFAULT_PORT), null);
        portField.setText(String.valueOf(lastPort));
        portField.setHorizontalAlignment(SwingConstants.LEFT);
        portField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (c == KeyEvent.VK_BACK_SPACE || c == KeyEvent.VK_DELETE) {
                    updateConnectButton();
                    return;
                }
                int length = portField.getText().length();
                int selectedLen = TextUtils.length(portField.getSelectedText());
                if (length - selectedLen >= 5) {
                    e.consume();
                } else if (!(c >= '0' && c <= '9')) {
                    e.consume();
                }
                updateConnectButton();
            }
        });
        // ENTER on the Port field connects (when enabled)
        portField.addActionListener(e -> handleEnterKey());
        manualPanel.add(portField, "growx");

        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> handleManualConnect());
        manualPanel.add(connectButton, "gapleft 10");

        add(manualPanel, "growx, wrap");

        // bottom button row: 'Connect Remote Server' (left) and 'Pair with QR Code' (right)
        JPanel bottomPanel = new JPanel(new MigLayout("fillx, insets 0"));

        JButton serverButton = new JButton("Connect Remote Server", UiUtils.getImageIcon(Icons.SERVER, UiUtils.IMG_SIZE_ICON));
        serverButton.addActionListener(e -> RemoteServerDialog.showRemoteServerDialog(this));
        bottomPanel.add(serverButton, "");

        JButton qrButton = new JButton("Pair with QR Code", UiUtils.getImageIcon(Icons.QR_CODE, UiUtils.IMG_SIZE_ICON));
        qrButton.addActionListener(e -> {
            QrCodeDialog.showQrCodeDialog(this, this::refreshTable);
        });
        bottomPanel.add(qrButton, "pushx, align right");

        add(bottomPanel, "growx, gaptop 10");

        updateConnectButton();

        refreshTable();
    }

    private void updateConnectButton() {
        if (connectButton == null || serverField == null || portField == null) return;
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
        connectButton.setEnabled(isEnabled);
    }

    /**
     * ENTER pressed on the IP or Port field - connect if the connect button is enabled
     */
    private void handleEnterKey() {
        if (connectButton != null && connectButton.isEnabled()) {
            handleManualConnect();
        }
    }

    /**
     * select the last octet of the IP address (ie: the "109" in "192.168.0.109") so the
     * user can quickly type just the last octet
     */
    private void selectIpLastOctet() {
        String text = serverField.getText();
        if (TextUtils.isEmpty(text)) return;
        int lastDot = text.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < text.length()) {
            serverField.select(lastDot + 1, text.length());
        } else {
            serverField.selectAll();
        }
    }

    private void handleManualConnect() {
        String ip = serverField.getCleanText();
        String portStr = portField.getCleanText();
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            log.error("handleManualConnect: Invalid port: " + portStr);
            return;
        }

        // check if device already connected
        String serial = ip + ":" + port;
        for (WirelessDevice device : tableModel.devices) {
            if (device.isConnected && device.serial.equals(serial)) {
                DialogHelper.showDialog(this, "Connect Device", "Device already connected");
                return;
            }
        }

        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_LAST_DEVICE_IP, ip);
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_LAST_DEVICE_PORT, port);

        connectButton.setEnabled(false);
        log.trace("handleManualConnect: connecting to: {}, {}", ip, port);
        DeviceManager deviceManager = DeviceManager.getInstance();
        deviceManager.connectDevice(ip, port, (success, result) -> {
            updateConnectButton();
            if (success) {
                log.debug("handleManualConnect: Connected to {}:{}", ip, port);
                // clear fields and refresh table
                SwingUtilities.invokeLater(() -> {
                    serverField.setText("");
                    portField.setText(String.valueOf(DEFAULT_PORT));
                    refreshTable();

                    startDeviceNamePolling();
                });
            } else {
                log.error("handleManualConnect: Failed to connect to {}:{}", ip, port);
                String msg = "Failed to connect to device.";
                if (TextUtils.notEmpty(result)) msg += " " + result;
                DialogHelper.showDialog(this, "Connect Device", msg, true);
            }
        });
    }

    private void refreshTable() {
        List<Device> connectedDeviceList = DeviceManager.getInstance().getDevices();
        List<WirelessDevice> recentDeviceList = getRecentWirelessDevices();
        List<WirelessDevice> displayDeviceList = new ArrayList<>();

        // add currently connected wireless devices
        for (Device device : connectedDeviceList) {
            if (device.isWireless()) {
                WirelessDevice wd = new WirelessDevice();
                wd.serial = device.serial;
                wd.model = device.model;
                wd.nickname = device.nickname;
                wd.isConnected = true;
                wd.connectionStatus = STATUS_CONNECTED;
                displayDeviceList.add(wd);
            }
        }

        // add recent devices that aren't currently connected
        for (WirelessDevice recentDevice : recentDeviceList) {
            boolean alreadyAdded = false;
            for (WirelessDevice displayDevice : displayDeviceList) {
                if (displayDevice.serial.equals(recentDevice.serial)) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (!alreadyAdded) {
                recentDevice.isConnected = false;
                recentDevice.connectionStatus = STATUS_DISCONNECTED;
                displayDeviceList.add(recentDevice);
            }
        }

        tableModel.setDevices(displayDeviceList);
    }

    /**
     * Handle deleting a disconnected device from the list
     */
    private void handleDeleteDevice(WirelessDevice device) {
        // confirm deletion
        String message = "Delete device '" + device.getName() + "' (" + device.serial + ") from the list?";
        if (DialogHelper.showConfirmDialog(this, "Confirm Delete", message)) {
            log.debug("handleDeleteDevice: Deleting device: {}", device);
            removeWirelessDevice(device);
            refreshTable();
        }
    }

    /**
     * check if device has a valid name
     */
    private boolean hasDeviceName(WirelessDevice device) {
        return TextUtils.notEmpty(device.nickname) || TextUtils.notEmpty(device.model);
    }

    /**
     * look for any devices that don't have a nickname or model set yet (it takes a few seconds after connecting)
     */
    private void startDeviceNamePolling() {
        // Start polling for device name
        List<WirelessDevice> displayDevices = tableModel.devices;
        for (WirelessDevice device : displayDevices) {
            if (device.isConnected && !hasDeviceName(device)) {
                startDeviceNamePolling(device.serial);
                break;
            }
        }
    }

    /**
     * start polling timer to refresh table until device name is populated
     */
    private void startDeviceNamePolling(String serial) {
        final int[] attemptCount = {0};

        Timer pollTimer = new Timer(DEVICE_NAME_POLL_INTERVAL_MS, null);
        pollTimer.addActionListener(e -> {
            attemptCount[0]++;

            // Check if we've exceeded max attempts
            if (attemptCount[0] > DEVICE_NAME_POLL_MAX_ATTEMPTS) {
                pollTimer.stop();
                log.trace("startDeviceNamePolling: STOP: {} attempts for {}", attemptCount[0], serial);
                return;
            }

            // Refresh table to get latest device info
            refreshTable();

            // Check if device now has a name
            List<WirelessDevice> displayDevices = tableModel.devices;
            for (WirelessDevice device : displayDevices) {
                if (device.serial.equals(serial) && hasDeviceName(device)) {
                    pollTimer.stop();
                    log.trace("startDeviceNamePolling: FOUND: #{}, {}", attemptCount[0], device);
                    return;
                }
            }
        });

        pollTimer.setRepeats(true);
        pollTimer.start();
        log.trace("startDeviceNamePolling: start polling: {}", serial);
    }

    public static List<WirelessDevice> getRecentWirelessDevices() {
        String recentDeviceStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_RECENT_WIRELESS_DEVICES);
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

        if (deviceList.size() > MAX_RECENT_DEVICES) {
            deviceList.remove(deviceList.size() - 1);
        }
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_RECENT_WIRELESS_DEVICES, GsonHelper.toJson(deviceList));
    }

    public static void removeWirelessDevice(WirelessDevice wirelessDevice) {
        List<WirelessDevice> deviceList = getRecentWirelessDevices();
        deviceList.removeIf(device -> TextUtils.equals(device.serial, wirelessDevice.serial));
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_RECENT_WIRELESS_DEVICES, GsonHelper.toJson(deviceList));
    }

    /**
     * extract IP address from serial (format: "192.168.0.100:5555")
     */
    private static String getIpFromSerial(String serial) {
        int pos = serial.indexOf(':');
        return pos > 0 ? serial.substring(0, pos) : serial;
    }

    /**
     * extract port from serial (format: "192.168.0.100:5555")
     */
    private static String getPortFromSerial(String serial) {
        int pos = serial.indexOf(':');
        return pos > 0 ? serial.substring(pos + 1) : "";
    }

    /**
     * Extract port as integer from serial
     */
    private static int getPortIntFromSerial(String serial) {
        String portStr = getPortFromSerial(serial);
        try {
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    enum DeviceColumn {
        CONNECTED("✓"),
        NAME("Name"),
        IP("IP"),
        PORT("Port"),
        STATUS("Status");

        final String label;

        DeviceColumn(String label) {
            this.label = label;
        }
    }

    /**
     * table model for device list
     */
    private class DeviceTableModel extends AbstractTableModel {
        private final DeviceColumn[] columns = DeviceColumn.values();
        private List<WirelessDevice> devices = new ArrayList<>();

        public void setDevices(List<WirelessDevice> devices) {
            this.devices = devices != null ? devices : new ArrayList<>();
            fireTableDataChanged();
        }

        public WirelessDevice getDeviceAt(int row) {
            if (row >= 0 && row < devices.size()) {
                return devices.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return devices.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column].label;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            WirelessDevice device = devices.get(rowIndex);
            DeviceColumn col = columns[columnIndex];
            return switch (col) {
                case CONNECTED -> device.isConnected;
                case NAME -> device.getName();
                case IP -> getIpFromSerial(device.serial);
                case PORT -> getPortFromSerial(device.serial);
                case STATUS -> device.connectionStatus;
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            DeviceColumn col = columns[columnIndex];
            if (col == DeviceColumn.CONNECTED) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columns[columnIndex] != DeviceColumn.CONNECTED) {
                return false;
            }
            // disable checkbox while connecting or disconnecting
            WirelessDevice device = devices.get(rowIndex);
            String status = device.connectionStatus;
            return !STATUS_CONNECTING.equals(status) && !STATUS_DISCONNECTING.equals(status);
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columns[columnIndex] == DeviceColumn.CONNECTED) {
                WirelessDevice device = devices.get(rowIndex);
                boolean shouldBeConnected = (Boolean) value;

                if (shouldBeConnected && !device.isConnected) {
                    handleConnect(device, rowIndex);
                } else if (!shouldBeConnected && device.isConnected) {
                    handleDisconnect(device, rowIndex);
                }
            }
        }

        /**
         * handle connecting to a device
         */
        private void handleConnect(WirelessDevice device, int rowIndex) {
            String ip = getIpFromSerial(device.serial);
            int port = getPortIntFromSerial(device.serial);

            // set status to "Connecting..."
            device.connectionStatus = STATUS_CONNECTING;
            fireTableRowsUpdated(rowIndex, rowIndex);
            log.trace("handleConnect: connecting to: {}", device);

            DeviceManager.getInstance().connectDevice(ip, port, (success, result) -> {
                if (success) {
                    device.isConnected = true;
                    device.connectionStatus = STATUS_CONNECTED;
                    log.debug("handleConnect: Connected to {}", device);

                    SwingUtilities.invokeLater(() -> {
                        fireTableRowsUpdated(rowIndex, rowIndex);
                        refreshTable();
                        startDeviceNamePolling(device.serial);
                    });
                } else {
                    device.connectionStatus = STATUS_DISCONNECTED;
                    log.error("handleConnect: Failed to connect to {}", device);
                    SwingUtilities.invokeLater(() -> {
                        fireTableRowsUpdated(rowIndex, rowIndex);
                    });
                    String msg = "Failed to connect to device.";
                    if (TextUtils.notEmpty(result)) msg += " " + result;
                    DialogHelper.showDialog(ConnectDialog.this, "Connect Device", msg);
                }
            });
        }

        /**
         * handle disconnecting from a device
         */
        private void handleDisconnect(WirelessDevice device, int rowIndex) {
            device.connectionStatus = STATUS_DISCONNECTING;
            fireTableRowsUpdated(rowIndex, rowIndex);

            DeviceManager.getInstance().disconnectDevice(device.serial, (success, result) -> {
                if (success) {
                    log.debug("handleDisconnect: Disconnected from {}", device);
                    device.isConnected = false;
                    device.connectionStatus = STATUS_DISCONNECTED;
                } else {
                    log.error("handleDisconnect: Failed to disconnect from {}", device);
                    device.connectionStatus = STATUS_CONNECTED;
                }
                SwingUtilities.invokeLater(() -> {
                    fireTableRowsUpdated(rowIndex, rowIndex);
                    if (success) refreshTable();
                });
            });
        }
    }

    /**
     * renderer for connection status column
     */
    private static class ConnectionStatusRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            boolean isSelectedAndFocused = isSelected && table.hasFocus();
            if (!isSelectedAndFocused) {
                String status = (String) value;
                if (STATUS_CONNECTED.equals(status)) {
                    setForeground(new Color(0, 150, 0)); // Green
                } else if (STATUS_DISCONNECTED.equals(status)) {
                    setForeground(Color.RED);
                } else if (STATUS_CONNECTING.equals(status) || STATUS_DISCONNECTING.equals(status)) {
                    setForeground(new Color(255, 140, 0)); // Orange
                } else {
                    setForeground(Color.GRAY);
                }
            }

            return this;
        }
    }

}
