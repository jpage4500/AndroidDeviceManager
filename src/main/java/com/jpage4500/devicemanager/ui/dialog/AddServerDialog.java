package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.RemoteServerConfig;
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
        boolean confirmed = DialogHelper.showCustomDialog(parent, panel, title, null);

        if (!confirmed) {
            return null;
        }

        // validate
        String name = nameField.getText().trim();
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String token = tokenField.getText().trim();

        if (name.isEmpty() || host.isEmpty() || portStr.isEmpty()) {
            DialogHelper.showDialog(parent, "Validation Error", "Please fill in all required fields");
            return null;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Port out of range");
            }
        } catch (NumberFormatException e) {
            DialogHelper.showDialog(parent, "Validation Error", "Invalid port number");
            return null;
        }

        // create or update config
        RemoteServerConfig result = existingServer != null ? existingServer : new RemoteServerConfig();
        result.name = name;
        result.host = host;
        result.port = port;
        result.authToken = token.isEmpty() ? null : token;
        result.enabled = enabledCheckbox.isSelected();

        return result;
    }
}

