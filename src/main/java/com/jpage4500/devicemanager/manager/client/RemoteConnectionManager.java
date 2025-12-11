package com.jpage4500.devicemanager.manager.client;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.manager.server.RemoteHttpServer;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages connections to remote ADB servers
 */
public class RemoteConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteConnectionManager.class);

    private final RemoteConnectionListener listener;

    // each entry is a remote server connection
    private final Map<String, RemoteConnection> connections = new ConcurrentHashMap<>();

    public interface RemoteConnectionListener {
        void onRemoteConnection(RemoteConnection connection);

        void onRemoteConnectionLost(RemoteConnection connection);

        void onRemoteDevicesUpdated(RemoteConnection connection, List<Device> devices);

        void onRemoteServerRemoved(RemoteConnection connection);
    }

    public RemoteConnectionManager(RemoteConnectionListener listener) {
        this.listener = listener;
        initialize();
    }

    public void initialize() {
        // load and connect to saved servers
        List<RemoteServerConfig> configList = getServers();
        log.debug("initialize: loaded {} servers", configList.size());
        for (RemoteServerConfig config : configList) {
            log.trace("initialize: {}", config);
            if (config.enabled) {
                RemoteConnection connection = new RemoteConnection(config, listener);
                connections.put(config.id, connection);
            }
        }
    }

    /**
     * Refresh all servers
     */
    public void refreshAllDevices(boolean fullRefresh) {
        for (Map.Entry<String, RemoteConnection> entry : connections.entrySet()) {
            fetchServerInfo(entry.getKey(), entry.getValue(), fullRefresh);
        }
    }

    private void fetchServerInfo(String serverId, RemoteConnection connection, boolean fullRefresh) {
        // fetch server info
        RemoteHttpServer.ServerInfo serverInfo = connection.fetchServerInfo();
        if (serverInfo != null) {
            listener.onRemoteConnection(connection);
            // check if device count has changed
            if (fullRefresh || serverInfo.deviceCount != connection.getDeviceCount()) {
                // run device list request after all connections have been checked
                fetchDevices(serverId);
            }
        } else {
            listener.onRemoteConnectionLost(connection);
        }
    }

    /**
     * Refresh device list from a specific server
     */
    public void fetchDevices(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) return;

        List<Device> deviceList = connection.fetchDevices();
        listener.onRemoteDevicesUpdated(connection, deviceList);
    }

    /**
     * Check if a server exists with the same host and port. Optionally exclude a server by ID.
     */
    public boolean isServerExist(String host, int port, String excludeServerId) {
        if (host == null) return false;
        List<RemoteServerConfig> configList = getServers();
        for (RemoteServerConfig config : configList) {
            if (excludeServerId != null && excludeServerId.equals(config.id)) continue;
            if (host.equalsIgnoreCase(config.host) && port == config.port) {
                String msg = String.format("A server with host '%s' and port %d already exists", config.host, config.port);
                log.warn("isServerExist: {}", msg);
                DialogHelper.showDialog(null, "Duplicate Server", msg);
                return true;
            }
        }
        return false;
    }

    /**
     * Add a new server configuration
     */
    public void addServer(RemoteServerConfig addConfig) {
        // prevent duplicates by host/port
        if (isServerExist(addConfig.host, addConfig.port, null)) return;

        List<RemoteServerConfig> configList = getServers();
        configList.add(addConfig);
        saveServers(configList);

        if (addConfig.enabled) {
            RemoteConnection connection = new RemoteConnection(addConfig, listener);
            connections.put(addConfig.id, connection);
        }
    }

    /**
     * Remove a server configuration
     */
    public void removeServer(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        if (connection != null) {
            // notify listener to remove devices before disconnecting
            listener.onRemoteServerRemoved(connection);
        }

        List<RemoteServerConfig> configList = getServers();
        configList.removeIf(config -> config.id.equals(serverId));
        saveServers(configList);
    }

    /**
     * Update server configuration
     */
    public void updateServer(RemoteServerConfig updateConfig) {
        // prevent duplicates when updating (exclude the server itself)
        if (isServerExist(updateConfig.host, updateConfig.port, updateConfig.id)) return;

        removeServer(updateConfig.id);

        // Add the updated config
        addServer(updateConfig);
    }

    /**
     * Load server configurations from preferences
     */
    public List<RemoteServerConfig> getServers() {
        String serverStr = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_CONNECTED_SERVERS);
        return GsonHelper.stringToList(serverStr, RemoteServerConfig.class);
    }

    public List<RemoteConnection> getActiveConnections() {
        return new ArrayList<>(connections.values());
    }

    /**
     * Save server configurations to preferences
     */
    private void saveServers(List<RemoteServerConfig> serversToSave) {
        String json = GsonHelper.toJson(serversToSave);
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_CONNECTED_SERVERS, json);
    }

    public void shutdown() {
        for (RemoteConnection connection : connections.values()) {
            connection.disconnect();
        }
        connections.clear();
    }

    public boolean isConnected(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        return connection != null && connection.isConnected();
    }

}
