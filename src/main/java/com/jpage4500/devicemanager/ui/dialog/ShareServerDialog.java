package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.RemoteClientInfo;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.RemoteServerManager;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Dialog for sharing local devices via HTTP server
 */
public class ShareServerDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ShareServerDialog.class);

    private final RemoteServerManager serverManager;

    private JLabel statusLabel;
    private JTextField deviceNameField;
    private JTextField portField;
    private JTextField authTokenField;
    private ClientTableModel clientTableModel;
    private JTable clientTable;
    private JButton toggleButton;
    private JButton copyButton;
    private JTextArea networkField;
    private List<RemoteConnectionUtils.Network> networkList;

    public static void showShareServerDialog(Component parent) {
        RemoteServerManager serverManager = DeviceManager.getInstance().getRemoteServerManager();
        ShareServerDialog dialog = new ShareServerDialog(serverManager);
        DialogHelper.showCustomDialog(parent, dialog, "Share Devices", new String[]{});
    }

    public ShareServerDialog(RemoteServerManager serverManager) {
        this.serverManager = serverManager;

        setLayout(new BorderLayout(10, 10));
        initUI();
        refreshUI();

        // fetch network list in background
        Utils.runBackground(() -> {
            synchronized (this) {
                networkList = RemoteConnectionUtils.getActiveNetworkInfo();
            }
            SwingUtilities.invokeLater(this::refreshUI);
        });
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new MigLayout("fillx", "[right]rel[grow,fill]"));

        // Status
        mainPanel.add(new JLabel("Status:"));
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        mainPanel.add(statusLabel, "wrap");

        // Server Name
        mainPanel.add(new JLabel("Server Name:"));
        deviceNameField = new JTextField();
        mainPanel.add(deviceNameField, "wrap");

        // placeholder for network(s)
        mainPanel.add(new JLabel("Host / IP:"), "aligny top");
        networkField = new JTextArea();
        networkField.setEditable(false);
        networkField.setBackground(Color.LIGHT_GRAY);
        networkField.setLineWrap(true);
        networkField.setWrapStyleWord(true);
        networkField.setRows(1);
        JScrollPane networkScrollPane = new JScrollPane(networkField);
        networkScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        networkScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mainPanel.add(networkScrollPane, "height 20:60:, wrap");

        // Port
        mainPanel.add(new JLabel("Port:"));
        portField = new JTextField();
        mainPanel.add(portField, "wrap");

        // Auth Token
        mainPanel.add(new JLabel("Auth Token:"));
        authTokenField = new JTextField();
        mainPanel.add(authTokenField, "wrap");

        // Copy button
        copyButton = new JButton("Copy Connection String");
        copyButton.addActionListener(e -> copyConnectionString());
        copyButton.setEnabled(false); // Disabled until server starts
        mainPanel.add(copyButton, "skip 1, wrap");

        // Connected Clients label
        mainPanel.add(new JLabel("Connected Clients:"), "wrap");

        // Client table
        clientTableModel = new ClientTableModel();
        clientTable = new JTable(clientTableModel);
        clientTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        clientTable.setRowHeight(25);
        JScrollPane scrollPane = new JScrollPane(clientTable);
        scrollPane.setPreferredSize(new Dimension(500, 150));
        mainPanel.add(scrollPane, "span, grow, wrap 10px");

        add(mainPanel, BorderLayout.CENTER);

        // Control buttons
        JPanel buttonPanel = new JPanel(new MigLayout("fillx"));
        toggleButton = new JButton();
        toggleButton.addActionListener(e -> toggleServer());
        buttonPanel.add(toggleButton, "al center");

        add(buttonPanel, BorderLayout.SOUTH);

        // Start auto-refresh timer
        Timer refreshTimer = new Timer(2000, e -> refreshClientList());
        refreshTimer.start();
    }

    private void refreshUI() {
        boolean isRunning = serverManager.isRunning();

        // Update status
        if (isRunning) {
            statusLabel.setText("Running");
            statusLabel.setForeground(new Color(0, 150, 0));
            toggleButton.setText("Stop Server");
        } else {
            statusLabel.setText("Stopped");
            statusLabel.setForeground(Color.RED);
            toggleButton.setText("Start Server");
        }

        // get network(s)
        synchronized (this) {
            if (networkList != null) {
                StringBuilder networkSb = new StringBuilder();
                for (RemoteConnectionUtils.Network network : networkList) {
                    // Add line break for subsequent entries
                    if (!networkSb.isEmpty()) networkSb.append("\n");
                    // IP address
                    networkSb.append(network.ip);
                    // hostname
                    if (TextUtils.notEmpty(network.host) && !TextUtils.equals(network.host, network.ip)) {
                        networkSb.append(" (");
                        networkSb.append(network.host);
                        networkSb.append(")");
                    }
                }
                networkField.setText(networkSb.toString());
            } else {
                networkField.setText("fetching network info...");
            }
        }

        // Get device name and IP
        String deviceName = RemoteConnectionUtils.getDeviceName();
        int port = isRunning ? serverManager.getPort() : getDefaultPort();
        String authToken = isRunning ? serverManager.getAuthToken() : getDefaultOrGenerateAuthToken();

        // Update fields
        deviceNameField.setText(deviceName);
        portField.setText(String.valueOf(port));
        authTokenField.setText(authToken);

        // Enable/disable editable fields based on server status
        deviceNameField.setEditable(!isRunning);
        portField.setEditable(!isRunning);
        authTokenField.setEditable(!isRunning);

        // Visual indication of editable state
        deviceNameField.setBackground(isRunning ? Color.LIGHT_GRAY : Color.WHITE);
        portField.setBackground(isRunning ? Color.LIGHT_GRAY : Color.WHITE);
        authTokenField.setBackground(isRunning ? Color.LIGHT_GRAY : Color.WHITE);

        // Enable/disable copy button based on server status
        copyButton.setEnabled(isRunning);

        refreshClientList();
    }

    private void toggleServer() {
        if (serverManager.isRunning()) {
            // Stop server - update UI immediately and run stop in background
            statusLabel.setText("Stopping Server...");
            statusLabel.setForeground(Color.ORANGE);
            toggleButton.setEnabled(false);

            // prevent server from running next time app is started
            PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_SERVER_ENABLED, false);

            new Thread(() -> {
                serverManager.stopServer();
                SwingUtilities.invokeLater(() -> {
                    toggleButton.setEnabled(true);
                    refreshUI();
                });
            }, "StopServer").start();
        } else {
            // Start server - use values from fields
            String deviceName = deviceNameField.getText().trim();
            String portStr = portField.getText().trim();
            String authToken = authTokenField.getText().trim();

            // Save device name preference
            if (!deviceName.isEmpty()) {
                PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_SERVER_DEVICE_NAME, deviceName);
            }

            // Validate auth token
            if (authToken.isEmpty()) {
                authToken = RemoteConnectionUtils.generateAuthToken();
            }

            int port;
            try {
                port = Integer.parseInt(portStr);
                if (port < 1024 || port > 65535) {
                    DialogHelper.showDialog(this, "Error", "Port must be between 1024 and 65535");
                    return;
                }
            } catch (NumberFormatException e) {
                DialogHelper.showDialog(this, "Error", "Invalid port number: " + portStr);
                return;
            }

            try {
                serverManager.startServer(port, authToken);
            } catch (Exception e) {
                log.error("Failed to start server", e);
                DialogHelper.showDialog(this, "Error", "Failed to start server: " + e.getMessage());
            }

            refreshUI();
        }
    }

    private void copyConnectionString() {
        if (!serverManager.isRunning()) return;

        String hostname = null;
        // if multiple networks listed, prompt which one to use
        if (networkList.size() > 1) {
            List<String> choices = new ArrayList<>();
            for (RemoteConnectionUtils.Network network : networkList) {
                choices.add(network.ip);
                // if host is different than ip, add it as an option
                if (TextUtils.notEmpty(network.host) && !TextUtils.equals(network.host, network.ip)) {
                    choices.add(network.host);
                }
            }
            int rc = DialogHelper.showOptionDialog(this, "Select hostname/IP", "Which hostname/IP address do you want to use?", choices);
            if (rc < 0) return;
            hostname = choices.get(rc);
        } else if (networkList.size() == 1) {
            hostname = networkList.get(0).host;
        }

        String connectionStr = RemoteConnectionUtils.generateConnectionString(hostname, serverManager.getPort(), serverManager.getAuthToken(), deviceNameField.getText());

        try {
            StringSelection selection = new StringSelection(connectionStr);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            JOptionPane.showMessageDialog(this, "Connection string copied to clipboard", "Copied", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            log.error("Failed to copy connection string", e);
            JOptionPane.showMessageDialog(this, "Failed to generate connection string", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshClientList() {
        if (serverManager.isRunning()) {
            // Save current selection
            int selectedRow = clientTable.getSelectedRow();
            List<RemoteClientInfo> clients = serverManager.getConnectedClients();
            clientTableModel.setClients(clients);
            // Restore selection if valid
            if (selectedRow >= 0 && selectedRow < clientTable.getRowCount()) {
                clientTable.setRowSelectionInterval(selectedRow, selectedRow);
            }
        } else {
            clientTableModel.setClients(new ArrayList<>());
        }
    }

    private int getDefaultPort() {
        return PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_SERVER_PORT, RemoteServerManager.DEFAULT_PORT);
    }

    private String getDefaultOrGenerateAuthToken() {
        // Try to get saved token first
        String savedToken = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN);

        if (savedToken != null && !savedToken.isEmpty()) {
            return savedToken;
        }

        return RemoteConnectionUtils.generateAuthToken();
    }

    /**
     * Table model for connected clients
     */
    private static class ClientTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Client Name", "IP Address", "Connected", "Last", "#"};
        private List<RemoteClientInfo> clients = new ArrayList<>();
        private final SimpleDateFormat sdf = new SimpleDateFormat("M/d @ h:mm aa");
        private final SimpleDateFormat sdfTime = new SimpleDateFormat("h:mm aa");

        public void setClients(List<RemoteClientInfo> clients) {
            this.clients = clients != null ? clients : new ArrayList<>();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return clients.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RemoteClientInfo client = clients.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return client.name != null ? client.name : client.id;
                case 1:
                    return client.ipAddress;
                case 2:
                    return sdf.format(new Date(client.connectedAtMs));
                case 3:
                    return sdfTime.format(new Date(client.lastActivityMs));
                case 4:
                    return String.valueOf(client.requestCount);
                default:
                    return null;
            }
        }
    }
}

