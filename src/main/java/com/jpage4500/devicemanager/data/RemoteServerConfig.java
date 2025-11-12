package com.jpage4500.devicemanager.data;

import java.util.UUID;

/**
 * Configuration for a remote ADB server
 */
public class RemoteServerConfig {
    public String id;              // unique identifier
    public String name;            // user-friendly name
    public String host;            // IP address or hostname
    public int port;               // server port
    public String authToken;       // authentication token
    public boolean enabled;        // whether to connect on startup
    public long lastConnectedMs;   // last successful connection
    public boolean isOnline;       // current connection status

    public RemoteServerConfig() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
    }

    public String getConnectionUrl() {
        return String.format("http://%s:%d", host, port);
    }
}

