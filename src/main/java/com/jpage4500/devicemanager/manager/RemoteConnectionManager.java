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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private NetworkDiscoveryManager discoveryManager;
    private ConnectionListener listener;

    public interface ConnectionListener {
        void onConnectionEstablished(RemoteConnection connection);

        void onConnectionLost(RemoteConnection connection);

        void onDevicesUpdated(RemoteConnection connection, List<Device> devices);

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

        // load and connect to saved servers
        List<RemoteServerConfig> servers = loadServers();
        for (RemoteServerConfig server : servers) {
            if (server.enabled) {
                connectToServer(server);
            }
        }

        // connect to saved devices
        //scheduler.submit(this::checkConnections);
        //scheduler.scheduleAtFixedRate(this::checkConnections, 0, 30, TimeUnit.SECONDS);
    }

    /**
     * connect to all servers
     */
    private void checkConnections() {
        for (Map.Entry<String, RemoteConnection> entry : connections.entrySet()) {
            String serverId = entry.getKey();
            RemoteConnection connection = entry.getValue();
            checkConnection(serverId, connection);
        }

        // schedule another check in 30 seconds
        scheduler.schedule(this::checkConnections, 30, TimeUnit.SECONDS);
    }

    private void checkConnection(String serverId, RemoteConnection connection) {
        // fetch server info
        RemoteHttpServer.ServerInfo serverInfo = connection.fetchServerInfo();
        if (serverInfo != null) {
            if (listener != null) {
                listener.onConnectionEstablished(connection);
            }
            // check if device count has changed
            if (serverInfo.deviceCount != connection.getDeviceCount()) {
                // run device list request after all connections have been checked
                scheduler.submit(() -> fetchDevices(serverId));
            }
        }
    }

    /**
     * Refresh device list from a specific server
     */
    public void fetchDevices(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) return;

        log.debug("Device count changed on server: {}", connection.getServerConfig().name);
        List<Device> deviceList = connection.fetchDevices();
        if (listener != null) {
            listener.onDevicesUpdated(connection, deviceList);
        }
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

        scheduler.submit(() -> checkConnection(server.id, connection));
    }

    /**
     * Disconnect from a server
     */
    public void disconnectFromServer(String serverId) {
        RemoteConnection connection = connections.remove(serverId);
        if (connection != null) {
            connection.disconnect();
            if (listener != null) {
                listener.onConnectionLost(connection);
            }
        }
    }

    /**
     * Refresh all servers
     */
    public void refreshAllDevices() {
        for (String serverId : connections.keySet()) {
            fetchDevices(serverId);
        }
    }

    /**
     * Get all remote devices from all connected servers
     */
    public List<Device> getAllRemoteDevices() {
        List<Device> allDevices = new ArrayList<>();
        for (RemoteConnection connection : connections.values()) {
            allDevices.addAll(connection.getDeviceList());
        }
        return allDevices;
    }

    /**
     * Execute command on remote device
     */
    public DeviceManager.ShellResult executeRemoteCommand(String serverId, String deviceSerial, String command) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            return new DeviceManager.ShellResult(false, null);
        }
        return connection.executeCommand(deviceSerial, command);
    }

    /**
     * List files on remote device
     */
    public DeviceManager.FileResponse listRemoteFiles(String serverId, String deviceSerial, String path) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            return new DeviceManager.FileResponse(null, "Server not connected: " + serverId);
        }
        return connection.fetchFileList(deviceSerial, path);
    }

    /**
     * Download file from remote device
     */
    public boolean downloadRemoteFile(String serverId, String deviceSerial, String path, String filename, java.io.File saveFile) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            log.error("downloadRemoteFile: Server not connected: {}", serverId);
            return false;
        }
        return connection.downloadFile(deviceSerial, path, filename, saveFile);
    }

    /**
     * Upload file to remote device
     */
    public boolean uploadRemoteFile(String serverId, String deviceSerial, String path, String filename, java.io.File localFile) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) {
            log.error("uploadRemoteFile: Server not connected: {}", serverId);
            return false;
        }

        return connection.uploadFile(deviceSerial, path, filename, localFile);
    }

    /**
     * Add a new server configuration
     */
    public void addServer(RemoteServerConfig server) {
        List<RemoteServerConfig> servers = loadServers();
        servers.add(server);
        saveServers(servers);

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
        saveServers(servers);
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
        saveServers(servers);

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
     * Load server configurations from preferences
     */
    private List<RemoteServerConfig> loadServers() {
        String serverStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_CONNECTED_SERVERS);
        return GsonHelper.stringToList(serverStr, RemoteServerConfig.class);
    }

    /**
     * Save server configurations to preferences
     */
    private void saveServers(List<RemoteServerConfig> serversToSave) {
        String json = GsonHelper.toJson(serversToSave);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_CONNECTED_SERVERS, json);
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
            log.debug("Starting network discovery for remote servers");
            discoveryManager.startDiscovery();
        }
    }

    /**
     * Stop network discovery
     * Call this when user closes the Remote Server Dialog
     */
    public void stopNetworkDiscovery() {
        if (discoveryManager != null && discoveryManager.isDiscovering()) {
            log.debug("Stopping network discovery for remote servers");
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
