package com.jpage4500.devicemanager.utils;

import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * Utility class for UPnP/IGD port forwarding
 * Automatically opens ports on UPnP-enabled routers
 */
public class UpnpUtils {
    private static final Logger log = LoggerFactory.getLogger(UpnpUtils.class);

    private static GatewayDevice activeGateway = null;

    /**
     * Attempt to open a port using UPnP
     *
     * @param port The port to open
     * @param description Description for the port mapping
     * @return true if successful, false otherwise
     */
    public static boolean openPort(int port, String description) {
        try {
            log.info("Attempting UPnP port forwarding for port {}", port);

            // Discover UPnP gateways
            GatewayDiscover discover = new GatewayDiscover();
            discover.discover();

            // Get the first valid gateway
            GatewayDevice gateway = discover.getValidGateway();
            if (gateway == null) {
                log.warn("No UPnP gateway found");
                return false;
            }

            activeGateway = gateway;
            InetAddress localAddress = gateway.getLocalAddress();

            log.info("Found UPnP gateway: {} at {}",
                gateway.getModelName(),
                gateway.getExternalIPAddress());

            // Check if mapping already exists
            PortMappingEntry portMapping = new PortMappingEntry();
            if (gateway.getSpecificPortMappingEntry(port, "TCP", portMapping)) {
                log.info("Port mapping already exists: {}", portMapping.getPortMappingDescription());

                // If it's our mapping, we're good
                if (portMapping.getInternalClient().equals(localAddress.getHostAddress())) {
                    log.info("Port {} is already mapped to this device", port);
                    return true;
                }

                // Try to delete the old mapping and create a new one
                log.info("Removing old port mapping");
                gateway.deletePortMapping(port, "TCP");
            }

            // Add port mapping
            boolean success = gateway.addPortMapping(
                port,                           // External port
                port,                           // Internal port
                localAddress.getHostAddress(), // Internal client (this machine)
                "TCP",                         // Protocol
                description                    // Description
            );

            if (success) {
                log.info("Successfully opened port {} via UPnP", port);
                log.info("External IP: {}", gateway.getExternalIPAddress());
            } else {
                log.warn("Failed to open port {} via UPnP", port);
            }

            return success;

        } catch (Exception e) {
            log.error("UPnP port forwarding failed", e);
            return false;
        }
    }

    /**
     * Close a previously opened port
     *
     * @param port The port to close
     * @return true if successful, false otherwise
     */
    public static boolean closePort(int port) {
        try {
            if (activeGateway == null) {
                log.warn("No active gateway to close port");
                return false;
            }

            log.info("Closing UPnP port mapping for port {}", port);
            boolean success = activeGateway.deletePortMapping(port, "TCP");

            if (success) {
                log.info("Successfully closed port {} via UPnP", port);
            } else {
                log.warn("Failed to close port {} via UPnP", port);
            }

            return success;

        } catch (Exception e) {
            log.error("Failed to close UPnP port", e);
            return false;
        }
    }

    /**
     * Check if a port is currently mapped
     *
     * @param port The port to check
     * @return true if mapped, false otherwise
     */
    public static boolean isPortMapped(int port) {
        try {
            if (activeGateway == null) {
                return false;
            }

            PortMappingEntry portMapping = new PortMappingEntry();
            return activeGateway.getSpecificPortMappingEntry(port, "TCP", portMapping);

        } catch (Exception e) {
            log.error("Failed to check port mapping", e);
            return false;
        }
    }

    /**
     * Get the external IP address from the UPnP gateway
     *
     * @return External IP address or null if unavailable
     */
    public static String getExternalIP() {
        try {
            if (activeGateway != null) {
                return activeGateway.getExternalIPAddress();
            }

            // Try to discover gateway
            GatewayDiscover discover = new GatewayDiscover();
            discover.discover();
            GatewayDevice gateway = discover.getValidGateway();

            if (gateway != null) {
                activeGateway = gateway;
                return gateway.getExternalIPAddress();
            }

        } catch (Exception e) {
            log.error("Failed to get external IP via UPnP", e);
        }

        return null;
    }
}

