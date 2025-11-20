package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.RemoteClientInfo;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the HTTP server that shares local devices with remote clients
 */
public class RemoteServerManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteServerManager.class);

    public static final int DEFAULT_PORT = 8765;

    private RemoteHttpServer httpServer;
    private int port;
    private String authToken;
    private boolean isRunning;

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

    public RemoteServerManager(ServerListener listener) {
        this.listener = listener;
        initialize();
    }

    /**
     * Initialize the server manager and auto-start if previously enabled
     * Call this when the application starts
     */
    public void initialize() {
        // Check if server was running when app last closed
        boolean wasEnabled = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SERVER_ENABLED);
        if (wasEnabled) {
            // Get saved port and auth token
            int savedPort = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_SERVER_PORT, DEFAULT_PORT);
            String savedToken = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN);
            if (TextUtils.notEmpty(savedToken) && savedPort > 0) {
                // Auto-start the server
                startServer(savedPort, savedToken);
            }
        }
    }

    public void startServer(int port, String authToken) {
        if (isRunning) {
            log.warn("startServer: already running on port: {}", this.port);
            return;
        }

        this.port = port;
        this.authToken = authToken;

        try {
            httpServer = new RemoteHttpServer(port, this.authToken, this);
            // Use longer timeout (60 seconds) to support WebSocket connections
            // WebSocket connections are long-lived and need more time between client messages
            httpServer.start(60000, false);
            isRunning = true;

            // Save preferences
            PreferenceUtils.setPreference(PreferenceUtils.PrefBoolean.PREF_SERVER_ENABLED, true);
            PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_SERVER_PORT, port);
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_SERVER_AUTH_TOKEN, this.authToken);

            log.info("startServer: port: {}", port);
            if (listener != null) listener.onServerStarted(port);
        } catch (IOException e) {
            log.error("startServer: error", e);
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

        isRunning = false;
        connectedClients.clear();

        log.info("stopServer: stopped");
        if (listener != null) listener.onServerStopped();
    }

    // Getters
    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    public String getAuthToken() {
        return authToken;
    }

    public List<RemoteClientInfo> getConnectedClients() {
        return new ArrayList<>(connectedClients.values());
    }

    public void trackClient(String clientIp, String clientName) {
        RemoteClientInfo client = connectedClients.get(clientIp);
        if (client == null) {
            client = new RemoteClientInfo(clientIp);
            if (clientName != null && !clientName.isEmpty()) {
                client.name = clientName;
            }
            connectedClients.put(clientIp, client);
            if (listener != null) listener.onClientConnected(client);
        } else if (client.name == null && clientName != null && !clientName.isEmpty()) {
            // update name if it was previously unknown
            client.name = clientName;
        }
        client.lastActivityMs = System.currentTimeMillis();
        client.requestCount++;
    }

}
