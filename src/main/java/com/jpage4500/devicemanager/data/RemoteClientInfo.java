package com.jpage4500.devicemanager.data;

import java.util.UUID;

/**
 * Information about a connected remote client (for server mode)
 */
public class RemoteClientInfo {
    public String id;              // unique client ID
    public String ipAddress;       // client IP
    public String name;            // client name (optional)
    public long connectedAtMs;     // connection timestamp
    public long lastActivityMs;    // last request timestamp
    public int requestCount;       // total requests made

    public RemoteClientInfo(String ipAddress) {
        this.id = UUID.randomUUID().toString();
        this.ipAddress = ipAddress;
        this.connectedAtMs = System.currentTimeMillis();
        this.lastActivityMs = connectedAtMs;
    }
}

