package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.RemoteClientInfo;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.RemoteServerManager;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.NetworkHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UpnpUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog for sharing local devices via HTTP server
 */
public class ShareServerDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ShareServerDialog.class);

    private final RemoteServerManager serverManager;

    private JLabel statusLabel;
    private JTextField deviceNameField;
    private JTextField localIpAddressField;
    private JTextField ipAddressField;
    private JTextField portField;
    private JTextField authTokenField;
    private ClientTableModel clientTableModel;
    private JButton toggleButton;
    private JButton copyButton;
    private JButton testButton;

    private String cachedPublicIp = null;

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

        // Fetch public IP asynchronously
        fetchPublicIpAsync();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new MigLayout("fillx", "[right]rel[grow,fill]"));

        // Status
        mainPanel.add(new JLabel("Status:"));
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD));
        mainPanel.add(statusLabel, "wrap");

        // Device Name
        mainPanel.add(new JLabel("Device Name:"));
        deviceNameField = new JTextField();
        mainPanel.add(deviceNameField, "wrap");

        // Local IP Address
        mainPanel.add(new JLabel("Local IP:"));
        localIpAddressField = new JTextField();
        localIpAddressField.setEditable(false);
        localIpAddressField.setBackground(Color.LIGHT_GRAY);
        mainPanel.add(localIpAddressField, "wrap");

        // Public IP Address
        mainPanel.add(new JLabel("Public IP:"));
        ipAddressField = new JTextField();
        ipAddressField.setEditable(false);
        ipAddressField.setBackground(Color.LIGHT_GRAY);
        mainPanel.add(ipAddressField, "wrap");

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

        // Test Connection button
        testButton = new JButton("Test Connection");
        testButton.addActionListener(e -> testConnection());
        testButton.setEnabled(false); // Disabled until server starts
        testButton.setToolTipText("Test if server is reachable from external networks");
        mainPanel.add(testButton, "skip 1, split 3");

        // Try UPnP button
        JButton upnpButton = new JButton("Try UPnP");
        upnpButton.addActionListener(e -> tryUpnpPortForwarding());
        upnpButton.setToolTipText("Attempt automatic port forwarding via UPnP");
        mainPanel.add(upnpButton);

        // Firewall button
        JButton firewallButton = new JButton("Firewall Help");
        firewallButton.addActionListener(e -> showFirewallHelp());
        firewallButton.setToolTipText("Show commands to open port in firewall");
        mainPanel.add(firewallButton, "wrap 10px");

        // Connected Clients label
        mainPanel.add(new JLabel("Connected Clients:"), "wrap");

        // Client table
        clientTableModel = new ClientTableModel();
        JTable clientTable = new JTable(clientTableModel);
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

        // Get device name and IP
        String deviceName = getDeviceName();
        String localIp = getRealLocalIpAddress();
        String publicIp = getPublicIpAddress();
        int port = isRunning ? serverManager.getPort() : getDefaultPort();
        String authToken = isRunning ? serverManager.getAuthToken() : getDefaultOrGenerateAuthToken();

        // Update fields
        deviceNameField.setText(deviceName);
        localIpAddressField.setText(localIp);
        ipAddressField.setText(publicIp);
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
        testButton.setEnabled(isRunning);

        refreshClientList();
    }

    private void toggleServer() {
        if (serverManager.isRunning()) {
            // Stop server
            serverManager.stopServer();
        } else {
            // Start server - use values from fields
            String deviceName = deviceNameField.getText().trim();
            String portStr = portField.getText().trim();
            String authToken = authTokenField.getText().trim();

            // Save device name preference
            if (!deviceName.isEmpty()) {
                PreferenceUtils.setPreference(
                    PreferenceUtils.Pref.PREF_SERVER_DEVICE_NAME,
                    deviceName
                );
            }

            // Validate auth token
            if (authToken.isEmpty()) {
                DialogHelper.showDialog(this, "Error", "Auth token cannot be empty");
                return;
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
        }

        refreshUI();
    }

    private void copyConnectionString() {
        if (!serverManager.isRunning()) {
            return;
        }

        try {
            String connStr = serverManager.getConnectionString();
            if (connStr != null && !connStr.isEmpty()) {
                StringSelection selection = new StringSelection(connStr);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);

                // Visual feedback
                JOptionPane.showMessageDialog(this,
                    "Connection string copied to clipboard!\n\nShare this with others to let them connect to your devices.",
                    "Copied",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            log.error("Failed to copy connection string", e);
            JOptionPane.showMessageDialog(this,
                "Failed to generate connection string",
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testConnection() {
        if (!serverManager.isRunning()) {
            return;
        }

        // Disable button during test
        testButton.setEnabled(false);
        testButton.setText("Testing...");

        // Run test in background thread
        new Thread(() -> {
            String publicIp = getPublicIpAddress();
            int port = serverManager.getPort();

            if (publicIp == null || publicIp.equals("Fetching...") || publicIp.equals("N/A")) {
                SwingUtilities.invokeLater(() -> {
                    testButton.setEnabled(true);
                    testButton.setText("Test Connection");
                    JOptionPane.showMessageDialog(this,
                        "Cannot test: Public IP not available.\nPlease wait a moment and try again.",
                        "Test Failed",
                        JOptionPane.WARNING_MESSAGE);
                });
                return;
            }

            // Test using portchecker.io API (GET /api/{host}/{port} as per their docs)
            String testUrl = String.format("https://portchecker.io/api/%s/%d", publicIp, port);
            log.debug("Testing external reachability: {}", testUrl);

            NetworkHelper networkHelper = new NetworkHelper();
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json");

            NetworkHelper.HttpResponse response = networkHelper.getRequest(testUrl, headers);
            log.trace("testConnection: http:{}, body:{}", response.status, response.body);

            final boolean isReachable;
            final String message;

            if (response.status == 200 && response.body != null) {
                // request went through
                if (TextUtils.equalsIgnoreCase(response.body, "true")) {
                    isReachable = true;
                    message = String.format(
                        "✅ Server is reachable from external networks!\n\n" +
                            "Public IP: %s\n" +
                            "Port: %d\n\n" +
                            "Your server can be accessed from the internet.",
                        publicIp, port
                    );
                } else {
                    isReachable = false;
                    message = String.format(
                        "❌ Server is NOT reachable from external networks.\n\n" +
                            "Public IP: %s\n" +
                            "Port: %d\n\n" +
                            "Possible causes:\n" +
                            "• Firewall blocking port %d\n" +
                            "• Router not forwarding port %d\n" +
                            "• ISP blocking incoming connections\n\n" +
                            "Your server is only accessible on your local network.",
                        publicIp, port, port, port
                    );
                }
            } else {
                // network error
                isReachable = false;
                String errorDetails = response.status == -1 ? response.body :
                    String.format("HTTP %d: %s", response.status, response.body);
                message = String.format(
                    "⚠️ Could not determine external reachability.\n\n" +
                        "Public IP: %s\n" +
                        "Port: %d\n\n" +
                        "Error: %s\n\n" +
                        "The test service may be temporarily unavailable.\n" +
                        "Try testing manually or wait and try again.",
                    publicIp, port, errorDetails
                );
            }

            SwingUtilities.invokeLater(() -> {
                testButton.setEnabled(true);
                testButton.setText("Test Connection");

                JOptionPane.showMessageDialog(this,
                    message,
                    isReachable ? "Connection Test: Success" : "Connection Test: Failed",
                    isReachable ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            });
        }, "TestConnection").start();
    }

    private void tryUpnpPortForwarding() {
        int port = serverManager.isRunning() ? serverManager.getPort() : getDefaultPort();

        // Show progress dialog
        JDialog progressDialog = new JDialog();
        progressDialog.setTitle("UPnP Port Forwarding");
        progressDialog.setModal(true);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel("Searching for UPnP gateway and opening port " + port + "..."), BorderLayout.CENTER);

        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        panel.add(progressBar, BorderLayout.SOUTH);

        progressDialog.add(panel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);

        // Run UPnP in background
        new Thread(() -> {
            boolean success = UpnpUtils.openPort(port, "Android Device Manager");
            String externalIp = UpnpUtils.getExternalIP();

            SwingUtilities.invokeLater(() -> {
                progressDialog.dispose();

                if (success) {
                    String message = String.format(
                        "✅ UPnP Port Forwarding Successful!\n\n" +
                        "Port %d is now open on your router.\n" +
                        "External IP: %s\n\n" +
                        "Your server should now be accessible from the internet.\n" +
                        "Use the 'Test Connection' button to verify.",
                        port, externalIp != null ? externalIp : "Unknown"
                    );
                    JOptionPane.showMessageDialog(this, message, "UPnP Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    String message = String.format(
                        "❌ UPnP Port Forwarding Failed\n\n" +
                        "Port %d could not be opened automatically.\n\n" +
                        "Possible reasons:\n" +
                        "• Router doesn't support UPnP/IGD\n" +
                        "• UPnP is disabled on your router\n" +
                        "• Router firewall blocking UPnP\n\n" +
                        "You'll need to manually configure port forwarding.\n" +
                        "Click 'Firewall Help' for instructions.",
                        port
                    );
                    JOptionPane.showMessageDialog(this, message, "UPnP Failed", JOptionPane.WARNING_MESSAGE);
                }
            });
        }, "UPnP-Manual").start();

        progressDialog.setVisible(true);
    }

    private void showFirewallHelp() {
        int port = serverManager.isRunning() ? serverManager.getPort() : getDefaultPort();
        String os = System.getProperty("os.name").toLowerCase();

        String title = "Firewall Configuration Help";
        String instructions;

        if (os.contains("mac")) {
            // macOS instructions
            instructions = String.format(
                "macOS Firewall Configuration\n" +
                "═══════════════════════════\n\n" +
                "To allow incoming connections on port %d:\n\n" +
                "Option 1: Using System Preferences (Recommended)\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "1. Open System Preferences → Security & Privacy\n" +
                "2. Click Firewall tab\n" +
                "3. Click lock to make changes\n" +
                "4. Click Firewall Options\n" +
                "5. Click '+' to add this application\n" +
                "6. Select 'Allow incoming connections'\n\n" +
                "Option 2: Using Terminal (Advanced)\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "# Check firewall status\n" +
                "sudo /usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate\n\n" +
                "# Add this app to firewall (replace path if needed)\n" +
                "sudo /usr/libexec/ApplicationFirewall/socketfilterfw --add /Applications/AndroidDeviceManager.app\n\n" +
                "# Allow incoming connections\n" +
                "sudo /usr/libexec/ApplicationFirewall/socketfilterfw --unblock /Applications/AndroidDeviceManager.app\n\n" +
                "Router Port Forwarding\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "1. Open router admin (usually 192.168.1.1)\n" +
                "2. Find Port Forwarding settings\n" +
                "3. Forward external port %d to internal port %d\n" +
                "4. Set internal IP to your local IP\n",
                port, port, port
            );
        } else if (os.contains("nix") || os.contains("nux")) {
            // Linux instructions
            instructions = String.format(
                "Linux Firewall Configuration\n" +
                "═══════════════════════════\n\n" +
                "Choose the firewall system your distribution uses:\n\n" +
                "UFW (Ubuntu/Debian)\n" +
                "━━━━━━━━━━━━━━━━━━\n" +
                "# Check UFW status\n" +
                "sudo ufw status\n\n" +
                "# Allow port %d\n" +
                "sudo ufw allow %d/tcp\n\n" +
                "# Or allow from specific subnet only (more secure)\n" +
                "sudo ufw allow from 192.168.0.0/24 to any port %d proto tcp\n\n" +
                "firewalld (CentOS/RHEL/Fedora)\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "# Check firewalld status\n" +
                "sudo firewall-cmd --state\n\n" +
                "# Allow port %d permanently\n" +
                "sudo firewall-cmd --permanent --add-port=%d/tcp\n" +
                "sudo firewall-cmd --reload\n\n" +
                "# Or use rich rule for specific subnet (more secure)\n" +
                "sudo firewall-cmd --permanent --add-rich-rule='rule family=\"ipv4\" source address=\"192.168.0.0/24\" port port=\"%d\" protocol=\"tcp\" accept'\n" +
                "sudo firewall-cmd --reload\n\n" +
                "iptables (Legacy)\n" +
                "━━━━━━━━━━━━━━━━━\n" +
                "# Allow port %d\n" +
                "sudo iptables -I INPUT -p tcp --dport %d -m state --state NEW -j ACCEPT\n\n" +
                "# Save rules (Debian/Ubuntu)\n" +
                "sudo sh -c 'iptables-save > /etc/iptables/rules.v4'\n\n" +
                "# Or (CentOS/RHEL)\n" +
                "sudo service iptables save\n\n" +
                "Router Port Forwarding\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "1. Access router admin (typically 192.168.1.1)\n" +
                "2. Navigate to Port Forwarding section\n" +
                "3. Forward external port %d to internal port %d\n" +
                "4. Set internal IP to your local IP\n",
                port, port, port, port, port, port, port, port, port, port
            );
        } else if (os.contains("win")) {
            // Windows instructions
            instructions = String.format(
                "Windows Firewall Configuration\n" +
                "═════════════════════════════\n\n" +
                "Option 1: Windows Defender Firewall GUI (Recommended)\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "1. Open Control Panel → Windows Defender Firewall\n" +
                "2. Click 'Advanced settings'\n" +
                "3. Click 'Inbound Rules' → 'New Rule'\n" +
                "4. Select 'Port' → Next\n" +
                "5. Select 'TCP' and enter port: %d\n" +
                "6. Select 'Allow the connection' → Next\n" +
                "7. Check all profiles → Next\n" +
                "8. Name: 'Android Device Manager' → Finish\n\n" +
                "Option 2: Command Line (Run as Administrator)\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "REM Add inbound rule for port %d\n" +
                "netsh advfirewall firewall add rule name=\"Android Device Manager\" dir=in action=allow protocol=TCP localport=%d\n\n" +
                "REM To remove the rule later:\n" +
                "netsh advfirewall firewall delete rule name=\"Android Device Manager\"\n\n" +
                "Option 3: PowerShell (Run as Administrator)\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "# Add inbound rule\n" +
                "New-NetFirewallRule -DisplayName \"Android Device Manager\" -Direction Inbound -Protocol TCP -LocalPort %d -Action Allow\n\n" +
                "# To remove the rule later:\n" +
                "Remove-NetFirewallRule -DisplayName \"Android Device Manager\"\n\n" +
                "Router Port Forwarding\n" +
                "━━━━━━━━━━━━━━━━━━━━━━\n" +
                "1. Open router admin page (usually 192.168.1.1)\n" +
                "2. Find Port Forwarding or Virtual Server settings\n" +
                "3. Add new rule:\n" +
                "   - External Port: %d\n" +
                "   - Internal Port: %d\n" +
                "   - Internal IP: Your computer's local IP\n" +
                "   - Protocol: TCP\n" +
                "4. Save and reboot router if required\n",
                port, port, port, port, port, port
            );
        } else {
            // Unknown OS
            instructions = String.format(
                "Firewall Configuration (Generic)\n" +
                "═══════════════════════════════\n\n" +
                "Port to open: %d (TCP)\n\n" +
                "Steps:\n" +
                "1. Configure your firewall to allow incoming TCP connections on port %d\n" +
                "2. Configure your router to forward port %d to your computer's local IP\n" +
                "3. Test connectivity using the 'Test Connection' button\n\n" +
                "OS detected: %s\n\n" +
                "Please consult your operating system's documentation for specific firewall configuration steps.\n",
                port, port, port, os
            );
        }

        // Create dialog with scrollable text area
        JTextArea textArea = new JTextArea(instructions);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(700, 500));

        JOptionPane.showMessageDialog(this, scrollPane, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshClientList() {
        if (serverManager.isRunning()) {
            List<RemoteClientInfo> clients = serverManager.getConnectedClients();
            clientTableModel.setClients(clients);
        } else {
            clientTableModel.setClients(new ArrayList<>());
        }
    }

    private String getDeviceName() {
        // Try to get from preferences first
        String savedName = PreferenceUtils.getPreference(
            PreferenceUtils.Pref.PREF_SERVER_DEVICE_NAME
        );
        if (savedName != null && !savedName.isEmpty()) {
            return savedName;
        }

        // Fall back to hostname
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "My Device";
        }
    }

    private String getRealLocalIpAddress() {
        // Get the actual LAN IP (not 127.0.0.1)
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String ip = localHost.getHostAddress();

            // If it's localhost, try to find the real network interface
            if (ip.equals("127.0.0.1") || ip.equals("0.0.0.0")) {
                java.util.Enumeration<java.net.NetworkInterface> interfaces =
                    java.net.NetworkInterface.getNetworkInterfaces();

                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface iface = interfaces.nextElement();

                    // Skip loopback and inactive interfaces
                    if (iface.isLoopback() || !iface.isUp()) {
                        continue;
                    }

                    java.util.Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();

                        // We want IPv4 addresses only (skip IPv6)
                        if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }

            return ip;
        } catch (Exception e) {
            log.debug("Failed to get local IP: {}", e.getMessage());
            return "N/A";
        }
    }

    private String getPublicIpAddress() {
        // Use cached public IP if available
        if (cachedPublicIp != null) {
            return cachedPublicIp;
        }

        // Return placeholder while fetching
        return "Fetching...";
    }

    private void fetchPublicIpAsync() {
        new Thread(() -> {
            try {
                NetworkHelper networkHelper = new NetworkHelper();
                NetworkHelper.HttpResponse response = networkHelper.getRequest("https://api.ipify.org");
                if (response.body != null && !response.body.trim().isEmpty()) {
                    cachedPublicIp = response.body.trim();
                    // Update UI on the event dispatch thread
                    SwingUtilities.invokeLater(() -> {
                        ipAddressField.setText(cachedPublicIp);
                    });
                }
            } catch (Exception e) {
                log.debug("Failed to fetch public IP: {}", e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    ipAddressField.setText("N/A");
                });
            }
        }, "FetchPublicIP").start();
    }

    private int getDefaultPort() {
        return PreferenceUtils.getPreference(
            PreferenceUtils.PrefInt.PREF_SERVER_PORT,
            8765
        );
    }

    private String getDefaultOrGenerateAuthToken() {
        // Try to get saved token first
        String savedToken = PreferenceUtils.getPreference(
            PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN
        );

        if (savedToken != null && !savedToken.isEmpty()) {
            return savedToken;
        }

        // Generate a new random token (16 characters)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int index = (int) (Math.random() * chars.length());
            token.append(chars.charAt(index));
        }
        return token.toString();
    }

    /**
     * Table model for connected clients
     */
    private static class ClientTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Client Name", "IP Address", "Connected At"};
        private List<RemoteClientInfo> clients = new ArrayList<>();

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
                    return client.name != null ? client.name : "Unknown";
                case 1:
                    return client.ipAddress;
                case 2:
                    return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(client.connectedAtMs));
                default:
                    return null;
            }
        }
    }
}

