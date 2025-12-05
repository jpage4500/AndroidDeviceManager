package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.utils.*;
import fi.iki.elonen.NanoWSD;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP server that handles remote client requests
 */
public class RemoteHttpServer extends NanoWSD {
    private static final Logger log = LoggerFactory.getLogger(RemoteHttpServer.class);

    public static final String VERSION = "1.0";

    public static final String HEADER_IP = "x-client-ip";
    public static final String HEADER_NAME = "x-client-name";
    public static final String HEADER_AUTHORIZATION = "authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String MIME_JSON = "application/json";

    public static final String API_INFO = "/api/info";
    public static final String API_DEVICES = "/api/devices";
    public static final String API_DEVICE_PROPERTIES = "/api/properties";
    public static final String API_DEVICE_SET_PROPERTY = "/api/setproperty";
    public static final String API_EXECUTE = "/api/execute";
    public static final String API_INSTALL = "/api/install";
    public static final String API_FILES_LIST = "/api/files/list";
    public static final String API_FILES_DOWNLOAD = "/api/files/download";
    public static final String API_FILES_UPLOAD = "/api/files/upload";
    public static final String API_SCREENSHOT = "/api/screenshot";
    public static final String WS_LOGS = "/ws/logs";
    public static final String WS_SCREEN = "/ws/screen";

    private final String authToken;
    private final RemoteServerManager serverManager;

    public RemoteHttpServer(int port, String authToken, RemoteServerManager serverManager) {
        super(port);
        this.authToken = authToken;
        this.serverManager = serverManager;
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

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> headers = session.getHeaders();
        Map<String, String> params = session.getParms();

        // check if this is a WebSocket upgrade request
        String upgradeHeader = headers.get("upgrade");
        if ("websocket".equalsIgnoreCase(upgradeHeader)) {
            log.debug("serve: WebSocket upgrade request detected: {}", uri);
            // let the parent class handle WebSocket upgrade which will call openWebSocket()
            return super.serve(session);
        }

        // regular HTTP request handling
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

        if (log.isTraceEnabled()) {
            log.trace("serve: {} {}, {} {}", method, uri, TextUtils.firstValid(headerName, clientIp), params.isEmpty() ? "" : GsonHelper.toJson(params));
        }

        // authenticate
        if (!authenticateClient(params, headers)) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized");
        }

        // track client (with optional name)
        serverManager.trackClient(clientIp, headerName);

        // route request
        try {
            if (uri.equals(API_INFO)) {
                return handleServerInfo(session);
            } else if (uri.equals(API_DEVICES)) {
                return handleGetDevices(session);
            } else if (uri.equals(API_DEVICE_PROPERTIES)) {
                return handleGetProperties(session);
            } else if (uri.equals(API_DEVICE_SET_PROPERTY)) {
                return handleSetProperty(session);
            } else if (uri.equals(API_EXECUTE)) {
                return handleExecuteCommand(session);
            } else if (uri.startsWith(API_INSTALL)) {
                return handleInstallFile(session);
            } else if (uri.startsWith(API_FILES_LIST)) {
                return handleListFiles(session);
            } else if (uri.startsWith(API_FILES_DOWNLOAD)) {
                return handleDownloadFile(session);
            } else if (uri.startsWith(API_FILES_UPLOAD)) {
                return handleUploadFile(session);
            } else if (uri.startsWith(API_SCREENSHOT)) {
                return handleScreenshot(session);
            } else {
                log.warn("serve: not handled: {}", uri);
                return createNotFoundResponse("Not found");
            }
        } catch (Exception e) {
            log.error("Error handling request: {}", e.getMessage());
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
        Map<String, String> map = DeviceManager.getInstance().fetchDevicePropertiesInternal(device);
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
        info.version = VERSION;

        // return count of devices (local only)
        List<Device> devices = DeviceManager.getInstance().getDevices();
        devices.removeIf(device -> device.remoteConnection != null);
        info.deviceCount = devices.size();
        info.serverName = RemoteConnectionUtils.getDeviceName();

        return createJsonResponse(info);
    }

