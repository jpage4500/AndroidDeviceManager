package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.utils.GsonHelper;
import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * HTTP server that handles remote client requests
 */
public class RemoteHttpServer extends NanoHTTPD {
    private static final Logger log = LoggerFactory.getLogger(RemoteHttpServer.class);

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
        String clientIp = headers.getOrDefault("remote-addr", headers.getOrDefault("http-client-ip", "unknown"));

        log.debug("Request: {} {} from {}", method, uri, clientIp);

        // Authenticate
        String token = headers.get("authorization");
        if (token == null || !token.equals("Bearer " + authToken)) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized");
        }

        // Track client
        serverManager.trackClient(clientIp);

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
     * GET /api/screenshot?serial=xxx
     */
    private Response handleScreenshot(IHTTPSession session) {
        // TODO: Implement screenshot capture
        return newFixedLengthResponse(Response.Status.NOT_IMPLEMENTED, MIME_PLAINTEXT,
            "Screenshot feature not yet implemented");
    }
}

