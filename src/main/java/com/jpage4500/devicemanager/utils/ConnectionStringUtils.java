package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.data.RemoteServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilities for generating and parsing connection strings
 */
public class ConnectionStringUtils {
    private static final Logger log = LoggerFactory.getLogger(ConnectionStringUtils.class);
    private static final String PREFIX = "adm://";

    /**
     * Generate connection string from server config
     */
    public static String generateConnectionString(String host, int port, String authToken, String name) {
        Map<String, Object> config = new HashMap<>();
        config.put("host", host);
        config.put("port", port);
        config.put("token", authToken);
        config.put("name", name);

        String json = GsonHelper.toJson(config);
        log.trace("generateConnectionString: {}", json);
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded;
    }

    /**
     * Parse connection string into server config
     */
    public static RemoteServerConfig parseConnectionString(String connectionString) {
        if (connectionString == null || !connectionString.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid connection string format");
        }

        try {
            String encoded = connectionString.substring(PREFIX.length());
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

            Map<String, Object> data = GsonHelper.fromJson(json, Map.class);

            RemoteServerConfig config = new RemoteServerConfig();
            config.name = (String) data.getOrDefault("name", "Unknown Server");
            config.host = (String) data.get("host");
            config.port = ((Number) data.get("port")).intValue();
            config.authToken = (String) data.get("token");
            config.enabled = true;

            return config;
        } catch (Exception e) {
            log.error("Failed to parse connection string", e);
            throw new IllegalArgumentException("Invalid connection string data", e);
        }
    }

    /**
     * Generate shareable URL that can be clicked to open app
     */
    public static String generateShareUrl(String host, int port, String authToken, String name) {
        try {
            return String.format("adb-manager://connect?host=%s&port=%d&token=%s&name=%s",
                URLEncoder.encode(host, StandardCharsets.UTF_8),
                port,
                URLEncoder.encode(authToken, StandardCharsets.UTF_8),
                URLEncoder.encode(name, StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to generate share URL", e);
            return null;
        }
    }

    /**
     * Check if string is a valid connection string
     */
    public static boolean isValidConnectionString(String str) {
        if (str == null || !str.startsWith(PREFIX)) {
            return false;
        }
        try {
            parseConnectionString(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

