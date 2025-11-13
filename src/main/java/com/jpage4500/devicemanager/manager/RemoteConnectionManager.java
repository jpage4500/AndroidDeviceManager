package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Manages connections to remote ADB servers
 */
public class RemoteConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteConnectionManager.class);

    private final Map<String, RemoteConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private NetworkDiscoveryManager discoveryManager;
    private ConnectionListener listener;

    public interface ConnectionListener {
        void onConnectionEstablished(RemoteServerConfig server);
        void onConnectionLost(RemoteServerConfig server);
        void onDevicesUpdated(String serverId, List<Device> devices);
        void onServerDiscovered(RemoteServerConfig server); // From network discovery
    }

    /**
     * Load saved servers and connect to enabled ones
     */
    public void initialize() {
        // Initialize discovery manager but don't start it automatically
        discoveryManager = new NetworkDiscoveryManager();
        discoveryManager.setListener(new NetworkDiscoveryManager.DiscoveryListener() {
            @Override
            public void onServerDiscovered(RemoteServerConfig server) {
                log.info("Discovered server on network: {}", server.name);
                if (listener != null) {
                    listener.onServerDiscovered(server);
                }
            }

            @Override
            public void onServerLost(String serverId) {
                log.info("Server lost from network: {}", serverId);
            }
        });
        // Note: Discovery is NOT started here - call startNetworkDiscovery() explicitly

        // Load and connect to saved servers
        List<RemoteServerConfig> servers = loadServers();
        for (RemoteServerConfig server : servers) {
            if (server.enabled) {
                connectToServer(server);
            }
        }

        // Start health check task
        scheduler.scheduleAtFixedRate(this::checkConnections, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Connect to a remote server
     */
    public void connectToServer(RemoteServerConfig server) {
        if (connections.containsKey(server.id)) {
            log.warn("Already connected to server: {}", server.name);
            return;
        }

        RemoteConnection connection = new RemoteConnection(server);
        connections.put(server.id, connection);

        // Connect asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                connection.connect();
                server.isOnline = true;
                server.lastConnectedMs = System.currentTimeMillis();
                saveServers();

                if (listener != null) {
                    listener.onConnectionEstablished(server);
                }

                // Fetch initial device list
                refreshDevices(server.id);

            } catch (Exception e) {
                log.error("Failed to connect to server: {}, {}", server.name, e.getMessage());
                server.isOnline = false;
                connections.remove(server.id);
            }
        });
    }

    /**
     * Disconnect from a server
     */
    public void disconnectFromServer(String serverId) {
        RemoteConnection connection = connections.remove(serverId);
        if (connection != null) {
            connection.disconnect();

            RemoteServerConfig server = connection.getServerConfig();
            server.isOnline = false;

            if (listener != null) {
                listener.onConnectionLost(server);
            }
        }
    }

    /**
     * Refresh device list from a specific server
     */
    public void refreshDevices(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) return;

        CompletableFuture.supplyAsync(() -> {
            try {
                return connection.fetchDevices();
            } catch (Exception e) {
                log.error("Failed to fetch devices from server: {}", serverId, e);
                return Collections.<Device>emptyList();
            }
        }).thenAccept(devices -> {
            if (listener != null) {
                listener.onDevicesUpdated(serverId, devices);
            }
        });
    }

    /**
     * Refresh all servers
     */
    public void refreshAllDevices() {
        for (String serverId : connections.keySet()) {
            refreshDevices(serverId);
        }
    }

    /**
     * Get all remote devices from all connected servers
     */
    public List<Device> getAllRemoteDevices() {
        List<Device> allDevices = new ArrayList<>();
        for (RemoteConnection connection : connections.values()) {
            allDevices.addAll(connection.getCachedDevices());
        }
        return allDevices;
    }

    /**
     * Execute command on remote device
     */
    public String executeRemoteCommand(String serverId, String deviceSerial, String command) throws Exception {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            throw new Exception("Server not connected: " + serverId);
        }

        return connection.executeCommand(deviceSerial, command);
    }

    /**
     * List files on remote device
     */
    public java.util.List<com.jpage4500.devicemanager.data.DeviceFile> listRemoteFiles(String serverId, String deviceSerial, String path) throws Exception {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            throw new Exception("Server not connected: " + serverId);
        }

        return connection.listFiles(deviceSerial, path);
    }

    /**
     * Download file from remote device
     */
    public void downloadRemoteFile(String serverId, String deviceSerial, String path, String filename, java.io.File saveFile) throws Exception {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            throw new Exception("Server not connected: " + serverId);
        }

        connection.downloadFile(deviceSerial, path, filename, saveFile);
    }

    /**
     * Upload file to remote device
     */
    public void uploadRemoteFile(String serverId, String deviceSerial, String path, String filename, java.io.File localFile) throws Exception {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            throw new Exception("Server not connected: " + serverId);
        }

        connection.uploadFile(deviceSerial, path, filename, localFile);
    }

    /**
     * Add a new server configuration
     */
    public void addServer(RemoteServerConfig server) {
        List<RemoteServerConfig> servers = loadServers();
        servers.add(server);
        saveServers();

        if (server.enabled) {
            connectToServer(server);
        }
    }

    /**
     * Remove a server configuration
     */
    public void removeServer(String serverId) {
        disconnectFromServer(serverId);

        List<RemoteServerConfig> servers = loadServers();
        servers.removeIf(s -> s.id.equals(serverId));
        saveServers();
    }

    /**
     * Update server configuration
     */
    public void updateServer(RemoteServerConfig server) {
        List<RemoteServerConfig> servers = loadServers();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).id.equals(server.id)) {
                servers.set(i, server);
                break;
            }
        }
        saveServers();

        // Reconnect if necessary
        boolean wasConnected = connections.containsKey(server.id);
        if (wasConnected) {
            disconnectFromServer(server.id);
        }
        if (server.enabled) {
            connectToServer(server);
        }
    }

    /**
     * Check health of all connections
     */
    private void checkConnections() {
        for (Map.Entry<String, RemoteConnection> entry : connections.entrySet()) {
            String serverId = entry.getKey();
            RemoteConnection connection = entry.getValue();

            if (!connection.isHealthy()) {
                log.warn("Connection unhealthy: {}", connection.getServerConfig().name);
                connection.getServerConfig().isOnline = false;

                // Try to reconnect
                CompletableFuture.runAsync(() -> {
                    try {
                        connection.reconnect();
                        log.info("Reconnected to server: {}", connection.getServerConfig().name);
                        connection.getServerConfig().isOnline = true;
                        if (listener != null) {
                            listener.onConnectionEstablished(connection.getServerConfig());
                        }
                    } catch (Exception e) {
                        log.error("Failed to reconnect to server", e);
                    }
                });
            }
        }
    }

    /**
     * Load server configurations from preferences
     */
    private List<RemoteServerConfig> loadServers() {
        String json = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_REMOTE_SERVERS);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            RemoteServerConfig[] servers = GsonHelper.fromJson(json, RemoteServerConfig[].class);
            return new ArrayList<>(Arrays.asList(servers));
        } catch (Exception e) {
            log.error("Failed to load server configs", e);
            return new ArrayList<>();
        }
    }

    /**
     * Save server configurations to preferences
     */
    private void saveServers() {
        List<RemoteServerConfig> allServers = new ArrayList<>();

        // Add connected servers
        for (RemoteConnection connection : connections.values()) {
            allServers.add(connection.getServerConfig());
        }

        // Also add disconnected servers from saved list
        List<RemoteServerConfig> savedServers = loadServers();
        for (RemoteServerConfig saved : savedServers) {
            if (!connections.containsKey(saved.id)) {
                allServers.add(saved);
            }
        }

        String json = GsonHelper.toJson(allServers);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_REMOTE_SERVERS, json);
    }

    public List<RemoteServerConfig> getServers() {
        return loadServers();
    }

    public Map<String, RemoteServerConfig> getDiscoveredServers() {
        if (discoveryManager != null) {
            return discoveryManager.getDiscoveredServers();
        }
        return new HashMap<>();
    }

    /**
     * Start network discovery for remote servers
     * Call this when user opens the Remote Server Dialog
     */
    public void startNetworkDiscovery() {
        if (discoveryManager != null && !discoveryManager.isDiscovering()) {
            log.info("Starting network discovery for remote servers");
            discoveryManager.startDiscovery();
        }
    }

    /**
     * Stop network discovery
     * Call this when user closes the Remote Server Dialog
     */
    public void stopNetworkDiscovery() {
        if (discoveryManager != null && discoveryManager.isDiscovering()) {
            log.info("Stopping network discovery for remote servers");
            discoveryManager.stopDiscovery();
        }
    }

    public void setListener(ConnectionListener listener) {
        this.listener = listener;
    }

    public void shutdown() {
        log.trace("shutdown: ");
        for (RemoteConnection connection : connections.values()) {
            connection.disconnect();
        }
        connections.clear();

        if (discoveryManager != null) {
            discoveryManager.stopDiscovery();
        }

        scheduler.shutdownNow();
        try {
            scheduler.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    public boolean isConnected(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        return connection != null && connection.isConnected();
    }
}
