package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.NetworkHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a connection to a single remote ADB server
 */
public class RemoteConnection {
    private static final Logger log = LoggerFactory.getLogger(RemoteConnection.class);

    private final RemoteServerConfig serverConfig;
    private NetworkHelper networkHelper;
    private final List<Device> deviceList = new ArrayList<>();
    private long lastHealthCheck = 0;
    private boolean isConnected = false;

    // WebSocket log streaming
    private final Map<String, LogStreamSession> logStreamSessions = new ConcurrentHashMap<>();
    private HttpClient httpClient;

    public RemoteConnection(RemoteServerConfig config) {
        this.serverConfig = config;
        networkHelper = new NetworkHelper();
        httpClient = HttpClient.newHttpClient();
    }

    /**
     * Represents an active log streaming session
     */
    private static class LogStreamSession {
        WebSocket webSocket;
        DeviceManager.DeviceLogListener listener;
        String deviceSerial;
        StringBuilder messageBuffer = new StringBuilder();

        LogStreamSession(WebSocket ws, DeviceManager.DeviceLogListener listener, String serial) {
            this.webSocket = ws;
            this.listener = listener;
            this.deviceSerial = serial;
        }
    }

    /**
     * Connect to server and fetch details
     */
    public RemoteHttpServer.ServerInfo fetchServerInfo() {
        Map<String, String> headers = getDefaultHeaders();
        String url = serverConfig.getUrl() + RemoteHttpServer.API_INFO;
        NetworkHelper.HttpResponse response = networkHelper.getRequest(url, headers);
        log.trace("fetchServerInfo: {}", GsonHelper.toJson(response));
        if (response.status == 200) {
            isConnected = true;
            lastHealthCheck = System.currentTimeMillis();
            return GsonHelper.fromJson(response.body, RemoteHttpServer.ServerInfo.class);
        } else {
            isConnected = false;
            return null;
        }
    }

    /**
     * Fetch device list from remote server
     */
    public List<Device> fetchDevices() {
        Map<String, String> headers = getDefaultHeaders();
        String url = serverConfig.getUrl() + RemoteHttpServer.API_DEVICES;
        NetworkHelper.HttpResponse response = networkHelper.getRequest(url, headers);
        log.trace("fetchDevices: {}", GsonHelper.toJson(response));
        if (response.status == 200) {
            List<Device> deviceList = GsonHelper.stringToList(response.body, Device.class);
            for (Device device : deviceList) {
                // mark as remote
                device.remoteConnection = this;
            }
            this.deviceList.clear();
            this.deviceList.addAll(deviceList);
        }
        return deviceList;
    }

    /**
     * List files on remote device
     */
    public DeviceManager.FileResponse fetchFileList(String deviceSerial, String path) {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        String url = serverConfig.getUrl() + RemoteHttpServer.API_FILES_LIST + "?serial=" + deviceSerial + "&path=" + encodedPath;
        Map<String, String> headers = getDefaultHeaders();
        NetworkHelper.HttpResponse response = networkHelper.getRequest(url, headers);
        log.trace("fetchFileList: {}", GsonHelper.toJson(response));
        if (response.status == 200) {
            List<DeviceFile> fileList = GsonHelper.stringToList(response.body, DeviceFile.class);
            return new DeviceManager.FileResponse(fileList, null);
        } else {
            return new DeviceManager.FileResponse(null, response.body);
        }
    }

    /**
     * List files on remote device
     */
    public Map<String, String> fetchDeviceProperties(String deviceSerial) {
        String url = serverConfig.getUrl() + RemoteHttpServer.API_DEVICE_PROPERTIES + "?serial=" + deviceSerial;
        Map<String, String> headers = getDefaultHeaders();
        NetworkHelper.HttpResponse response = networkHelper.getRequest(url, headers);
        log.trace("fetchFileList: {}", GsonHelper.toJson(response));
        if (response.status == 200) {
            return GsonHelper.stringToMap(response.body, String.class, String.class);
        } else {
            return null;
        }
    }

