package se.vidstige.jadb;

import java.io.IOException;
import java.net.InetSocketAddress;

class HostPairWithRemoteTcpDevice {
    private final Transport transport;

    HostPairWithRemoteTcpDevice(Transport transport) {
        this.transport = transport;
    }

    /**
     * pair with a remote device using wireless pairing
     * @param inetSocketAddress pairing address (not connection address)
     * @param pairingCode Pairing code from QR code or device screen
     * @return the address that was paired
     * @throws IOException if transport fails
     * @throws JadbException if adb protocol fails
     * @throws ConnectionToRemoteDeviceException if pairing fails
     */
    InetSocketAddress pair(InetSocketAddress inetSocketAddress, String pairingCode)
            throws IOException, JadbException, ConnectionToRemoteDeviceException {
        // format: host:pair:<pairing_code>:<host>:<port>
        // example: host:pair:000000:192.168.0.56:43493
        String command = String.format("host:pair:%s:%s:%d",
            pairingCode,
            inetSocketAddress.getHostString(),
            inetSocketAddress.getPort());

        transport.send(command);
        verifyTransportLevel();
        verifyProtocolLevel();

        return inetSocketAddress;
    }

    private void verifyTransportLevel() throws IOException, JadbException {
        try {
            transport.verifyResponse();
        } catch (NumberFormatException e) {
            // happens when ADB returns "FAIL" and transport tries to parse it as hex length
            throw new JadbException("Pairing failed - ADB server rejected the pairing request");
        }
    }

    private void verifyProtocolLevel() throws IOException, ConnectionToRemoteDeviceException {
        try {
            String status = transport.readString();
            if (!checkIfPairedSuccessfully(status)) {
                throw new ConnectionToRemoteDeviceException(extractError(status));
            }
        } catch (NumberFormatException e) {
            // happens when response format is unexpected
            throw new ConnectionToRemoteDeviceException("Invalid pairing response from ADB server");
        }
    }

    private boolean checkIfPairedSuccessfully(String response) {
        return response.toLowerCase().contains("successfully paired");
    }

    private String extractError(String response) {
        int lastColon = response.lastIndexOf(':');
        if (lastColon != -1) {
            return response.substring(lastColon + 1).trim();
        } else {
            return response;
        }
    }
}

