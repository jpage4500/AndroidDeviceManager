package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.RemoteClientInfo;
import com.jpage4500.devicemanager.utils.ConnectionStringUtils;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the HTTP server that shares local devices with remote clients
 */
public class RemoteServerManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteServerManager.class);

    private static final int DEFAULT_PORT = 8765;

    private RemoteHttpServer httpServer;
    private int port;
    private String authToken;
    private boolean isRunning;

    // Network discovery
    private NetworkDiscoveryManager discoveryManager;

    // Track connected clients
    private final Map<String, RemoteClientInfo> connectedClients = new ConcurrentHashMap<>();

    // Callback for UI updates
    private ServerListener listener;

    public interface ServerListener {
        void onServerStarted(int port);
        void onServerStopped();
        void onClientConnected(RemoteClientInfo client);
        void onClientDisconnected(RemoteClientInfo client);
        void onError(Exception e);
    }

    /**
     * Start the remote server
     */
    public void startServer() {
        startServer(DEFAULT_PORT);
    }

    public void startServer(int port) {
        if (isRunning) {
            log.warn("Server already running on port {}", this.port);
            return;
        }

        this.port = port;
        this.authToken = generateAuthToken();

        try {
            httpServer = new RemoteHttpServer(port, authToken, this);
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            isRunning = true;

            // Start network discovery broadcasting
            if (discoveryManager == null) {
                discoveryManager = new NetworkDiscoveryManager();
            }
            String serverName = getDeviceName();
            discoveryManager.startBroadcasting(port, authToken, serverName);

            // Save preferences
            PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_SERVER_ENABLED, true);
            PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_SERVER_PORT, port);
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN, authToken);

            log.info("Remote server started on port {} with broadcasting", port);
            if (listener != null) listener.onServerStarted(port);

        } catch (IOException e) {
            log.error("Failed to start server", e);
            if (listener != null) listener.onError(e);
        }
    }

    /**
     * Stop the remote server
     */
    public void stopServer() {
        if (!isRunning) return;

        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }

        // Stop network discovery broadcasting
        if (discoveryManager != null) {
            discoveryManager.stopBroadcasting();
        }

        isRunning = false;
        connectedClients.clear();

        PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_SERVER_ENABLED, false);

        log.info("Remote server stopped");
        if (listener != null) listener.onServerStopped();
    }

    /**
     * Generate a random authentication token
     */
    private String generateAuthToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * Get connection string for easy sharing
     */
    public String getConnectionString() {
        return ConnectionStringUtils.generateConnectionString(
            getLocalIpAddress(),
            port,
            authToken,
            getDeviceName()
        );
    }

    /**
     * Get shareable URL
     */
    public String getShareUrl() {
        return ConnectionStringUtils.generateShareUrl(
            getLocalIpAddress(),
            port,
            authToken,
            getDeviceName()
        );
    }

    /**
     * Get local IP address
     */
    private String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private String getDeviceName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    // Getters
    public boolean isRunning() { return isRunning; }
    public int getPort() { return port; }
    public String getAuthToken() { return authToken; }
    public List<RemoteClientInfo> getConnectedClients() {
        return new ArrayList<>(connectedClients.values());
    }

    // Client tracking
    void trackClient(String clientIp) {
        RemoteClientInfo client = connectedClients.get(clientIp);
        if (client == null) {
            client = new RemoteClientInfo(clientIp);
            connectedClients.put(clientIp, client);
            if (listener != null) listener.onClientConnected(client);
        }
        client.lastActivityMs = System.currentTimeMillis();
        client.requestCount++;
    }

    public void setListener(ServerListener listener) {
        this.listener = listener;
    }
}