    /**
     * GET /api/devices - Return list of local devices
     * - returns JSON of List<Device>
     */
    private Response handleGetDevices(IHTTPSession session) {
        List<Device> devices = DeviceManager.getInstance().getDevices();
        // remove any remote devices
        devices.removeIf(device -> device.remoteConnection != null);

        return createJsonResponse(devices);
    }

    /**
     * POST /api/execute - Execute ADB command on device
     * Body: {"serial": "xxx", "command": "shell ls"}
     */
    private Response handleExecuteCommand(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> postMap = getPostBodyMap(session);
        String serial = postMap.get("serial");
        String command = postMap.get("command");
        if (serial == null || command == null) {
            return createBadResponse("Missing serial or command");
        }

        Device device = DeviceManager.getInstance().getDevice(serial);
        if (device == null || device.remoteConnection != null) {
            return createNotFoundResponse("Device not found");
        }

        // execute command
        DeviceManager.ShellResult shellResult = DeviceManager.getInstance().runShell(device, command);
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

        DeviceManager.FileResponse fileResponse = DeviceManager.getInstance().fetchFileListInternal(device, path, false);
        if (fileResponse.fileList != null) {
            return createJsonResponse(fileResponse.fileList);
        } else {
            return createNotFoundResponse("Error: " + fileResponse.error);
        }
    }

