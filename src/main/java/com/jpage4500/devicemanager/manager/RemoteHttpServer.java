package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import fi.iki.elonen.NanoWSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * HTTP server that handles remote client requests
 */
public class RemoteHttpServer extends NanoWSD {
    private static final Logger log = LoggerFactory.getLogger(RemoteHttpServer.class);

    public static final String HEADER_IP = "x-client-ip";
    public static final String HEADER_NAME = "x-client-name";
    public static final String HEADER_AUTHORIZATION = "authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String MIME_JSON = "application/json";

    public static final String API_INFO = "/api/info";
    public static final String API_DEVICES = "/api/devices";
    public static final String API_DEVICE_PROPERTIES = "/api/properties";
    public static final String API_EXECUTE = "/api/execute";
    public static final String API_FILES_LIST = "/api/files/list";
    public static final String API_FILES_DOWNLOAD = "/api/files/download";
    public static final String API_FILES_UPLOAD = "/api/files/upload";
    public static final String API_SCREENSHOT = "/api/screenshot";
    public static final String WS_LOGS = "/ws/logs";

    private final String authToken;
    private final RemoteServerManager serverManager;
    private final DeviceManager deviceManager;

    public RemoteHttpServer(int port, String authToken, RemoteServerManager serverManager) {
        super(port);
        this.authToken = authToken;
        this.serverManager = serverManager;
        this.deviceManager = DeviceManager.getInstance();
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        String uri = handshake.getUri();
        Map<String, String> headers = handshake.getHeaders();
        Map<String, String> params = handshake.getParms();

        log.debug("WebSocket upgrade request: {}", uri);

        // Authenticate via query parameter or header
        if (!authenticateClient(params, headers)) {
            log.warn("Unauthorized WebSocket connection attempt");
            return null;
        }

        // Handle log streaming WebSocket
        if (uri.equals(WS_LOGS)) {
            return handleLogStreamWebSocket(handshake, params);
        }

        log.warn("Unknown WebSocket endpoint: {}", uri);
        return null;
    }

    /**
     * authenticate the client using token from params or headers
     *
     * @return true if authenticated, false if not
     */
    private boolean authenticateClient(Map<String, String> params, Map<String, String> headers) {
        String token = null;
        if (params != null) {
            token = params.get("token");
        }
        if (token == null) {
            token = headers.get(HEADER_AUTHORIZATION);
            if (TextUtils.startsWith(token, BEARER_PREFIX)) {
                token = token.substring(BEARER_PREFIX.length());
            }
        }

        return TextUtils.equals(token, authToken);
    }

    private WebSocket handleLogStreamWebSocket(IHTTPSession handshake, Map<String, String> params) {
        String serial = params.get("serial");
        if (serial == null) {
            log.warn("Missing serial parameter for WebSocket log stream");
            return null;
        }

        Device device = deviceManager.getDevice(serial);
        if (device == null || device.remoteConnection != null) {
            log.warn("Device not found or is remote: {}", serial);
            return null;
        }

        // Parse optional filter parameter
        String filterText = params.get("filter");
        LogFilter filter = null;
        if (filterText != null && !filterText.isEmpty()) {
            filter = LogFilter.parse(filterText);
            log.debug("Applying filter to log stream: {}", filterText);
        }

        log.info("Opening WebSocket log stream for device: {}", serial);
        return new LogStreamWebSocket(handshake, device, filter);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> headers = session.getHeaders();
        Map<String, String> params = session.getParms();
        // client provided IP and name
        String headerIp = safeHeader(headers.get(HEADER_IP));
        String headerName = safeHeader(headers.get(HEADER_NAME));
        // server provided IP address
        String clientIp = headers.get("remote-addr");
        if (TextUtils.isEmpty(clientIp)) {
            clientIp = headers.get("http-client-ip");
            if (TextUtils.isEmpty(clientIp)) {
                clientIp = headerIp;
                if (TextUtils.isEmpty(clientIp)) {
                    clientIp = "unknown";
                }
            }
        }

        log.trace("server: {} {}, ip:{}, name:{}, clientIP:{}", method, uri, clientIp, headerName, headerIp);

        // Authenticate
        if (!authenticateClient(params, headers)) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized");
        }

