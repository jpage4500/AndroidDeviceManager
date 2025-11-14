package com.jpage4500.devicemanager.data;

import java.util.UUID;

/**
 * Configuration for a remote device manager server
 */
public class RemoteServerConfig {
    public String id;              // unique identifier
    public String name;            // user-friendly name
    public String host;            // IP address or hostname
    public int port;               // server port
    public String authToken;       // authentication token
    public boolean enabled;        // whether to connect on startup

    public RemoteServerConfig() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
    }

    public String getUrl() {
        return String.format("http://%s:%d", host, port);
    }
}
