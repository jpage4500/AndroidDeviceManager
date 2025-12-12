package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.client.RemoteConnectionManager;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.Timer;
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

        // start auto-refresh timer
        Timer refreshTimer = new Timer(1000, e -> refreshStatus());
        refreshTimer.start();
    }

    private void refreshStatus() {
        int selectedRow = serverTable.getSelectedRow();
        tableModel.fireTableDataChanged();
        // restore selection if valid
        if (selectedRow >= 0 && selectedRow < serverTable.getRowCount()) {
            serverTable.setRowSelectionInterval(selectedRow, selectedRow);
        }
    }

    private void initUI() {
        // text
//        String msg = "<html>" +
//            "To add a remote server, run Device Manager and open the 'Server' dialog and hit 'Start Server'<br><br>" +
//            "Once running, copy the connection string from the 'Server' dialog and paste it using the 'Paste Connection' button below<br><br>" +
//            "</html>";
//        JLabel titleLabel = new JLabel(msg);
//        add(titleLabel, BorderLayout.NORTH);

        // table
        tableModel = new ServerTableModel();
        serverTable = new JTable(tableModel);
        serverTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverTable.setRowHeight(30);
        serverTable.getColumnModel().getColumn(ServerColumn.ENABLED.ordinal()).setPreferredWidth(30);  // Status
        serverTable.getColumnModel().getColumn(ServerColumn.COLOR.ordinal()).setPreferredWidth(30);  // Color
        serverTable.getColumnModel().getColumn(ServerColumn.NAME.ordinal()).setPreferredWidth(150); // Name
        serverTable.getColumnModel().getColumn(ServerColumn.HOST.ordinal()).setPreferredWidth(150); // Host
        serverTable.getColumnModel().getColumn(ServerColumn.PORT.ordinal()).setPreferredWidth(60);  // Port
        serverTable.getColumnModel().getColumn(ServerColumn.STATUS.ordinal()).setPreferredWidth(80);  // Status

        // custom renderer for status column
        serverTable.getColumnModel().getColumn(ServerColumn.ENABLED.ordinal()).setCellRenderer(new StatusCellRenderer());
        serverTable.getColumnModel().getColumn(ServerColumn.COLOR.ordinal()).setCellRenderer(new ColorCellRenderer());
        serverTable.getColumnModel().getColumn(ServerColumn.STATUS.ordinal()).setCellRenderer(new ConnectionStatusRenderer());

        // Add mouse listener for color picker on color column
        serverTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = serverTable.rowAtPoint(e.getPoint());
                int col = serverTable.columnAtPoint(e.getPoint());
                if (col == ServerColumn.COLOR.ordinal() && row >= 0) { // Color column
                    RemoteServerConfig server = tableModel.getServerAt(row);
                    if (server != null) {
                        Color initialColor = new Color(server.color, true);
                        Color newColor = JColorChooser.showDialog(serverTable, "Choose Server Color", initialColor);
                        if (newColor != null && !newColor.equals(initialColor)) {
                            server.color = newColor.getRGB();
                            // Persist the change
                            DeviceManager.getInstance().getRemoteConnectionManager().updateServer(server);
                            tableModel.fireTableRowsUpdated(row, row);
                        }
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(serverTable);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        add(scrollPane, BorderLayout.CENTER);

        // buttons panel
        JPanel buttonPanel = new JPanel(new MigLayout("fillx"));

        JButton addButton = new JButton("Add Server");
        addButton.addActionListener(e -> showAddServerDialog());

        JButton pasteButton = new JButton("Paste Connection");
        pasteButton.addActionListener(e -> pasteConnectionString());

        JButton editButton = new JButton("Edit");
        editButton.addActionListener(e -> editSelectedServer());

        JButton removeButton = new JButton("Remove");
        removeButton.addActionListener(e -> removeSelectedServer());

        buttonPanel.add(addButton);
        buttonPanel.add(pasteButton);
        buttonPanel.add(editButton);
        buttonPanel.add(removeButton);

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
            // prevent duplicates by host/port
            if (remoteConnectionManager.isServerExist(config.host, config.port, null)) return;
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
            // prevent duplicates when updating (exclude current server ID)
            if (remoteConnectionManager.isServerExist(updated.host, updated.port, updated.id)) return;
            remoteConnectionManager.updateServer(updated);
            loadServers();
        }
    }

    private void pasteConnectionString() {
        String connStr = Utils.getClipboardText();
        if (TextUtils.isEmpty(connStr)) {
            DialogHelper.showDialog(this, "Error", "Clipboard is empty");
            return;
        }
        RemoteServerConfig config = RemoteConnectionUtils.parseConnectionString(connStr.trim());
        log.trace("pasteConnectionString: {} -> {}", connStr, GsonHelper.toJson(config));
        if (config != null) {
            // Open Add Server dialog with pre-filled values
            AddServerDialog dialog = new AddServerDialog(parent, config);
            RemoteServerConfig updated = dialog.showDialog();
            if (updated != null) {
                RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
                // prevent duplicates by host/port
                if (remoteConnectionManager.isServerExist(updated.host, updated.port, null)) return;
                remoteConnectionManager.addServer(updated);
                loadServers();
            }
        } else {
            DialogHelper.showDialog(this, "Error", "Invalid connection string");
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

    enum ServerColumn {
        ENABLED("✓"),
        NAME("Name"),
        HOST("Host"),
        PORT("Port"),
        STATUS("Status"),
        COLOR("Color"),
        ;

        final String label;

        ServerColumn(String label) {
            this.label = label;
        }
    }

    /**
     * Table model for server list
     */
    private static class ServerTableModel extends AbstractTableModel {
        private final ServerColumn[] columns = ServerColumn.values();
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
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column].label;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RemoteServerConfig server = servers.get(rowIndex);
            ServerColumn col = columns[columnIndex];
            return switch (col) {
                case ENABLED -> server.enabled;
                case COLOR -> server.color;
                case NAME -> server.name;
                case HOST -> server.host;
                case PORT -> server.port;
                case STATUS -> getConnectionStatus(server);
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            ServerColumn col = columns[columnIndex];
            if (col == ServerColumn.ENABLED) return Boolean.class;
            if (col == ServerColumn.COLOR) return Integer.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columns[columnIndex] == ServerColumn.ENABLED;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columns[columnIndex] == ServerColumn.ENABLED) {
                RemoteServerConfig server = servers.get(rowIndex);
                server.enabled = (Boolean) value;

                // update server
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
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            checkbox.setSelected(value != null && (Boolean) value);
            checkbox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return checkbox;
        }
    }

    /**
     * Renderer for color column
     */
    private static class ColorCellRenderer extends DefaultTableCellRenderer {
        private static final int BOX_SIZE = 16;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            int colorInt = value instanceof Integer ? (Integer) value : Colors.COLOR_ONLINE.getRGB();
            Color color = new Color(colorInt, true);
            JLabel label = new JLabel();
            label.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            label.setOpaque(true);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setVerticalAlignment(SwingConstants.CENTER);
            label.setIcon(new ColorBoxIcon(color, BOX_SIZE, isSelected ? table.getSelectionBackground() : Color.LIGHT_GRAY));
            return label;
        }
    }

    /**
     * Icon for rendering a colored box (used in color column)
     */
    private static class ColorBoxIcon implements Icon {
        private final Color color;
        private final int size;
        private final Color borderColor;

        public ColorBoxIcon(Color color, int size, Color borderColor) {
            this.color = color;
            this.size = size;
            this.borderColor = borderColor;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Draw only the box, leave background transparent
            g2.setColor(color);
            g2.fillRect(x, y, size, size);
            g2.setColor(borderColor);
            g2.drawRect(x, y, size - 1, size - 1);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /**
     * Renderer for connection status
     */
    private static class ConnectionStatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            boolean isSelectedAndFocused = isSelected && table.hasFocus();
            if (!isSelectedAndFocused) {
                String status = (String) value;
                if ("Connected".equals(status)) {
                    setForeground(new Color(0, 150, 0)); // Green
                } else if ("Disconnected".equals(status)) {
                    setForeground(Color.RED);
                } else {
                    setForeground(Color.GRAY);
                }
            }

            return this;
        }
    }
}
