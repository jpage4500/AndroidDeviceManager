package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.RemoteConnectionManager;
import com.jpage4500.devicemanager.manager.RemoteServerManager;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for adding/editing a remote server
 */
public class AddServerDialog {
    private final Component parent;
    private final RemoteServerConfig existingServer;

    private JTextField nameField;
    private JTextField hostField;
    private JTextField portField;
    private JTextField tokenField;
    private JCheckBox enabledCheckbox;

    public AddServerDialog(Component parent, RemoteServerConfig existingServer) {
        this.parent = parent;
        this.existingServer = existingServer;
    }

    public RemoteServerConfig showDialog() {
        JPanel panel = new JPanel(new MigLayout("fillx", "[right]rel[grow,fill]"));

        // name
        panel.add(new JLabel("Name:"));
        nameField = new JTextField(20);
        if (existingServer != null) nameField.setText(existingServer.name);
        panel.add(nameField, "wrap");

        // host
        panel.add(new JLabel("Host/IP:"));
        hostField = new JTextField(20);
        if (existingServer != null) hostField.setText(existingServer.host);
        panel.add(hostField, "wrap");

        // port
        panel.add(new JLabel("Port:"));
        portField = new JTextField(String.valueOf(RemoteServerManager.DEFAULT_PORT));
        if (existingServer != null) portField.setText(String.valueOf(existingServer.port));
        panel.add(portField, "wrap");

        // token
        panel.add(new JLabel("Auth Token:"));
        tokenField = new JTextField(20);
        if (existingServer != null && existingServer.authToken != null) {
            tokenField.setText(existingServer.authToken);
        }
        panel.add(tokenField, "wrap");

        // enabled
        enabledCheckbox = new JCheckBox("Enabled");
        enabledCheckbox.setSelected(existingServer == null || existingServer.enabled);
        panel.add(enabledCheckbox, "skip 1, wrap");

        String title = existingServer == null ? "Add Server" : "Edit Server";
        String actionButton = existingServer == null ? "Add" : "Save";

        // Loop until valid input or user cancels
        while (true) {
            int result = JOptionPane.showOptionDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{actionButton, "Cancel"}, actionButton);
            if (result != JOptionPane.OK_OPTION) return null;

            // Validate input
            String name = nameField.getText().trim();
            String host = hostField.getText().trim();
            String portStr = portField.getText().trim();
            String token = tokenField.getText().trim();

            if (name.isEmpty() || host.isEmpty() || portStr.isEmpty()) {
                DialogHelper.showDialog(parent, "Validation Error", "Please fill in all required fields");
                continue;
            }

            int port = TextUtils.getNumber(portStr, -1);
            if (port < 0 || port > 65535) {
                DialogHelper.showDialog(parent, "Validation Error", "Invalid port number (1-65535)");
                continue;
            }

            // Validation passed - create or update config
            RemoteServerConfig config = existingServer != null ? existingServer : new RemoteServerConfig();
            config.name = name;
            config.host = host;
            config.port = port;
            config.authToken = token.isEmpty() ? null : token;
            config.enabled = enabledCheckbox.isSelected();

            // prevent duplicates by host/port
            RemoteConnectionManager remoteConnectionManager = DeviceManager.getInstance().getRemoteConnectionManager();
            String id = existingServer != null ? existingServer.id : null;
            if (remoteConnectionManager.isServerExist(config.host, config.port, id)) continue;

            return config;
        }
    }
}

