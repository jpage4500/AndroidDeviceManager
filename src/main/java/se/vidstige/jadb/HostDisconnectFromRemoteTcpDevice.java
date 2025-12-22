package se.vidstige.jadb;

import java.io.IOException;
import java.net.InetSocketAddress;

public class HostDisconnectFromRemoteTcpDevice extends HostConnectionCommand {
    HostDisconnectFromRemoteTcpDevice(Transport transport) {
        super(transport, new ResponseValidatorImp());

    }

    //Visible for testing
    HostDisconnectFromRemoteTcpDevice(Transport transport, ResponseValidator responseValidator) {
        super(transport, responseValidator);
    }

    InetSocketAddress disconnect(InetSocketAddress inetSocketAddress)
            throws IOException, JadbException, ConnectionToRemoteDeviceException {
        return executeHostCommand("disconnect", inetSocketAddress);
    }

    /**
     * disconnect from device using serial string (supports mDNS names)
     * @param serial device serial (can be ip:port or mDNS name)
     * @throws IOException if transport fails
     * @throws JadbException if adb protocol fails
     * @throws ConnectionToRemoteDeviceException if disconnect fails
     */
    void disconnectBySerial(String serial)
            throws IOException, JadbException, ConnectionToRemoteDeviceException {
        Transport transport = getTransport();
        transport.send(String.format("host:disconnect:%s", serial));
        verifyTransportLevel(transport);
        verifyProtocolLevel(transport);
    }

    static final class ResponseValidatorImp extends ResponseValidatorBase {
        private static final String SUCCESSFULLY_DISCONNECTED = "disconnected";
        private static final String ALREADY_DISCONNECTED = "error: no such device";

        ResponseValidatorImp() {
            super(SUCCESSFULLY_DISCONNECTED, ALREADY_DISCONNECTED);
        }
    }
}
