package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.data.RemoteServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utilities for generating and parsing connection strings
 */
public class RemoteConnectionUtils {
    private static final Logger log = LoggerFactory.getLogger(RemoteConnectionUtils.class);

    private static final String PREFIX = "adm://";

    public static String generateAuthToken() {
        // generate a new random token (16 characters)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int index = (int) (Math.random() * chars.length());
            token.append(chars.charAt(index));
        }
        return token.toString();
    }

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
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return PREFIX + encoded;
    }

    /**
     * Parse connection string into server config
     */
    public static RemoteServerConfig parseConnectionString(String connectionString) {
        if (connectionString == null || !connectionString.startsWith(PREFIX)) {
            return null;
        }

        try {
            String encoded = connectionString.substring(PREFIX.length());
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            Map<String, Object> data = GsonHelper.stringToMap(json, String.class, Object.class);

            RemoteServerConfig config = new RemoteServerConfig();
            config.name = (String) data.getOrDefault("name", "Unknown Server");
            config.host = (String) data.get("host");
            config.port = ((Number) data.get("port")).intValue();
            config.authToken = (String) data.get("token");
            config.enabled = true;

            return config;
        } catch (Exception e) {
            log.error("Failed to parse connection string", e);
            return null;
        }
    }

    /**
     * Get public IP address
     */
    public static String getPublicIpAddress() {
        // try to get public IP address first
        try {
            NetworkHelper networkHelper = new NetworkHelper();
            NetworkHelper.HttpResponse response = networkHelper.getRequest("https://api.ipify.org");
            if (response.status == 200) {
                return response.body.trim();
            }
        } catch (Exception e) {
            log.debug("Failed to get public IP, falling back to local: {}", e.getMessage());
        }

        // fall back to local IP
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    public static class Network {
        public String label;
        public String ip;
        public String host;

        public String getDesc() {
            StringBuilder sb = new StringBuilder();
            sb.append(ip);
            if (TextUtils.notEmpty(host) && !TextUtils.equals(ip, host)) {
                sb.append(" (");
                sb.append(host);
                sb.append(")");
            }
            return sb.toString();
        }
    }

    public static List<Network> getActiveNetworkInfo() {
        List<Network> networkList = new ArrayList<>();
        // get the actual LAN IP (not 127.0.0.1)
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // skip loopback and inactive interfaces
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // we want IPv4 addresses only (skip IPv6)
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        log.trace("getActiveNetworkInfo: {}, {}, {}", addr.getHostAddress(), addr.getHostName(), iface.getName());
                        Network network = new Network();
                        network.label = iface.getName();
                        network.ip = addr.getHostAddress();
                        // NOTE: getHostName will do a reverse lookup and could take a while
                        network.host = addr.getHostName();
                        networkList.add(network);
                    }
                }
            }
        } catch (Exception e) {
            log.error("getActiveNetworkInfo: Exception: {}", e.getMessage());
        }
        return networkList;
    }

    public static String getDeviceName() {
        // try to get from preferences first
        String savedName = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_SERVER_DEVICE_NAME);
        if (savedName != null && !savedName.isEmpty()) {
            return savedName;
        }

        // fall back to hostname
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "My Device";
        }
    }

}

