package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * HTTP server that handles remote client requests
 */
public class RemoteHttpServer extends NanoHTTPD {
    private static final Logger log = LoggerFactory.getLogger(RemoteHttpServer.class);

    public static final String HEADER_IP = "x-client-ip";
    public static final String HEADER_NAME = "x-client-name";
    public static final String HEADER_AUTHORIZATION = "authorization";

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
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Map<String, String> headers = session.getHeaders();
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
        String token = headers.get(HEADER_AUTHORIZATION);
        if (token == null || !token.equals("Bearer " + authToken)) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized");
        }

        // Track client (with optional name)
        serverManager.trackClient(clientIp, headerName);

        // Route request
        try {
            if (uri.equals("/api/info")) {
                return handleServerInfo(session);
            } else if (uri.equals("/api/devices")) {
                return handleGetDevices(session);
            } else if (uri.equals("/api/execute")) {
                return handleExecuteCommand(session);
            } else if (uri.startsWith("/api/files/list")) {
                return handleListFiles(session);
            } else if (uri.startsWith("/api/files/download")) {
                return handleDownloadFile(session);
            } else if (uri.startsWith("/api/files/upload")) {
                return handleUploadFile(session);
            } else if (uri.startsWith("/api/screenshot")) {
                return handleScreenshot(session);
            } else {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
            }
        } catch (Exception e) {
            log.error("Error handling request", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Error: " + e.getMessage());
        }
    }

    /**
     * GET /api/info - Server information
     */
    private Response handleServerInfo(IHTTPSession session) {
        Map<String, Object> info = new HashMap<>();
        info.put("version", "1.0");
        info.put("deviceCount", deviceManager.getDevices().size());
        info.put("serverName", "Android Device Manager");

        String json = GsonHelper.toJson(info);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    /**
     * GET /api/devices - Return list of local devices
     */
    private Response handleGetDevices(IHTTPSession session) {
        List<Device> devices = deviceManager.getDevices();

        // Create simplified device list (remove sensitive data)
        List<Map<String, Object>> deviceList = new ArrayList<>();
        for (Device device : devices) {
            if (!device.isRemote) { // Only share local devices
                Map<String, Object> deviceInfo = new HashMap<>();
                deviceInfo.put("serial", device.serial);
                deviceInfo.put("nickname", device.nickname);
                deviceInfo.put("model", device.getProperty(Device.PROP_MODEL));
                deviceInfo.put("os", device.getProperty(Device.PROP_OS));
                deviceInfo.put("batteryLevel", device.batteryLevel);
                deviceInfo.put("isOnline", device.isOnline);
                deviceInfo.put("isBooted", device.isBooted);
                deviceList.add(deviceInfo);
            }
        }

        String json = GsonHelper.toJson(deviceList);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
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
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                "Missing request body");
        }

        @SuppressWarnings("unchecked")
        Map<String, String> request = GsonHelper.fromJson(body, Map.class);
        String serial = request.get("serial");
        String command = request.get("command");

        if (serial == null || command == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                "Missing serial or command");
        }

        Device device = deviceManager.getDevice(serial);
        if (device == null || device.isRemote) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                "Device not found");
        }

        // Execute command
        DeviceManager.ShellResult shellResult = deviceManager.runShell(device, command);

        // Convert result list to string
        StringBuilder outputBuilder = new StringBuilder();
        if (shellResult.resultList != null) {
            for (String line : shellResult.resultList) {
                if (outputBuilder.length() > 0) {
                    outputBuilder.append("\n");
                }
                outputBuilder.append(line);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", shellResult.isSuccess);
        response.put("output", outputBuilder.toString());
        response.put("error", shellResult.isSuccess ? "" : "Command failed");

        String json = GsonHelper.toJson(response);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    /**
     * GET /api/files/list?serial=xxx&path=/sdcard
     */
    private Response handleListFiles(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String serial = params.get("serial");
        String path = params.get("path");

        if (serial == null || path == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                "Missing serial or path");
        }

        Device device = deviceManager.getDevice(serial);
        if (device == null) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                "Device not found");
        }

        try {
            List<DeviceFile> files = deviceManager.getFileListSync(device, path);
            String json = GsonHelper.toJson(files);
            return newFixedLengthResponse(Response.Status.OK, "application/json", json);
        } catch (Exception e) {
            log.error("Failed to list files", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Error: " + e.getMessage());
        }
    }

    /**
     * GET /api/files/download?serial=xxx&path=/sdcard&file=test.txt
     * Downloads a file from the device
     */
    private Response handleDownloadFile(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String serial = params.get("serial");
        String path = params.get("path");
        String filename = params.get("file");

        if (serial == null || path == null || filename == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                "Missing serial, path, or file parameter");
        }

        Device device = deviceManager.getDevice(serial);
        if (device == null || device.isRemote) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                "Device not found");
        }

        try {
            // Create a temporary file to download to
            File tempFile = File.createTempFile("device_download_", "_" + filename);
            tempFile.deleteOnExit();

            // Use jadb to pull the file
            se.vidstige.jadb.RemoteFile remoteFile = new se.vidstige.jadb.RemoteFileRecord(path, filename, 0, 0, 0);
            device.jadbDevice.pull(remoteFile, tempFile);

            if (!tempFile.exists() || tempFile.length() == 0) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                    "File not found or empty on device");
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
        String serial = params.get("serial");
        String path = params.get("path");
        String filename = params.get("file");

        if (serial == null || path == null || filename == null) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                "Missing serial, path, or file parameter");
        }

        Device device = deviceManager.getDevice(serial);
        if (device == null || device.isRemote) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                "Device not found");
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
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                    "No file data received");
            }

            // Use jadb to push the file to the device
            se.vidstige.jadb.RemoteFile remoteFile = new se.vidstige.jadb.RemoteFileRecord(path, filename, 0, 0, 0);
            device.jadbDevice.push(tempFile, remoteFile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded successfully");
            response.put("path", path + "/" + filename);

            String json = GsonHelper.toJson(response);
            return newFixedLengthResponse(Response.Status.OK, "application/json", json);
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Error uploading file: " + e.getMessage());
            String json = GsonHelper.toJson(response);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", json);
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
        if (lower.endsWith(".json")) return "application/json";
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
}

