package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.data.RemoteServerConfig;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.NetworkHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public RemoteConnection(RemoteServerConfig config) {
        this.serverConfig = config;
        networkHelper = new NetworkHelper();
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

}