        // Track client (with optional name)
        serverManager.trackClient(clientIp, headerName);

        // Route request
        try {
            if (uri.equals(API_INFO)) {
                return handleServerInfo(session);
            } else if (uri.equals(API_DEVICES)) {
                return handleGetDevices(session);
            } else if (uri.equals(API_DEVICE_PROPERTIES)) {
                return handleGetProperties(session);
            } else if (uri.equals(API_EXECUTE)) {
                return handleExecuteCommand(session);
            } else if (uri.startsWith(API_FILES_LIST)) {
                return handleListFiles(session);
            } else if (uri.startsWith(API_FILES_DOWNLOAD)) {
                return handleDownloadFile(session);
            } else if (uri.startsWith(API_FILES_UPLOAD)) {
                return handleUploadFile(session);
            } else if (uri.startsWith(API_SCREENSHOT)) {
                return handleScreenshot(session);
            } else {
                return createNotFoundResponse("Not found");
            }
        } catch (Exception e) {
            log.error("Error handling request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Error: " + e.getMessage());
        }
    }

    private Response handleGetProperties(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        Device device = getDeviceParam(params);
        if (device == null) {
            return createNotFoundResponse("Device not found");
        }
        Map<String, String> map = deviceManager.fetchDevicePropertiesInternal(device);
        if (map != null) {
            return createJsonResponse(map);
        } else {
            return createNotFoundResponse("Error: ");
        }
    }

    public static class ServerInfo {
        public String version;
        public int deviceCount;
        public String serverName;
    }

    /**
     * GET /api/info - Server information
     * - returns JSON of ServerInfo
     */
    private Response handleServerInfo(IHTTPSession session) {
        ServerInfo info = new ServerInfo();
        info.version = "1.0";
        info.deviceCount = deviceManager.getDevices().size();
        info.serverName = "Android Device Manager";

        return createJsonResponse(info);
    }

    /**
     * GET /api/devices - Return list of local devices
     * - returns JSON of List<Device>
     */
    private Response handleGetDevices(IHTTPSession session) {
        List<Device> devices = deviceManager.getDevices();
        // remove any remote devices
        devices.removeIf(device -> device.remoteConnection != null);

        return createJsonResponse(devices);
    }

    /**
     * POST /api/execute - Execute ADB command on device
     * Body: {"serial": "xxx", "command": "shell ls"}
     */
    private Response handleExecuteCommand(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String body = files.get("postData");

        if (body == null || body.isEmpty()) {
            return createBadResponse("Missing request body");
        }

        @SuppressWarnings("unchecked")
        Map<String, String> request = GsonHelper.fromJson(body, Map.class);
        String serial = request.get("serial");
        String command = request.get("command");

        if (serial == null || command == null) {
            return createBadResponse("Missing serial or command");
        }

        Device device = deviceManager.getDevice(serial);
        if (device == null || device.remoteConnection != null) {
            return createNotFoundResponse("Device not found");
        }

        // Execute command
        DeviceManager.ShellResult shellResult = deviceManager.runShell(device, command);
        return createJsonResponse(shellResult);
    }

    /**
     * GET /api/files/list?serial=xxx&path=/sdcard
     */
    private Response handleListFiles(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        Device device = getDeviceParam(params);
        if (device == null) {
            return createNotFoundResponse("Device not found");
        }
        String path = params.get("path");

        DeviceManager.FileResponse fileResponse = deviceManager.fetchFileListInternal(device, path, false);
        if (fileResponse.fileList != null) {
            return createJsonResponse(fileResponse.fileList);
        } else {
            return createNotFoundResponse("Error: " + fileResponse.error);
        }
    }

    private Device getDeviceParam(Map<String, String> params) {
        String serial = params.get("serial");
        return deviceManager.getDevice(serial);
    }

    /**
     * GET /api/files/download?serial=xxx&path=/sdcard&file=test.txt
     * Downloads a file from the device
     */
    private Response handleDownloadFile(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        Device device = getDeviceParam(params);
        if (device == null) {
            return createNotFoundResponse("Device not found");
        }
        String path = params.get("path");
        String filename = params.get("file");

        if (path == null || filename == null) {
            return createBadResponse(
                "Missing serial, path, or file parameter");
        }

        try {
            // Create a temporary file to download to
            File tempFile = File.createTempFile("device_download_", "_" + filename);
            tempFile.deleteOnExit();

            DeviceFile deviceFile = new DeviceFile();
            // directories not supported - files only
            deviceFile.isDirectory = false;
            deviceFile.name = filename;

            boolean isOk = deviceManager.downloadFileInternal(device, path, deviceFile, tempFile);
            if (!isOk || !tempFile.exists() || tempFile.length() == 0) {
                return createNotFoundResponse("File not found on device");
            }

            // Determine MIME type
            String mimeType = getMimeType(filename);

            // Stream the file to the client
            FileInputStream fis = new FileInputStream(tempFile);
            Response response = newChunkedResponse(Response.Status.OK, mimeType, fis);
            response.addHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.addHeader("Content-Length", String.valueOf(tempFile.length()));

            return response;
        } catch (Exception e) {
            log.error("Failed to download file", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Error downloading file: " + e.getMessage());
        }
    }

    /**
     * POST /api/files/upload?serial=xxx&path=/sdcard&file=test.txt
     * Uploads a file to the device
     * Body: raw file data
     */
    private Response handleUploadFile(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        Device device = getDeviceParam(params);
        if (device == null) {
            return createNotFoundResponse("Device not found");
        }

        String path = params.get("path");
        String filename = params.get("file");

        if (path == null || filename == null) {
            return createBadResponse(
                "Missing serial, path, or file parameter");
        }

        try {
            // Create a temporary file to receive the upload
            File tempFile = File.createTempFile("device_upload_", "_" + filename);
            tempFile.deleteOnExit();

            // Parse the body and save to temp file
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            // The uploaded file data is in the postData
            String postData = files.get("postData");
            if (postData != null && !postData.isEmpty()) {
                // Write the data to temp file
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(postData.getBytes(StandardCharsets.ISO_8859_1));
                }
            } else {
                // Try to get the uploaded file from the files map
                String tmpFilePath = files.get("file");
                if (tmpFilePath != null) {
                    File uploadedFile = new File(tmpFilePath);
                    if (uploadedFile.exists()) {
                        // Copy to our temp file
                        Files.copy(uploadedFile.toPath(), tempFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0) {
                return createBadResponse(
                    "No file data received");
            }

            // Use jadb to push the file to the device
            se.vidstige.jadb.RemoteFile remoteFile = new se.vidstige.jadb.RemoteFileRecord(path, filename, 0, 0, 0);
            device.jadbDevice.push(tempFile, remoteFile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded successfully");
            response.put("path", path + "/" + filename);

            return createJsonResponse(response);
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error uploading file: " + e.getMessage());
            String json = GsonHelper.toJson(response);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_JSON, json);
        }
    }

    /**
     * Determine MIME type from filename
     */
    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return MIME_JSON;
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    /**
     * GET /api/screenshot?serial=xxx
     */
    private Response handleScreenshot(IHTTPSession session) {
        // TODO: Implement screenshot capture
        return newFixedLengthResponse(Response.Status.NOT_IMPLEMENTED, MIME_PLAINTEXT,
            "Screenshot feature not yet implemented");
    }

    private String safeHeader(String value) {
        if (value == null) return null;
        value = value.trim();
        if (value.isEmpty()) return null;
        // Basic sanitization: limit length and strip control chars
        value = value.replaceAll("[\r\n]", "");
        if (value.length() > 128) value = value.substring(0, 128);
        return value;
    }

    private Response createJsonResponse(Object object) {
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, GsonHelper.toJson(object));
    }

    private Response createBadResponse(String errorMessage) {
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, errorMessage);
    }

    private Response createNotFoundResponse(String errorMessage) {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, errorMessage);
    }

}