    private Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        // all requests require authorization
        headers.put(RemoteHttpServer.HEADER_AUTHORIZATION, "Bearer " + serverConfig.authToken);
        headers.put("Accept", "application/json");
        // add client info (name, IP)
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            headers.put(RemoteHttpServer.HEADER_IP, localHost.getHostAddress());
            headers.put(RemoteHttpServer.HEADER_NAME, localHost.getHostName());
        } catch (Exception e) {
            log.trace("getDefaultHeaders: Exception: {}", e.getMessage());
        }

        return headers;
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        isConnected = false;
    }

    /**
     * Execute command on remote device
     */
    public DeviceManager.ShellResult executeCommand(String deviceSerial, String command) {
        Map<String, String> request = new HashMap<>();
        request.put("serial", deviceSerial);
        request.put("command", command);

        Map<String, String> headers = getDefaultHeaders();
        headers.put("Content-Type", "application/json");
        String url = serverConfig.getUrl() + RemoteHttpServer.API_EXECUTE;
        NetworkHelper.HttpResponse response = networkHelper.postRequest(url, GsonHelper.toJson(request), headers);

        if (response.status == 200) {
            return GsonHelper.fromJson(response.body, DeviceManager.ShellResult.class);
        } else {
            return new DeviceManager.ShellResult(false, null);
        }
    }

    /**
     * Download file from remote device
     */
    public boolean downloadFile(String deviceSerial, String path, String filename, File saveFile) {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        String encodedFile = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        String url = serverConfig.getUrl() + RemoteHttpServer.API_FILES_DOWNLOAD + "?serial=" + deviceSerial +
            "&path=" + encodedPath + "&file=" + encodedFile;

        Map<String, String> headers = getDefaultHeaders();
        NetworkHelper.HttpResponse response = networkHelper.download(url, saveFile, headers);
        return response.status == 200;
    }

    /**
     * Upload file to remote device
     */
    public boolean uploadFile(String deviceSerial, String path, String filename, File localFile) {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        String encodedFile = URLEncoder.encode(filename, StandardCharsets.UTF_8);
        String url = serverConfig.getUrl() + RemoteHttpServer.API_FILES_UPLOAD + "?serial=" + deviceSerial +
            "&path=" + encodedPath + "&file=" + encodedFile;

        Map<String, String> headers = getDefaultHeaders();
        NetworkHelper.HttpResponse response = networkHelper.upload(url, localFile, headers);
        return response.status == 200;
    }

    public RemoteServerConfig getServerConfig() {
        return serverConfig;
    }

    public List<Device> getDeviceList() {
        return new ArrayList<>(deviceList);
    }

    public int getDeviceCount() {
        return deviceList.size();
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getId() {
        return serverConfig.id;
    }

    public String toString() {
        return GsonHelper.toJson(serverConfig);
    }

    /**
     * Start streaming logs from remote device via WebSocket
     *
     * @param deviceSerial  Device serial number
     * @param lastLogTime   Optional last log time to resume from
     * @param filterText    Optional filter expression (e.g., "level:E && tag:*MyTag*")
     * @param listener      Listener to receive log entries
     */
    public void startLogging(String deviceSerial, String lastLogTime, String filterText, DeviceManager.DeviceLogListener listener) {
        // Stop any existing session
        stopLogging(deviceSerial);

        log.debug("startLogging: serial={}, filter={}", deviceSerial, filterText);

        // Build WebSocket URL
        String wsUrl = buildWebSocketUrl(deviceSerial, filterText);

        // Create WebSocket listener
        WebSocket.Listener wsListener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                log.info("WebSocket opened for device: {}", deviceSerial);
                WebSocket.Listener.super.onOpen(webSocket);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                LogStreamSession session = logStreamSessions.get(deviceSerial);
                if (session != null) {
                    // Accumulate message parts
                    session.messageBuffer.append(data);

                    if (last) {
                        // Complete message received
                        String message = session.messageBuffer.toString();
                        session.messageBuffer.setLength(0);
                        handleWebSocketMessage(message, session);
                    }
                }
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                log.info("WebSocket closed for device: {}, code: {}, reason: {}", deviceSerial, statusCode, reason);
                logStreamSessions.remove(deviceSerial);
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.error("WebSocket error for device: {}", deviceSerial, error);
                logStreamSessions.remove(deviceSerial);
                WebSocket.Listener.super.onError(webSocket, error);
            }
        };

        // Connect WebSocket
        try {
            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), wsListener);

            wsFuture.whenComplete((ws, throwable) -> {
                if (throwable != null) {
                    log.error("Failed to connect WebSocket for device: {}", deviceSerial, throwable);
                } else {
                    // Store session
                    LogStreamSession session = new LogStreamSession(ws, listener, deviceSerial);
                    logStreamSessions.put(deviceSerial, session);
                    log.debug("WebSocket session created for device: {}", deviceSerial);
                }
            });
        } catch (Exception e) {
            log.error("Error starting log stream for device: {}", deviceSerial, e);
        }
    }

    /**
     * Stop streaming logs from remote device
     */
    public void stopLogging(String deviceSerial) {
        LogStreamSession session = logStreamSessions.remove(deviceSerial);
        if (session != null && session.webSocket != null) {
            log.debug("stopLogging: {}", deviceSerial);
            session.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing")
                .whenComplete((ws, throwable) -> {
                    if (throwable != null) {
                        log.warn("Error closing WebSocket: {}", throwable.getMessage());
                    }
                });
        }
    }

    /**
     * Check if currently streaming logs for a device
     */
    public boolean isLogging(String deviceSerial) {
        return logStreamSessions.containsKey(deviceSerial);
    }

    /**
     * Pause log streaming (sends pause control message)
     */
    public void pauseLogging(String deviceSerial) {
        LogStreamSession session = logStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            sendControlMessage(session.webSocket, "pause", null);
        }
    }

    /**
     * Resume log streaming (sends resume control message)
     */
    public void resumeLogging(String deviceSerial) {
        LogStreamSession session = logStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            sendControlMessage(session.webSocket, "resume", null);
        }
    }

    /**
     * Update filter dynamically (sends filter control message)
     */
    public void updateLogFilter(String deviceSerial, String filterText) {
        LogStreamSession session = logStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> params = new HashMap<>();
            params.put("filterText", filterText != null ? filterText : "");
            sendControlMessage(session.webSocket, "filter", params);
        }
    }

    /**
     * Get process map from remote device (placeholder - actual implementation would need REST API endpoint)
     * For now, this is handled via WebSocket processMap messages
     */
    public Map<String, String> getProcessMap(String deviceSerial) {
        // Process map is received via WebSocket messages and forwarded to listener
        // This method is a placeholder for API compatibility
        log.debug("getProcessMap: {} (handled via WebSocket)", deviceSerial);
        return new HashMap<>();
    }

    // Private helper methods

    private String buildWebSocketUrl(String deviceSerial, String filterText) {
        // Convert http/https URL to ws/wss
        String baseUrl = serverConfig.getUrl();
        String wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://");

        // Build query parameters
        StringBuilder url = new StringBuilder(wsUrl);
        url.append(RemoteHttpServer.WS_LOGS);
        url.append("?token=").append(URLEncoder.encode(serverConfig.authToken, StandardCharsets.UTF_8));
        url.append("&serial=").append(URLEncoder.encode(deviceSerial, StandardCharsets.UTF_8));

        if (filterText != null && !filterText.isEmpty()) {
            url.append("&filter=").append(URLEncoder.encode(filterText, StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    private void handleWebSocketMessage(String message, LogStreamSession session) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = GsonHelper.fromJson(message, Map.class);
            String type = (String) msg.get("type");

            if (type == null) {
                log.warn("Received message without type field");
                return;
            }

            switch (type) {
                case "connected":
                    log.debug("Connected to log stream for device: {}", session.deviceSerial);
                    break;

                case "logs":
                    handleLogsMessage(msg, session);
                    break;

                case "processMap":
                    handleProcessMapMessage(msg, session);
                    break;

                case "status":
                    String state = (String) msg.get("state");
                    log.debug("Status update for device {}: {}", session.deviceSerial, state);
                    break;

                case "warning":
                    String warning = (String) msg.get("message");
                    log.warn("Warning from server for device {}: {}", session.deviceSerial, warning);
                    break;

                case "error":
                    String error = (String) msg.get("message");
                    log.error("Error from server for device {}: {}", session.deviceSerial, error);
                    break;

                default:
                    log.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
        }
    }

    private void handleLogsMessage(Map<String, Object> msg, LogStreamSession session) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entriesData = (List<Map<String, Object>>) msg.get("entries");

        if (entriesData == null || entriesData.isEmpty()) {
            return;
        }

        List<LogEntry> logEntries = new ArrayList<>();
        for (Map<String, Object> entryData : entriesData) {
            LogEntry entry = parseLogEntry(entryData);
            if (entry != null) {
                logEntries.add(entry);
            }
        }

        if (!logEntries.isEmpty() && session.listener != null) {
            session.listener.handleLogEntries(logEntries);
        }
    }

    private void handleProcessMapMessage(Map<String, Object> msg, LogStreamSession session) {
        @SuppressWarnings("unchecked")
        Map<String, String> processMap = (Map<String, String>) msg.get("map");

        if (processMap != null && session.listener != null) {
            session.listener.handleProcessMap(processMap);
        }
    }

    private LogEntry parseLogEntry(Map<String, Object> data) {
        try {
            // Create a LogEntry from the JSON data
            // The LogEntry constructor expects a formatted line, so we'll reconstruct it
            StringBuilder line = new StringBuilder();

            String date = (String) data.get("date");
            String pid = getStringValue(data.get("pid"));
            String tid = getStringValue(data.get("tid"));
            String level = (String) data.get("level");
            String tag = (String) data.get("tag");
            String message = (String) data.get("message");

            // Reconstruct logcat format: MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: MESSAGE
            if (date != null) line.append(date).append("  ");
            if (pid != null) line.append(pid).append("  ");
            if (tid != null) line.append(tid).append(" ");
            if (level != null) line.append(level).append(" ");
            if (tag != null) line.append(tag).append(": ");
            if (message != null) line.append(message);

            // Get ID (as Double from JSON, convert to long)
            Object idObj = data.get("id");
            long id = 0;
            if (idObj instanceof Number) {
                id = ((Number) idObj).longValue();
            }

            LogEntry entry = new LogEntry(line.toString(), id);

            // Set app if provided
            String app = (String) data.get("app");
            if (app != null) {
                entry.app = app;
            }

            return entry;
        } catch (Exception e) {
            log.error("Error parsing log entry", e);
            return null;
        }
    }

    private String getStringValue(Object value) {
        if (value == null) return null;
        return value.toString();
    }

    private void sendControlMessage(WebSocket webSocket, String action, Map<String, Object> additionalParams) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("action", action);
            if (additionalParams != null) {
                message.putAll(additionalParams);
            }
            String json = GsonHelper.toJson(message);
            webSocket.sendText(json, true);
            log.debug("Sent control message: {}", json);
        } catch (Exception e) {
            log.error("Error sending control message", e);
        }
    }

}


