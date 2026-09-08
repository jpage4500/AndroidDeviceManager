package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.data.RemoteServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for generating and parsing connection strings
 */
public class RemoteConnectionUtils {
    private static final Logger log = LoggerFactory.getLogger(RemoteConnectionUtils.class);

    private static final String PREFIX = "adm://";
    private static final String HARDWARE_PORT_KEY = "Hardware Port: ";
    private static final String DEVICE_KEY = "Device: ";
    private static final int COMMAND_TIMEOUT_SEC = 2;

    // macOS device name (en0) to hardware port (Wi-Fi)
    private static Map<String, String> macHardwarePortMap;

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
            log.error("Failed to parse connection string:{}", e.getMessage());
            return null;
        }
    }

    /**
     * Get public IP address
     */
    public static String getPublicIpAddress() {
        // try to get public IP address first
        try {
            NetworkHelper.HttpResponse response = NetworkHelper.getRequest("https://api.ipify.org");
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
        public static final String TYPE_VPN = "VPN";

        public String label;
        public String ip;
        public String host;
        public String type;
        public boolean isSiteLocal;

        /**
         * example: "192.168.0.10 (mymac.local) - Wi-Fi (en0)"
         */
        public String getDesc() {
            StringBuilder sb = new StringBuilder();
            sb.append(ip);
            if (TextUtils.notEmpty(host) && !TextUtils.equals(ip, host)) {
                sb.append(" (");
                sb.append(host);
                sb.append(")");
            }
            sb.append(" - ");
            sb.append(getTypeDesc());
            return sb.toString();
        }

        /**
         * true if this is a LAN address other devices on the same network can reach
         */
        public boolean isLocalNetwork() {
            return isSiteLocal && !TextUtils.equals(type, TYPE_VPN);
        }

        /**
         * example: "Wi-Fi (en0)"
         */
        public String getTypeDesc() {
            if (TextUtils.isEmpty(type)) return label;
            return type + " (" + label + ")";
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
                        network.type = getInterfaceType(iface);
                        network.isSiteLocal = addr.isSiteLocalAddress();
                        networkList.add(network);
                    }
                }
            }
        } catch (Exception e) {
            log.error("getActiveNetworkInfo: Exception: {}", e.getMessage());
        }
        return networkList;
    }

    /**
     * describe an interface (Wi-Fi, Ethernet, VPN, ..) so it's clear where an IP address comes from
     *
     * @return interface type or null if unknown
     */
    private static String getInterfaceType(NetworkInterface iface) throws SocketException {
        String name = iface.getName();
        // VPN clients use tunnel interfaces - point-to-point with no hardware address
        if (TextUtils.startsWithAny(name, true, "utun", "tun", "tap", "ppp", "ipsec", "wg")
            || (iface.isPointToPoint() && iface.getHardwareAddress() == null)) {
            return Network.TYPE_VPN;
        }

        // macOS names every interface "enX" - ask networksetup which port that is
        if (Utils.isMac()) return getMacHardwarePort(name);

        // Windows display names are already readable ("Wi-Fi", "Intel(R) Ethernet .."); linux uses name prefixes
        String desc = TextUtils.firstValid(iface.getDisplayName(), name);
        if (TextUtils.containsAny(desc, true, "vpn", "tunnel", "wireguard", "tailscale", "zerotier", "anyconnect", "netskope", "zscaler", "globalprotect")) return Network.TYPE_VPN;
        if (TextUtils.containsAny(desc, true, "virtual", "hyper-v", "vmware", "virtualbox", "parallels", "docker")) return "Virtual";
        if (TextUtils.startsWithAny(name, true, "wl") || TextUtils.containsAny(desc, true, "wi-fi", "wifi", "wireless", "802.11")) return "Wi-Fi";
        if (TextUtils.startsWithAny(name, true, "en", "eth") || TextUtils.containsAny(desc, true, "ethernet")) return "Ethernet";
        return null;
    }

    /**
     * map a macOS device name (en0) to the hardware port it belongs to (Wi-Fi)
     */
    private static synchronized String getMacHardwarePort(String name) {
        if (macHardwarePortMap == null) {
            macHardwarePortMap = new HashMap<>();
            // output repeats: "Hardware Port: Wi-Fi" / "Device: en0" / "Ethernet Address: .."
            String port = null;
            for (String line : runCommand("networksetup", "-listallhardwareports")) {
                if (line.startsWith(HARDWARE_PORT_KEY)) {
                    port = line.substring(HARDWARE_PORT_KEY.length()).trim();
                } else if (line.startsWith(DEVICE_KEY) && port != null) {
                    macHardwarePortMap.put(line.substring(DEVICE_KEY.length()).trim(), port);
                    port = null;
                }
            }
            log.trace("getMacHardwarePort: {}", macHardwarePortMap);
        }
        return macHardwarePortMap.get(name);
    }

    private static List<String> runCommand(String... command) {
        List<String> resultList = new ArrayList<>();
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    resultList.add(line);
                }
            }
            if (!process.waitFor(COMMAND_TIMEOUT_SEC, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (Exception e) {
            log.debug("runCommand: {}: Exception: {}", command[0], e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return resultList;
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

