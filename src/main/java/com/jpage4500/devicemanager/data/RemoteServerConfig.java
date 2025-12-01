package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;

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
        if (!TextUtils.startsWith(host, "http")) {
            return String.format("http://%s:%d", host, port);
        }
        return String.format("%s:%d", host, port);
    }

    public String getWebsocketUrl() {
        String url = getUrl();
        return url.replace("http://", "ws://").replace("https://", "wss://");
    }

    @Override
    public String toString() {
        return GsonHelper.toJson(this);
    }
}
