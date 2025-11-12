package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.utils.GsonHelper;
import org.apache.http.client.config.RequestConfig;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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

    // added timeouts
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int SOCKET_TIMEOUT_MS = 6000;

    public RemoteConnection(RemoteServerConfig config) {
        this.serverConfig = config;
        this.httpClient = buildHttpClient(); // changed
    }

    // build http client with timeouts
    private CloseableHttpClient buildHttpClient() {
        RequestConfig cfg = RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MS)
            .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();
        return HttpClients.custom().setDefaultRequestConfig(cfg).build();
    }

    // quick port reachability test
    private boolean testSocketReachable() {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(serverConfig.host, serverConfig.port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) { return false; }
    }

    /**
     * Establish connection to server with diagnostics
     */
    public void connect() throws IOException {
        long startMs = System.currentTimeMillis();
        serverConfig.lastError = null;

        // DNS resolution
        try {
            InetAddress addr = InetAddress.getByName(serverConfig.host);
            log.debug("connect: resolved {} -> {}", serverConfig.host, addr.getHostAddress());
        } catch (Exception e) {
            serverConfig.lastError = "Host resolution failed: " + e.getMessage();
            throw new IOException(serverConfig.lastError, e);
        }
        // Port reachability
        if (!testSocketReachable()) {
            serverConfig.lastError = "Port unreachable: " + serverConfig.host + ":" + serverConfig.port;
            throw new IOException(serverConfig.lastError);
        }
        // Handshake
        try {
            @SuppressWarnings("unchecked") Map<String,Object> info = httpGet("/api/info", Map.class);
            log.info("Connected to server: {} - {}", serverConfig.name, info);
            isConnected = true;
            serverConfig.isOnline = true;
            lastHealthCheck = System.currentTimeMillis();
        } catch (IOException e) {
            serverConfig.lastError = "Handshake failed: " + e.getMessage();
            isConnected = false;
            serverConfig.isOnline = false;
            throw e;
        } finally {
            log.debug("connect: {} elapsed={}ms success={}", serverConfig.name, (System.currentTimeMillis()-startMs), isConnected);
        }
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
        httpClient = buildHttpClient();
        connect();
    }

    /**
     * Fetch device list from remote server
     */
    public List<Device> fetchDevices() throws IOException {
        List<?> deviceDataList = httpGet("/api/devices", List.class);

        List<Device> devices = new ArrayList<>();
        for (Object obj : deviceDataList) {
            @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) obj;

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

        @SuppressWarnings("unchecked") Map<String, Object> response = httpPost("/api/execute", request, Map.class);

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
            serverConfig.isOnline = true;
            serverConfig.lastError = null;
            return true;
        } catch (Exception e) {
            log.warn("Health check failed for server: {}", serverConfig.name);
            isConnected = false;
            serverConfig.isOnline = false;
            serverConfig.lastError = "Health check failed: " + e.getMessage();
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
            int status = response.getStatusLine().getStatusCode();
            String json = EntityUtils.toString(response.getEntity());
            if (status >= 400) {
                throw new IOException("HTTP " + status + " GET " + path + " body=" + json);
            }
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
            int status = response.getStatusLine().getStatusCode();
            String responseJson = EntityUtils.toString(response.getEntity());
            if (status >= 400) {
                throw new IOException("HTTP " + status + " POST " + path + " body=" + responseJson);
            }
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

