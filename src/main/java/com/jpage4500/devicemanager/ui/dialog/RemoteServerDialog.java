package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.RemoteConnectionManager;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.RemoteConnectionUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for managing remote server connections
 */
public class RemoteServerDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(RemoteServerDialog.class);

    private final Component parent;
    private JTable serverTable;
    private ServerTableModel tableModel;

    public static void showRemoteServerDialog(Component parent) {
        RemoteServerDialog dialog = new RemoteServerDialog(parent);
        DialogHelper.showCustomDialog(parent, dialog, "Remote Servers", new String[]{});
    }

    public RemoteServerDialog(Component parent) {
        this.parent = parent;
        setLayout(new BorderLayout(10, 10));
        initUI();
        loadServers();
    }

    private void initUI() {
        // Table
        tableModel = new ServerTableModel();
        serverTable = new JTable(tableModel);
        serverTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverTable.setRowHeight(30);
        serverTable.getColumnModel().getColumn(0).setPreferredWidth(30);  // Status
        serverTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Name
        serverTable.getColumnModel().getColumn(2).setPreferredWidth(150); // Host
        serverTable.getColumnModel().getColumn(3).setPreferredWidth(60);  // Port
        serverTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Status

        // Custom renderer for status column
        serverTable.getColumnModel().getColumn(0).setCellRenderer(new StatusCellRenderer());
        serverTable.getColumnModel().getColumn(4).setCellRenderer(new ConnectionStatusRenderer());

        JScrollPane scrollPane = new JScrollPane(serverTable);
        scrollPane.setPreferredSize(new Dimension(600, 300));

        // Buttons panel
        JPanel buttonPanel = new JPanel(new MigLayout("fillx"));

        JButton addButton = new JButton("Add Server");
        addButton.addActionListener(e -> showAddServerDialog());

        JButton pasteButton = new JButton("Paste Connection");
        pasteButton.addActionListener(e -> pasteConnectionString());

        JButton editButton = new JButton("Edit");
        editButton.addActionListener(e -> editSelectedServer());

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> removeSelectedServer());

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshServers());

        buttonPanel.add(addButton);
        buttonPanel.add(pasteButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(refreshButton);

        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadServers() {
        RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
        List<RemoteServerConfig> servers = remoteConnectionManager.getServers();
        tableModel.setServers(servers);
    }

    private void showAddServerDialog() {
        AddServerDialog dialog = new AddServerDialog(parent, null);
        RemoteServerConfig config = dialog.showDialog();
        if (config != null) {
            RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
            remoteConnectionManager.addServer(config);
            loadServers();
        }
    }

    private void editSelectedServer() {
        int selectedRow = serverTable.getSelectedRow();
        if (selectedRow < 0) {
            DialogHelper.showDialog(this, "Error", "Please select a server to edit");
            return;
        }

        RemoteServerConfig server = tableModel.getServerAt(selectedRow);
        AddServerDialog dialog = new AddServerDialog(parent, server);
        RemoteServerConfig updated = dialog.showDialog();

        if (updated != null) {
            RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
            remoteConnectionManager.updateServer(updated);
            loadServers();
        }
    }

    private void pasteConnectionString() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            String connStr = (String) clipboard.getData(DataFlavor.stringFlavor);

            if (connStr != null && !connStr.trim().isEmpty()) {
                RemoteServerConfig config = RemoteConnectionUtils.parseConnectionString(connStr.trim());
                log.trace("pasteConnectionString: {} -> {}", GsonHelper.toJson(config), connStr);
                if (config != null) {
                    RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
                    remoteConnectionManager.addServer(config);
                }
            }
        } catch (Exception e) {
            log.error("Failed to paste from clipboard", e);
            DialogHelper.showDialog(this, "Error", "Failed to paste from clipboard");
        }
    }

    private void removeSelectedServer() {
        int selectedRow = serverTable.getSelectedRow();
        if (selectedRow < 0) {
            DialogHelper.showDialog(this, "Error", "Please select a server to remove");
            return;
        }

        RemoteServerConfig server = tableModel.getServerAt(selectedRow);
        if (DialogHelper.showConfirmDialog(this, "Remove Server", "Remove server '" + server.name + "'?")) {
            RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
            remoteConnectionManager.removeServer(server.id);
            loadServers();
        }
    }

    private void refreshServers() {
        DeviceManager.getInstance().getRemoteConnectionManager().refreshAllDevices();
    }

    /**
     * Table model for server list
     */
    private static class ServerTableModel extends AbstractTableModel {
        private final String[] columnNames = {"✓", "Name", "Host", "Port", "Status"};
        private List<RemoteServerConfig> servers = new ArrayList<>();

        public void setServers(List<RemoteServerConfig> servers) {
            this.servers = servers != null ? servers : new ArrayList<>();
            fireTableDataChanged();
        }

        public RemoteServerConfig getServerAt(int row) {
            if (row >= 0 && row < servers.size()) {
                return servers.get(row);
            }
            return null;
        }

        @Override
        public int getRowCount() {
            return servers.size();
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
            RemoteServerConfig server = servers.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return server.enabled;
                case 1:
                    return server.name;
                case 2:
                    return server.host;
                case 3:
                    return server.port;
                case 4:
                    return getConnectionStatus(server);
                default:
                    return null;
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // Only enabled checkbox is editable
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                RemoteServerConfig server = servers.get(rowIndex);
                server.enabled = (Boolean) value;

                // Update server
                DeviceManager.getInstance().getRemoteConnectionManager().updateServer(server);

                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        private String getConnectionStatus(RemoteServerConfig server) {
            if (!server.enabled) return "Disabled";

            RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
            if (remoteConnectionManager.isConnected(server.id)) {
                return "Connected";
            } else {
                return "Disconnected";
            }
        }
    }

    /**
     * Renderer for status checkboxes
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private final JCheckBox checkbox = new JCheckBox();

        public StatusCellRenderer() {
            checkbox.setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            checkbox.setSelected(value != null && (Boolean) value);
            checkbox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return checkbox;
        }
    }

    /**
     * Renderer for connection status
     */
    private static class ConnectionStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String status = (String) value;
            if ("Connected".equals(status)) {
                setForeground(new Color(0, 150, 0)); // Green
            } else if ("Disconnected".equals(status)) {
                setForeground(Color.RED);
            } else {
                setForeground(Color.GRAY);
            }

            return this;
        }
    }
}

