package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.utils.GsonHelper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Represents a connection to a single remote ADB server
 */
public class RemoteConnection {
    private static final Logger log = LoggerFactory.getLogger(RemoteConnection.class);

    private final RemoteServerConfig serverConfig;
    private CloseableHttpClient httpClient;
    private List<Device> cachedDevices = new ArrayList<>();
    private long lastHealthCheck = 0;
    private boolean isConnected = false;

    public RemoteConnection(RemoteServerConfig config) {
        this.serverConfig = config;
        this.httpClient = HttpClients.createDefault();
    }

    /**
     * Establish connection to server
     */
    public void connect() throws IOException {
        // Test connection with server info endpoint
        @SuppressWarnings("unchecked")
        Map<String, Object> info = httpGet("/api/info", Map.class);
        log.info("Connected to server: {} - {}", serverConfig.name, info);
        isConnected = true;
        lastHealthCheck = System.currentTimeMillis();
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (IOException e) {
            log.error("Error closing HTTP client", e);
        }
        isConnected = false;
        httpClient = null;
    }

    /**
     * Reconnect to server
     */
    public void reconnect() throws IOException {
        disconnect();
        httpClient = HttpClients.createDefault();
        connect();
    }

    /**
     * Fetch device list from remote server
     */
    public List<Device> fetchDevices() throws IOException {
        List<?> deviceDataList = httpGet("/api/devices", List.class);

        List<Device> devices = new ArrayList<>();
        for (Object obj : deviceDataList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) obj;

            Device device = new Device();
            device.serial = (String) data.get("serial");
            device.nickname = (String) data.get("nickname");
            device.isOnline = (Boolean) data.getOrDefault("isOnline", false);
            device.isBooted = (Boolean) data.getOrDefault("isBooted", false);

            Object batteryObj = data.get("batteryLevel");
            if (batteryObj instanceof Number) {
                device.batteryLevel = ((Number) batteryObj).intValue();
            }

            // Mark as remote
            device.isRemote = true;
            device.remoteServerId = serverConfig.id;
            device.remoteServerName = serverConfig.name;
            device.remoteConnection = this;

            // Set properties
            if (device.propMap == null) {
                device.propMap = new HashMap<>();
            }
            device.propMap.put(Device.PROP_MODEL, (String) data.get("model"));
            device.propMap.put(Device.PROP_OS, (String) data.get("os"));

            devices.add(device);
        }

        cachedDevices = devices;
        return devices;
    }

    /**
     * Execute command on remote device
     */
    public String executeCommand(String deviceSerial, String command) throws IOException {
        Map<String, String> request = new HashMap<>();
        request.put("serial", deviceSerial);
        request.put("command", command);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = httpPost("/api/execute", request, Map.class);

        boolean success = (Boolean) response.getOrDefault("success", false);
        String output = (String) response.getOrDefault("output", "");
        String error = (String) response.getOrDefault("error", "");

        if (!success && error != null && !error.isEmpty()) {
            throw new IOException("Remote command failed: " + error);
        }

        return output;
    }

    /**
     * Check if connection is healthy
     */
    public boolean isHealthy() {
        long now = System.currentTimeMillis();
        if (now - lastHealthCheck < 30000) {
            return isConnected;
        }

        try {
            httpGet("/api/info", Map.class);
            lastHealthCheck = now;
            isConnected = true;
            return true;
        } catch (Exception e) {
            log.warn("Health check failed for server: {}", serverConfig.name);
            isConnected = false;
            return false;
        }
    }

    /**
     * HTTP GET request
     */
    private <T> T httpGet(String path, Class<T> responseType) throws IOException {
        String url = serverConfig.getConnectionUrl() + path;
        HttpGet request = new HttpGet(url);
        request.setHeader("Authorization", "Bearer " + serverConfig.authToken);

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String json = EntityUtils.toString(response.getEntity());
            return GsonHelper.fromJson(json, responseType);
        }
    }

    /**
     * HTTP POST request
     */
    private <T> T httpPost(String path, Object body, Class<T> responseType) throws IOException {
        String url = serverConfig.getConnectionUrl() + path;
        HttpPost request = new HttpPost(url);
        request.setHeader("Authorization", "Bearer " + serverConfig.authToken);
        request.setHeader("Content-Type", "application/json");

        String json = GsonHelper.toJson(body);
        request.setEntity(new StringEntity(json));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            String responseJson = EntityUtils.toString(response.getEntity());
            return GsonHelper.fromJson(responseJson, responseType);
        }
    }

    public RemoteServerConfig getServerConfig() {
        return serverConfig;
    }

    public List<Device> getCachedDevices() {
        return new ArrayList<>(cachedDevices);
    }

    public boolean isConnected() {
        return isConnected;
    }
}

