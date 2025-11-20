package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages connections to remote ADB servers
 */
public class RemoteConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteConnectionManager.class);

    public static final int REFRESH_SECS = 30;

    private final Map<String, RemoteConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Future<?> future;
    private final ConnectionListener listener;

    public interface ConnectionListener {
        void onRemoteConnection(RemoteConnection connection);

        void onRemoteConnectionLost(RemoteConnection connection);

        void onRemoteDevicesUpdated(RemoteConnection connection, List<Device> devices);
    }

    public RemoteConnectionManager(ConnectionListener listener) {
        this.listener = listener;

        // load and connect to saved servers
        List<RemoteServerConfig> configList = getServers();
        for (RemoteServerConfig config : configList) {
            if (config.enabled) {
                RemoteConnection connection = new RemoteConnection(config);
                connections.put(config.id, connection);
            }
        }

        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (!connections.isEmpty()) {
            // if a refresh is pending, stop it
            if (future != null) future.cancel(false);
            // refresh all devices NOW and again every 30 seconds
            future = scheduler.scheduleWithFixedDelay(this::refreshAllDevices, 0, REFRESH_SECS, TimeUnit.SECONDS);
        } else {
            // no servers configured - stop refreshing
            if (future != null) {
                future.cancel(false);
                future = null;
            }
        }
    }

    /**
     * Refresh all servers
     */
    public void refreshAllDevices() {
        for (Map.Entry<String, RemoteConnection> entry : connections.entrySet()) {
            fetchServerInfo(entry.getKey(), entry.getValue());
        }
    }

    private void fetchServerInfo(String serverId, RemoteConnection connection) {
        // fetch server info
        RemoteHttpServer.ServerInfo serverInfo = connection.fetchServerInfo();
        if (serverInfo != null) {
            if (listener != null) {
                listener.onRemoteConnection(connection);
            }
            // check if device count has changed
            if (serverInfo.deviceCount != connection.getDeviceCount()) {
                // run device list request after all connections have been checked
                fetchDevices(serverId);
            }
        }
    }

    /**
     * Refresh device list from a specific server
     */
    public void fetchDevices(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        if (connection == null) return;

        List<Device> deviceList = connection.fetchDevices();
        if (listener != null) {
            listener.onRemoteDevicesUpdated(connection, deviceList);
        }
    }

    /**
     * Add a new server configuration
     */
    public void addServer(RemoteServerConfig addConfig) {
        List<RemoteServerConfig> configList = getServers();
        configList.add(addConfig);
        saveServers(configList);

        if (addConfig.enabled) {
            RemoteConnection connection = new RemoteConnection(addConfig);
            connections.put(addConfig.id, connection);

            // refresh devices
            scheduleRefresh();
        }
    }

    /**
     * Remove a server configuration
     */
    public void removeServer(String serverId) {
        disconnectFromServer(serverId);

        List<RemoteServerConfig> configList = getServers();
        configList.removeIf(config -> config.id.equals(serverId));
        saveServers(configList);
    }

    /**
     * Update server configuration
     */
    public void updateServer(RemoteServerConfig updateConfig) {
        disconnectFromServer(updateConfig.id);
        addServer(updateConfig);
    }

    /**
     * Disconnect from a server
     */
    private void disconnectFromServer(String serverId) {
        RemoteConnection connection = connections.remove(serverId);
        if (connection != null) {
            connection.disconnect();
            if (listener != null) {
                listener.onRemoteConnectionLost(connection);
            }
        }
    }

    /**
     * Load server configurations from preferences
     */
    public List<RemoteServerConfig> getServers() {
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

    public void shutdown() {
        for (RemoteConnection connection : connections.values()) {
            connection.disconnect();
        }
        connections.clear();
        scheduler.shutdownNow();
    }

    public boolean isConnected(String serverId) {
        RemoteConnection connection = connections.get(serverId);
        return connection != null && connection.isConnected();
    }
}