    /**
     * GET /api/screenshot?serial=xxx
     */
    private Response handleScreenshot(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        Device device = getDeviceParam(params);
        if (device == null) {
            return createNotFoundResponse("Device not found");
        }

        // wake device
        DeviceManager.getInstance().wakeDevice(device);

        // capture screenshot
        BufferedImage bufferedImage = DeviceManager.getInstance().captureScreenshotInternal(device);

        try {
            // compress image
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            Thumbnails.of(bufferedImage)
                .scale(1.0)
                .outputFormat("png")
                .outputQuality(0.85)
                .toOutputStream(baos);

            byte[] imageBytes = baos.toByteArray();
            String filename = "screenshot_" + device.serial + ".png";

            // stream compressed image to client
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            Response response = newChunkedResponse(Response.Status.OK, "image/png", bais);
            response.addHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.addHeader("Content-Length", String.valueOf(imageBytes.length));

            return response;
        } catch (Exception e) {
            log.error("handleScreenshot: Failed to process screenshot", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Error processing screenshot: " + e.getMessage());
        }
    }

    /**
     * POST /api/setproperty - set custom property on device
     * Body: {"serial": "xxx", "key": "key", "value": "value"}
     */
    private Response handleSetProperty(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> postMap = getPostBodyMap(session);
        String serial = postMap.get("serial");
        String key = postMap.get("key");
        String value = postMap.get("value");
        if (serial == null || key == null || value == null) {
            log.trace("handleSetProperty: missing data: {}", GsonHelper.toJson(postMap));
            return createBadResponse("Missing data");
        }

        Device device = DeviceManager.getInstance().getDevice(serial);
        if (device == null || device.remoteConnection != null) {
            return createNotFoundResponse("Device not found");
        }

        // execute command
        boolean isOk = DeviceManager.getInstance().setPropertyInternal(device, key, value);
        Map<String, Object> response = new HashMap<>();
        response.put("success", isOk);
        return createJsonResponse(response);
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
            return createBadResponse("Missing path, or file parameter");
        }

        try {
            // create a temporary file to download to
            File tempFile = File.createTempFile("device_download_", "_" + filename);
            tempFile.deleteOnExit();

            DeviceFile deviceFile = new DeviceFile();
            // directories not supported - files only
            deviceFile.isDirectory = false;
            deviceFile.name = filename;

            boolean isOk = DeviceManager.getInstance().downloadFileInternal(device, path, deviceFile, tempFile);
            if (!isOk || !tempFile.exists() || tempFile.length() == 0) {
                return createNotFoundResponse("File not found on device");
            }

            // determine MIME type
            String mimeType = FileUtils.getMimeType(filename);

            // stream the file to the client
            FileInputStream fis = new FileInputStream(tempFile);
            Response response = newChunkedResponse(Response.Status.OK, mimeType, fis);
            response.addHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.addHeader("Content-Length", String.valueOf(tempFile.length()));

            return response;
        } catch (Exception e) {
            log.error("handleDownloadFile: Failed to download file", e);
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
            return createBadResponse("Missing path, or file parameter");
        }

        Timer timer = new Timer();
        try {
            // create a temporary file to receive the upload
            File tempFile = File.createTempFile("device_upload_", "_" + filename);
            tempFile.deleteOnExit();

            downloadFile(session, tempFile);
            if (!tempFile.exists() || tempFile.length() == 0) {
                return createBadResponse("No file data received");
            }

            log.trace("handleUploadFile: {}: file:{}, path:{}, size:{}", timer, filename, path, tempFile.length());

            // TODO: move jadbDevice to DeviceManager
            // use jadb to push the file to the device
            se.vidstige.jadb.RemoteFile remoteFile = new se.vidstige.jadb.RemoteFileRecord(path, filename, 0, 0, 0);
            device.jadbDevice.push(tempFile, remoteFile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded successfully");
            response.put("path", path + "/" + filename);
            return createJsonResponse(response);
        } catch (Exception e) {
            log.error("handleUploadFile: Failed to upload file", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error uploading file: " + e.getMessage());
        }
    }

    /**
     * POST /api/install?serial=xxx
     * Install file
     * Body: raw file data
     */
    private Response handleInstallFile(IHTTPSession session) {
        // NOTE: same key is used multiple times to need to use getParameters()
        Map<String, List<String>> paramMap = session.getParameters();
        List<String> deviceIdList = paramMap.get("serial");
        if (deviceIdList == null || deviceIdList.isEmpty()) {
            return createNotFoundResponse("Device not found");
        }
        // convert to list of Device's
        List<Device> devices = new ArrayList<>();
        for (String deviceId : deviceIdList) {
            Device device = DeviceManager.getInstance().getDevice(deviceId);
            if (device != null) devices.add(device);
        }
        if (devices.isEmpty()) {
            return createNotFoundResponse("Device not found");
        }
        log.trace("handleInstallFile: devices:{}", GsonHelper.toJson(deviceIdList));

        try {
            // create a temporary file to receive the upload
            File tempFile = File.createTempFile("install", ".apk");
            tempFile.deleteOnExit();

            downloadFile(session, tempFile);

            if (!tempFile.exists() || tempFile.length() == 0) {
                return createBadResponse("No file data received");
            }

            // install to all devices
            boolean isSuccess = true;
            StringBuilder sb = new StringBuilder();
            for (Device device : devices) {
                DeviceManager.Result result = DeviceManager.getInstance().installAppInternal(device, tempFile);
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(isSuccess ? "✅" : "❌");
                sb.append(" ").append(device.serial);
                // if any device fails, return failure
                if (!result.isSuccess) isSuccess = false;
            }
            String result = sb.toString();
            log.debug("handleInstallFile: {}", result);
            if (!isSuccess) {
                return createBadResponse(result);
            } else {
                return createTextResponse(result);
            }
        } catch (Exception e) {
            log.error("handleInstallFile: Exception: {}", e.getMessage());
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage());
        }
    }

    private void downloadFile(IHTTPSession session, File file) throws ResponseException, IOException {
        // parse the body and save to temp file
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);

        // the uploaded file data is in the postData
        String postData = files.get("postData");
        if (postData != null && !postData.isEmpty()) {
            // write the data to temp file
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(postData.getBytes(StandardCharsets.ISO_8859_1));
            }
        } else {
            // try to get the uploaded file from the files map
            String tmpFilePath = files.get("file");
            if (tmpFilePath != null) {
                File uploadedFile = new File(tmpFilePath);
                if (uploadedFile.exists()) {
                    // copy to our temp file
                    Files.copy(uploadedFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Map<String, String> getPostBodyMap(IHTTPSession session) throws ResponseException, IOException {
        String body = getPostBody(session);
        return GsonHelper.stringToMap(body, String.class, String.class);
    }

    private String getPostBody(IHTTPSession session) throws ResponseException, IOException {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        return files.get("postData");
    }

    private Device getDeviceParam(Map<String, String> params) {
        String serial = params.get("serial");
        Device device = DeviceManager.getInstance().getDevice(serial);
        if (device == null) {
            log.warn("getDeviceParam: device not found: {}", serial);
        } else if (device.remoteConnection != null) {
            log.warn("getDeviceParam: remote device not supported: {}", serial);
        }
        return device;
    }

    private String safeHeader(String value) {
        if (value == null) return null;
        value = value.trim();
        if (value.isEmpty()) return null;
        // basic sanitization: limit length and strip control chars
        value = value.replaceAll("[\r\n]", "");
        if (value.length() > 128) value = value.substring(0, 128);
        return value;
    }

    private Response createTextResponse(String text) {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, text);
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

    // added rejecting websocket helper
    private static class RejectWebSocket extends WebSocket {
        private final String reason;
        private final boolean sendError;

        public RejectWebSocket(IHTTPSession hs, String reason, boolean sendError) {
            super(hs);
            this.reason = reason == null ? "Rejected" : reason;
            this.sendError = sendError;
        }

        @Override
        protected void onOpen() {
            try {
                if (sendError) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("type", "error");
                    m.put("message", reason);
                    send(GsonHelper.toJson(m));
                }
                close(WebSocketFrame.CloseCode.PolicyViolation, reason, false);
            } catch (IOException ignored) {
            }
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String r, boolean remote) {
        }

        @Override
        protected void onMessage(WebSocketFrame msg) {
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
        }

        @Override
        protected void onException(IOException ex) {
        }
    }

    private WebSocket handleLogStreamWebSocket(IHTTPSession handshake, Map<String, String> params) {
        Device device = getDeviceParam(params);
        if (device == null) {
            return new RejectWebSocket(handshake, "Device not found", true);
        }
        String filterText = params.get("filter");
        LogFilter filter = null;
        if (filterText != null && !filterText.isEmpty()) {
            try {
                filter = LogFilter.parse(filterText);
                log.debug("handleLogStreamWebSocket: filter: {}", filterText);
            } catch (Exception e) {
                log.warn("handleLogStreamWebSocket: Filter parse error: {}", e.getMessage());
                return new RejectWebSocket(handshake, "Invalid filter expression", true);
            }
        }
        log.info("handleLogStreamWebSocket: Opening WebSocket log stream for device: {}", device.getDisplayName());
        return new LogStreamWebSocket(handshake, device, filter);
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        String uri = handshake.getUri();
        Map<String, String> headers = handshake.getHeaders();
        Map<String, String> params = handshake.getParms();

        log.debug("openWebSocket: upgrade request: {}", uri);

        // authenticate via query parameter or header
        if (!authenticateClient(params, headers)) {
            log.warn("openWebSocket: Unauthorized WebSocket connection attempt");
            return new RejectWebSocket(handshake, "Unauthorized", true);
        }

        // handle log streaming WebSocket
        if (uri.equals(WS_LOGS)) {
            return handleLogStreamWebSocket(handshake, params);
        } else if (uri.equals(WS_SCREEN)) {
            return handleScreenStreamWebSocket(handshake, params);
        } else {
            log.warn("openWebSocket: Unknown WebSocket endpoint: {}", uri);
            return new RejectWebSocket(handshake, "Unknown endpoint", true);
        }
    }

    /**
     * Handle screen streaming WebSocket connection
     */
    private WebSocket handleScreenStreamWebSocket(IHTTPSession handshake, Map<String, String> params) {
        String serial = params.get("serial");
        if (serial == null) {
            log.warn("handleScreenStreamWebSocket: Missing serial parameter");
            return new RejectWebSocket(handshake, "Missing serial parameter", true);
        }

        Device device = DeviceManager.getInstance().getDevice(serial);
        if (device == null) {
            log.warn("handleScreenStreamWebSocket: Device not found: {}", serial);
            return new RejectWebSocket(handshake, "Device not found", true);
        }

        // get compression parameter (defaults to false)
        boolean useCompression = "true".equalsIgnoreCase(params.get("compress"));

        log.info("handleScreenStreamWebSocket: Opening WebSocket screen stream for device: {}, compression: {}",
            device.getDisplayName(), useCompression);
        return new ScreenStreamWebSocket(handshake, device, useCompression);
    }

}

