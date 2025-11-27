package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.NetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
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

    // webSocket log streaming
    private final Map<String, LogStreamSession> logStreamSessions = new ConcurrentHashMap<>();
    // webSocket screen streaming
    private final Map<String, ScreenStreamSession> screenStreamSessions = new ConcurrentHashMap<>();
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
        final WebSocket webSocket;
        final DeviceManager.DeviceLogListener listener;
        final String deviceSerial;
        final StringBuilder messageBuffer = new StringBuilder();

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
        //log.trace("fetchDevices: response: {}", GsonHelper.toJson(response));
        if (response.status == 200) {
            List<Device> deviceList = GsonHelper.stringToList(response.body, Device.class);
            log.trace("fetchDevices: got: {} remote devices", deviceList.size());
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
        log.trace("fetchFileList: response: {}", GsonHelper.toJson(response));
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
        log.trace("fetchDeviceProperties: response: {}", GsonHelper.toJson(response));
        if (response.status == 200) {
            return GsonHelper.stringToMap(response.body, String.class, String.class);
        } else {
            return null;
        }
    }

    public boolean setProperty(String deviceSerial, String key, String value) {
        String url = serverConfig.getUrl() + RemoteHttpServer.API_DEVICE_SET_PROPERTY;
        Map<String, String> headers = getDefaultHeaders();
        // required for JSON payload
        headers.put("Content-Type", "application/json");

        Map<String, String> request = new HashMap<>();
        request.put("serial", deviceSerial);
        request.put("key", key);
        request.put("value", value);

        NetworkHelper.HttpResponse response = networkHelper.postRequest(url, GsonHelper.toJson(request), headers);
        return response.status == 200;
    }

    /**
     * capture screenshot
     */
    public BufferedImage fetchScreenshot(String deviceSerial) {
        String url = serverConfig.getUrl() + RemoteHttpServer.API_SCREENSHOT + "?serial=" + deviceSerial;
        Map<String, String> headers = getDefaultHeaders();
        NetworkHelper.HttpDataResponse response = networkHelper.download(url, headers);
        if (response.status == 200 && response.data != null) {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(response.data)) {
                return ImageIO.read(bais);
            } catch (Exception e) {
                log.error("fetchScreenshot: error decoding image", e);
            }
        } else {
            log.warn("fetchScreenshot: failed, status: {}", response.status);
        }
        return null;
    }

    private Map<String, String> getDefaultHeaders() {
        Map<String, String> headers = new HashMap<>();
        // all requests require authorization
        headers.put(RemoteHttpServer.HEADER_AUTHORIZATION, "Bearer " + serverConfig.authToken);
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "Android Device Manager");
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
        Map<String, String> headers = getDefaultHeaders();
        // required for JSON payload
        headers.put("Content-Type", "application/json");
        String url = serverConfig.getUrl() + RemoteHttpServer.API_EXECUTE;

        Map<String, String> request = new HashMap<>();
        request.put("serial", deviceSerial);
        request.put("command", command);

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
        NetworkHelper.HttpResponse response = networkHelper.downloadFile(url, saveFile, headers);
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

    public DeviceManager.Result installApp(List<String> serialList, File localFile) {
        String url = serverConfig.getUrl() + RemoteHttpServer.API_INSTALL;
        // support multiple devices
        for (int i = 0; i < serialList.size(); i++) {
            String serial = serialList.get(i);
            if (i == 0) url += "?";
            else url += "&";
            url += "serial=" + serial;
        }

        Map<String, String> headers = getDefaultHeaders();
        NetworkHelper.HttpResponse response = networkHelper.upload(url, localFile, headers);
        return new DeviceManager.Result(response.status == 200, response.body);
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

    public String getName() {
        return serverConfig.name;
    }

    public String toString() {
        return GsonHelper.toJson(serverConfig);
    }

    /**
     * Start streaming logs from remote device via WebSocket
     *
     * @param deviceSerial Device serial number
     * @param lastLogTime  Optional last log time to resume from
     * @param filterText   Optional filter expression (e.g., "level:E && tag:*MyTag*")
     * @param listener     Listener to receive log entries
     */
    public void startLogging(String deviceSerial, String lastLogTime, String filterText, DeviceManager.DeviceLogListener listener) {
        // stop any existing session
        stopLogging(deviceSerial);

        log.debug("startLogging: serial: {}, filter: {}", deviceSerial, filterText);

        // build WebSocket URL
        String wsUrl = buildWebSocketUrl(deviceSerial, filterText);

        // create WebSocket listener
        WebSocket.Listener wsListener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                WebSocket.Listener.super.onOpen(webSocket);
                log.info("onOpen: device: {}", deviceSerial);
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                LogStreamSession session = logStreamSessions.get(deviceSerial);
                if (session != null) {
                    synchronized (session.messageBuffer) {
                        // accumulate message parts
                        session.messageBuffer.append(data);
                    }

                    if (last) {
                        String message;
                        synchronized (session.messageBuffer) {
                            // complete message received
                            message = session.messageBuffer.toString();
                            session.messageBuffer.setLength(0);
                        }

                        handleWebSocketMessage(message, session);
                    }
                }
                webSocket.request(1);
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                log.info("onClose: device: {}, code: {}, reason: {}", deviceSerial, statusCode, reason);
                logStreamSessions.remove(deviceSerial);
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.error("onError: device: {}, {}", deviceSerial, error.getMessage());
                logStreamSessions.remove(deviceSerial);
                WebSocket.Listener.super.onError(webSocket, error);
            }
        };

        // connect WebSocket
        try {
            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), wsListener);

            wsFuture.whenComplete((ws, throwable) -> {
                if (throwable != null) {
                    log.error("startLogging: device: {} failed", deviceSerial, throwable);
                } else {
                    // store session
                    LogStreamSession session = new LogStreamSession(ws, listener, deviceSerial);
                    logStreamSessions.put(deviceSerial, session);
                    log.debug("startLogging: device: {} session created", deviceSerial);
                }
            });
        } catch (Exception e) {
            log.error("startLogging: device: {} error", deviceSerial, e);
        }
    }

    /**
     * Stop streaming logs from remote device
     */
    public void stopLogging(String deviceSerial) {
        LogStreamSession session = logStreamSessions.remove(deviceSerial);
        if (session != null && session.webSocket != null) {
            log.debug("stopLogging: device: {}", deviceSerial);
            session.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing")
                .whenComplete((ws, throwable) -> {
                    if (throwable != null) {
                        log.warn("stopLogging: device: {} error closing", deviceSerial, throwable);
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
            sendControlMessage(session.webSocket, LogStreamWebSocket.ACTION_PAUSE, null);
        }
    }

    /**
     * Resume log streaming (sends resume control message)
     */
    public void resumeLogging(String deviceSerial) {
        LogStreamSession session = logStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            sendControlMessage(session.webSocket, LogStreamWebSocket.ACTION_RESUME, null);
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
            sendControlMessage(session.webSocket, LogStreamWebSocket.ACTION_FILTER, params);
        }
    }

    /**
     * Get process map from remote device (placeholder - actual implementation would need REST API endpoint)
     * For now, this is handled via WebSocket processMap messages
     */
    public Map<String, String> getProcessMap(String deviceSerial) {
        // process map is received via WebSocket messages and forwarded to listener
        // this method is a placeholder for API compatibility
        log.debug("getProcessMap: device: {}", deviceSerial);
        return new HashMap<>();
    }

    // private helper methods

    private String buildWebSocketUrl(String deviceSerial, String filterText) {
        String baseUrl = serverConfig.getWebsocketUrl();
        // build query parameters
        StringBuilder url = new StringBuilder(baseUrl);
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

            if (msg == null) {
                // log first and last 100 chars to help debug truncation
                String preview = message.length() > 200 ?
                    message.substring(0, 100) + "..." + message.substring(message.length() - 100) :
                    message;
                log.error("handleWebSocketMessage: device: {} failed to parse message, length: {}, preview: {}",
                    session.deviceSerial, message.length(), preview);
                return;
            }

            String type = (String) msg.get("type");

            if (type == null) {
                log.warn("handleWebSocketMessage: device: {} missing type", session.deviceSerial);
                return;
            }

            switch (type) {
                case LogStreamWebSocket.TYPE_CONNECTED -> {
                    log.debug("handleWebSocketMessage: device: {} connected", session.deviceSerial);
                }
                case LogStreamWebSocket.TYPE_LOGS -> handleLogsMessage(msg, session);
                case LogStreamWebSocket.TYPE_PROCESS_MAP -> handleProcessMapMessage(msg, session);
                case LogStreamWebSocket.TYPE_STATUS -> {
                    String state = (String) msg.get("state");
                    log.debug("handleWebSocketMessage: device: {} status: {}", session.deviceSerial, state);
                }
                case LogStreamWebSocket.TYPE_WARNING -> {
                    String warning = (String) msg.get("message");
                    log.warn("handleWebSocketMessage: device: {} warning: {}", session.deviceSerial, warning);
                }
                case LogStreamWebSocket.TYPE_ERROR -> {
                    String error = (String) msg.get("message");
                    log.error("handleWebSocketMessage: device: {} error: {}", session.deviceSerial, error);
                }
                default -> log.debug("handleWebSocketMessage: device: {} unknown type: {}", session.deviceSerial, type);
            }
        } catch (Exception e) {
            log.error("handleWebSocketMessage: device: {}", session.deviceSerial, e);
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
            // create a LogEntry from the JSON data
            // the LogEntry constructor expects a formatted line, so we'll reconstruct it
            StringBuilder line = new StringBuilder();

            String date = (String) data.get("date");
            String pid = getStringValue(data.get("pid"));
            String tid = getStringValue(data.get("tid"));
            String level = (String) data.get("level");
            String tag = (String) data.get("tag");
            String message = (String) data.get("message");

            // reconstruct logcat format: MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: MESSAGE
            if (date != null) line.append(date).append("  ");
            if (pid != null) line.append(pid).append("  ");
            if (tid != null) line.append(tid).append(" ");
            if (level != null) line.append(level).append(" ");
            if (tag != null) line.append(tag).append(": ");
            if (message != null) line.append(message);

            // get ID (as Double from JSON, convert to long)
            Object idObj = data.get("id");
            long id = 0;
            if (idObj instanceof Number) {
                id = ((Number) idObj).longValue();
            }

            LogEntry entry = new LogEntry(line.toString(), id);

            // set app if provided
            String app = (String) data.get("app");
            if (app != null) {
                entry.app = app;
            }

            return entry;
        } catch (Exception e) {
            log.error("parseLogEntry: error", e);
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
            log.debug("sendControlMessage: action: {}", action);
        } catch (Exception e) {
            log.error("sendControlMessage: error", e);
        }
    }

    // ========================================================================
    // screen Streaming
    // ========================================================================

    /**
     * Listener for screen stream frames
     */
    public interface ScreenStreamListener {
        void onFrame(java.awt.image.BufferedImage image, int width, int height);

        void onStatus(String status, String message);

        void onError(String error);

        void onClosed();
    }

    /**
     * Represents an active screen streaming session
     */
    private static class ScreenStreamSession {
        WebSocket webSocket;
        ScreenStreamListener listener;
        String deviceSerial;
        ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
        int expectedHeaderLength = -1;
        byte[] currentHeader = null;
        int currentImageSize = 0;

        ScreenStreamSession(WebSocket ws, ScreenStreamListener listener, String serial) {
            this.webSocket = ws;
            this.listener = listener;
            this.deviceSerial = serial;
        }
    }

    /**
     * Start streaming screen from remote device
     */
    public void startScreenStream(String deviceSerial, int intervalMs, boolean useCompression, ScreenStreamListener listener) {
        // stop any existing session
        stopScreenStream(deviceSerial);

        log.debug("startScreenStream: serial: {}, intervalMs: {}, compress: {}", deviceSerial, intervalMs, useCompression);

        // build WebSocket URL
        String wsUrl = buildScreenStreamUrl(deviceSerial, intervalMs, useCompression);

        // create WebSocket listener
        WebSocket.Listener wsListener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                WebSocket.Listener.super.onOpen(webSocket);
                log.info("onOpen: screen stream device: {}", deviceSerial);
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
                if (session != null) {
                    try {
                        handleScreenStreamBinary(data, last, session);
                    } catch (Exception e) {
                        log.error("onBinary: error handling data", e);
                    }
                }
                return WebSocket.Listener.super.onBinary(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
                if (session != null && last) {
                    handleScreenStreamText(data.toString(), session);
                }
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                log.info("onClose: screen stream device: {}, code: {}, reason: {}", deviceSerial, statusCode, reason);
                ScreenStreamSession session = screenStreamSessions.remove(deviceSerial);
                if (session != null && session.listener != null) {
                    session.listener.onClosed();
                }
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                log.error("onError: screen stream device: {}", deviceSerial, error);
                ScreenStreamSession session = screenStreamSessions.remove(deviceSerial);
                if (session != null && session.listener != null) {
                    session.listener.onError("WebSocket error: " + error.getMessage());
                }
                WebSocket.Listener.super.onError(webSocket, error);
            }
        };

        // connect WebSocket
        try {
            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), wsListener);

            wsFuture.whenComplete((ws, throwable) -> {
                if (throwable != null) {
                    log.error("startScreenStream: device: {} failed", deviceSerial, throwable);
                    listener.onError("Failed to connect: " + throwable.getMessage());
                } else {
                    // store session
                    ScreenStreamSession session = new ScreenStreamSession(ws, listener, deviceSerial);
                    screenStreamSessions.put(deviceSerial, session);
                    log.debug("startScreenStream: device: {} session created", deviceSerial);
                }
            });
        } catch (Exception e) {
            log.error("startScreenStream: device: {} error", deviceSerial, e);
            listener.onError("Failed to start: " + e.getMessage());
        }
    }

    /**
     * Stop streaming screen from remote device
     */
    public void stopScreenStream(String deviceSerial) {
        ScreenStreamSession session = screenStreamSessions.remove(deviceSerial);
        if (session != null && session.webSocket != null) {
            log.debug("stopScreenStream: device: {}", deviceSerial);
            session.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing")
                .whenComplete((ws, throwable) -> {
                    if (throwable != null) {
                        log.warn("stopScreenStream: device: {} error closing", deviceSerial, throwable);
                    }
                });
        }
    }

    /**
     * Send tap input to remote device
     */
    public void sendScreenInputTap(String deviceSerial, int x, int y) {
        ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", ScreenStreamWebSocket.ACTION_INPUT);
            message.put("type", ScreenStreamWebSocket.INPUT_TAP);
            message.put("x", x);
            message.put("y", y);
            sendScreenControlMessage(session.webSocket, message);
        }
    }

    /**
     * Send swipe input to remote device
     */
    public void sendScreenInputSwipe(String deviceSerial, int x1, int y1, int x2, int y2, int duration) {
        ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", ScreenStreamWebSocket.ACTION_INPUT);
            message.put("type", ScreenStreamWebSocket.INPUT_SWIPE);
            message.put("x1", x1);
            message.put("y1", y1);
            message.put("x2", x2);
            message.put("y2", y2);
            message.put("duration", duration);
            sendScreenControlMessage(session.webSocket, message);
        }
    }

    /**
     * Send text input to remote device
     */
    public void sendScreenInputText(String deviceSerial, String text) {
        ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", ScreenStreamWebSocket.ACTION_INPUT);
            message.put("type", ScreenStreamWebSocket.INPUT_TEXT);
            message.put("text", text);
            sendScreenControlMessage(session.webSocket, message);
        }
    }

    /**
     * Send keyevent to remote device
     */
    public void sendScreenInputKeyEvent(String deviceSerial, int keycode) {
        ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", ScreenStreamWebSocket.ACTION_INPUT);
            message.put("type", ScreenStreamWebSocket.INPUT_KEYEVENT);
            message.put("keycode", keycode);
            sendScreenControlMessage(session.webSocket, message);
        }
    }

    /**
     * Set screen stream interval
     */
    public void setScreenStreamInterval(String deviceSerial, int intervalMs) {
        ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", ScreenStreamWebSocket.ACTION_SET_INTERVAL);
            message.put("intervalMs", intervalMs);
            sendScreenControlMessage(session.webSocket, message);
        }
    }

    /**
     * Set compression mode for screen stream
     */
    public void setScreenStreamCompression(String deviceSerial, boolean useCompression) {
        ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", ScreenStreamWebSocket.ACTION_SET_COMPRESSION);
            message.put("useCompression", useCompression);
            sendScreenControlMessage(session.webSocket, message);
        }
    }

    /**
     * Set quality level for screen stream
     */
    public void setScreenStreamQuality(String deviceSerial, String quality) {
        ScreenStreamSession session = screenStreamSessions.get(deviceSerial);
        if (session != null && session.webSocket != null) {
            Map<String, Object> message = new HashMap<>();
            message.put("action", ScreenStreamWebSocket.ACTION_SET_QUALITY);
            message.put("quality", quality);
            sendScreenControlMessage(session.webSocket, message);
        }
    }

    private String buildScreenStreamUrl(String deviceSerial, int intervalMs, boolean useCompression) {
        String baseUrl = serverConfig.getWebsocketUrl();
        StringBuilder url = new StringBuilder(baseUrl);
        url.append(RemoteHttpServer.WS_SCREEN);
        url.append("?token=").append(URLEncoder.encode(serverConfig.authToken, StandardCharsets.UTF_8));
        url.append("&serial=").append(URLEncoder.encode(deviceSerial, StandardCharsets.UTF_8));
        url.append("&intervalMs=").append(intervalMs);
        url.append("&compress=").append(useCompression);

        return url.toString();
    }

    private void handleScreenStreamBinary(ByteBuffer data, boolean last, ScreenStreamSession session) {
        try {
            // append data to buffer
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            session.dataBuffer.write(bytes);

            if (!last) {
                return; // Wait for complete message
            }

            // complete binary message received
            byte[] fullData = session.dataBuffer.toByteArray();
            session.dataBuffer.reset();

            if (fullData.length < 4) {
                log.warn("handleScreenStreamBinary: insufficient data");
                return;
            }

            // parse frame: 4-byte header length + header JSON + PNG image
            ByteBuffer buffer = ByteBuffer.wrap(fullData);
            int headerLength = buffer.getInt();

            if (fullData.length < 4 + headerLength) {
                log.warn("handleScreenStreamBinary: incomplete header");
                return;
            }

            byte[] headerBytes = new byte[headerLength];
            buffer.get(headerBytes);
            String headerJson = new String(headerBytes, StandardCharsets.UTF_8);

            @SuppressWarnings("unchecked")
            Map<String, Object> header = GsonHelper.fromJson(headerJson, Map.class);
            if (header == null) {
                log.warn("handleScreenStreamBinary: invalid header JSON");
                return;
            }

            int imageSize = buffer.remaining();
            byte[] imageBytes = new byte[imageSize];
            buffer.get(imageBytes);

            // get format from header ("png" or "jpeg")
            String format = (String) header.get("format");
            if (format == null) format = "png"; // default fallback

            // decode image bytes (ImageIO supports both PNG and JPEG)
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);

            if (image != null) {
                int width = ((Number) header.get("width")).intValue();
                int height = ((Number) header.get("height")).intValue();
                // if (log.isTraceEnabled()) log.trace("handleScreenStreamBinary: decoded frame format={}, size={}KB, w={}, h={}", format, imageBytes.length / 1024, width, height);
                session.listener.onFrame(image, width, height);
            } else {
                log.warn("handleScreenStreamBinary: failed to decode image, format={}, bytes={}", format, imageBytes.length);
            }

        } catch (Exception e) {
            log.error("handleScreenStreamBinary: error", e);
            session.listener.onError("Frame decode error: " + e.getMessage());
        }
    }

    private void handleScreenStreamText(String message, ScreenStreamSession session) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = GsonHelper.fromJson(message, Map.class);
            if (msg == null) return;

            String type = (String) msg.get("type");
            if ("status".equals(type)) {
                String status = (String) msg.get("status");
                String statusMsg = (String) msg.get("message");
                session.listener.onStatus(status, statusMsg);
            } else if ("error".equals(type)) {
                String error = (String) msg.get("error");
                session.listener.onError(error);
            }
        } catch (Exception e) {
            log.error("handleScreenStreamText: error", e);
        }
    }

    private void sendScreenControlMessage(WebSocket webSocket, Map<String, Object> message) {
        try {
            String json = GsonHelper.toJson(message);
            webSocket.sendText(json, true);
            if (log.isTraceEnabled()) {
                log.trace("sendScreenControlMessage: {}", json);
            }
        } catch (Exception e) {
            log.error("sendScreenControlMessage: error", e);
        }
    }

}
