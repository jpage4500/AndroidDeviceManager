package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.RemoteServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles network discovery using mDNS/Bonjour
 */
public class NetworkDiscoveryManager {
    private static final Logger log = LoggerFactory.getLogger(NetworkDiscoveryManager.class);

    private static final String SERVICE_TYPE = "_adb-manager._tcp.local.";

    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private final Map<String, RemoteServerConfig> discoveredServers = new ConcurrentHashMap<>();
    private DiscoveryListener listener;

    public interface DiscoveryListener {
        void onServerDiscovered(RemoteServerConfig server);

        void onServerLost(String serverId);
    }

    /**
     * Start broadcasting this server on the network
     */
    public void startBroadcasting(int port, String authToken, String serverName) {
        try {
            jmdns = JmDNS.create(InetAddress.getLocalHost());

            Map<String, String> properties = new HashMap<>();
            properties.put("authToken", authToken);
            properties.put("version", "1.0");

            serviceInfo = ServiceInfo.create(
                SERVICE_TYPE,
                serverName,
                port,
                0,
                0,
                properties
            );

            jmdns.registerService(serviceInfo);
            log.info("Started broadcasting server: {} on port {}", serverName, port);

        } catch (IOException e) {
            log.error("Failed to start broadcasting", e);
        }
    }

    /**
     * Stop broadcasting this server
     */
    public void stopBroadcasting() {
        if (jmdns != null && serviceInfo != null) {
            jmdns.unregisterService(serviceInfo);
            try {
                jmdns.close();
            } catch (IOException e) {
                log.error("Error closing JmDNS", e);
            }
            jmdns = null;
            serviceInfo = null;
            log.info("Stopped broadcasting server");
        }
    }

    /**
     * Start discovering servers on the network
     */
    public void startDiscovery() {
        try {
            if (jmdns == null) {
                jmdns = JmDNS.create();
            }

            jmdns.addServiceListener(SERVICE_TYPE, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    log.debug("Service discovered: {}", event.getName());
                    // Request full service info
                    jmdns.requestServiceInfo(event.getType(), event.getName(), 1000);
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    ServiceInfo info = event.getInfo();
                    log.info("Service resolved: {} at {}:{}",
                        info.getName(),
                        info.getHostAddresses()[0],
                        info.getPort());

                    RemoteServerConfig config = new RemoteServerConfig();
                    config.id = info.getName(); // Use service name as ID for discovery
                    config.name = info.getName();
                    config.host = info.getHostAddresses()[0];
                    config.port = info.getPort();
                    config.authToken = info.getPropertyString("authToken");
                    config.enabled = false; // User must enable discovered servers

                    discoveredServers.put(config.id, config);

                    if (listener != null) {
                        listener.onServerDiscovered(config);
                    }
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    log.info("Service removed: {}", event.getName());
                    String serverId = event.getName();
                    discoveredServers.remove(serverId);

                    if (listener != null) {
                        listener.onServerLost(serverId);
                    }
                }
            });

            log.info("Started network discovery");

        } catch (IOException e) {
            log.error("Failed to start discovery", e);
        }
    }

    /**
     * Stop discovering servers
     */
    public void stopDiscovery() {
        if (jmdns != null) {
            try {
                jmdns.close();
            } catch (IOException e) {
                log.error("Error closing JmDNS", e);
            }
            jmdns = null;
            discoveredServers.clear();
            log.info("Stopped network discovery");
        }
    }

    public Map<String, RemoteServerConfig> getDiscoveredServers() {
        return new HashMap<>(discoveredServers);
    }

    public void setListener(DiscoveryListener listener) {
        this.listener = listener;
    }

    public boolean isBroadcasting() {
        return serviceInfo != null;
    }

    public boolean isDiscovering() {
        return jmdns != null;
    }

}

