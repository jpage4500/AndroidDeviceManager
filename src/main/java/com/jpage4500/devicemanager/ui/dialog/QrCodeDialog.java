package com.jpage4500.devicemanager.ui.dialog;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.jpage4500.devicemanager.logging.AppLoggerFactory;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.RemoteConnectionUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * dialog for displaying a QR code for ADB wireless pairing
 */
public class QrCodeDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(QrCodeDialog.class);

    private static final int QR_CODE_SIZE = 200;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SERVICE_NAME_LENGTH = 20;
    private static final int PASSWORD_LENGTH = 20;
    private static final String MDNS_SERVICE_TYPE = "_adb-tls-pairing._tcp.local.";

    private final String serviceName;
    private final String password;
    private JmDNS jmdns;
    private final AtomicBoolean isPaired = new AtomicBoolean(false);
    private JLabel statusLabel;

    public interface QRCodeListener {
        void qrCodeDialogClosed();
    }

    public static void showQrCodeDialog(Component parent, QRCodeListener listener) {
        QrCodeDialog dialog = new QrCodeDialog();
        // ensure panel is fully laid out before showing
        dialog.revalidate();

        // start mDNS discovery
        dialog.startDiscovery();

        // show dialog and cleanup when closed
        DialogHelper.showCustomDialog(parent, dialog, "Pair Device with QR Code", new String[]{"Close"});

        // cleanup when dialog is closed
        dialog.stopDiscovery();

        listener.qrCodeDialogClosed();
    }

    private QrCodeDialog() {
        // generate random service name and password
        serviceName = "ADB_WIFI_" + generateRandomString(SERVICE_NAME_LENGTH);
        password = generateRandomString(PASSWORD_LENGTH);

        setLayout(new MigLayout("fillx, insets 20", "[center]"));
        setPreferredSize(new Dimension(450, 450));

        // title label
        JLabel titleLabel = new JLabel("<html><b>Scan QR Code to Pair Device</b></html>");
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));
        add(titleLabel, "wrap, gapbottom 20");

        // instructions
        JTextArea instructions = new JTextArea(
            """
                On your Android device:
                1. Go to Settings → Developer options
                2. Enable 'Wireless debugging'
                3. Tap 'Pair device with QR code'
                4. Scan the QR code below"""
        );
        instructions.setEditable(false);
        instructions.setBackground(getBackground());
        instructions.setFont(instructions.getFont().deriveFont(13f));
        instructions.setBorder(BorderFactory.createEmptyBorder());
        add(instructions, "wrap, gapbottom 20");

        // generate and display QR code
        try {
            BufferedImage qrImage = generateQRCode();
            JLabel qrLabel = new JLabel(new ImageIcon(qrImage));
            qrLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 2));
            add(qrLabel, "wrap, gapbottom 20");
        } catch (WriterException e) {
            log.error("QrCodeDialog: failed to generate QR code: {}", e.getMessage());
            JLabel errorLabel = new JLabel("<html><b>Failed to generate QR code</b></html>");
            errorLabel.setForeground(Color.RED);
            add(errorLabel, "wrap");
        }

        // status label for showing pairing progress
        statusLabel = new JLabel("Waiting for device to scan QR code...");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
        statusLabel.setForeground(new Color(0, 102, 204)); // blue color
        add(statusLabel, "wrap, gapbottom 10");

        // note
        JLabel noteLabel = new JLabel("<html><i>Note: After scanning the QR code, pairing will happen automatically.</i></html>");
        noteLabel.setFont(noteLabel.getFont().deriveFont(11f));
        noteLabel.setForeground(Color.GRAY);
        add(noteLabel, "wrap");
    }

    /**
     * generate QR code for ADB wireless pairing
     * QR code format: WIFI:T:ADB;S:<service_name>;P:<password>;;
     */
    private BufferedImage generateQRCode() throws WriterException {
        String qrContent = String.format("WIFI:T:ADB;S:%s;P:%s;;", serviceName, password);
        log.debug("generateQRCode: generating QR code with content: {}", qrContent);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(qrContent, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);

        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }

    /**
     * generate a random alphanumeric string
     */
    private String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    /**
     * start mDNS discovery to listen for ADB pairing service
     */
    private void startDiscovery() {
        new Thread(() -> {
            try {
                configureLogging(true);

                log.info("startDiscovery: starting mDNS discovery for ADB pairing service...");
                updateStatus("Starting discovery...", Color.BLUE);

                // create JmDNS instance
                InetAddress addr = findNetworkInterface();
                log.info("startDiscovery: using network interface: {} ({})", addr.getHostName(), addr.getHostAddress());
                jmdns = JmDNS.create(addr);

                // add service listener
                jmdns.addServiceListener(MDNS_SERVICE_TYPE, new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent event) {
                        log.debug("serviceAdded: service added: {}", event.getName());
                        // request service info
                        jmdns.requestServiceInfo(event.getType(), event.getName(), 1000);
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent event) {
                        log.debug("serviceRemoved: service removed: {}", event.getName());
                    }

                    @Override
                    public void serviceResolved(ServiceEvent event) {
                        if (isPaired.get()) {
                            return; // already paired, ignore
                        }

                        ServiceInfo info = event.getInfo();
                        log.info("serviceResolved: service resolved: {}, addresses: {}", info.getName(), info.getHostAddresses());

                        // find the best address to use (prefer IPv4, avoid IPv6 link-local)
                        String bestAddress = findBestAddress(info.getHostAddresses());
                        if (bestAddress == null) {
                            log.debug("serviceResolved: no suitable address found, waiting for more info");
                            return;
                        }

                        int port = info.getPort();

                        // set flag immediately to prevent multiple pairing attempts
                        if (!isPaired.compareAndSet(false, true)) {
                            log.debug("serviceResolved: pairing already in progress, ignoring");
                            return;
                        }

                        log.info("serviceResolved: attempting to pair with {}:{} using password: {}", bestAddress, port, password);
                        updateStatus("Device found! Pairing...", new Color(255, 140, 0)); // orange

                        // attempt pairing
                        pairWithDevice(bestAddress, port);
                    }
                });

                updateStatus("Listening for device... Scan QR code now.", new Color(0, 153, 0)); // green
                log.info("startDiscovery: mDNS discovery started successfully");

            } catch (IOException e) {
                log.error("startDiscovery: failed to start mDNS discovery: {}", e.getMessage());
                updateStatus("Discovery failed: " + e.getMessage(), Color.RED);
            }
        }, "mDNS-Discovery").start();
    }

    /**
     * stop mDNS discovery and cleanup resources
     */
    private void stopDiscovery() {
        if (jmdns != null) {
            try {
                log.info("stopDiscovery: stopping mDNS discovery...");
                jmdns.close();
                jmdns = null;
            } catch (IOException e) {
                log.error("stopDiscovery: error closing JmDNS: {}", e.getMessage());
            }
            configureLogging(false);
        }
    }

    /**
     * pair with the discovered device
     */
    private void pairWithDevice(String address, int port) {
        DeviceManager.getInstance().pairDevice(address, port, password, (isSuccess, result) -> {
            if (isSuccess) {
                log.info("pairWithDevice: successfully paired with device at {}:{}", address, port);
                updateStatus("✓ Pairing successful!", new Color(0, 153, 0));

                // stop discovery to prevent further pairing attempts
                stopDiscovery();

                // show success notification and close dialog
                SwingUtilities.invokeLater(() -> {
                    String msg = String.format("Successfully paired with device at %s:%d", address, port);
                    DialogHelper.showDialog(QrCodeDialog.this, "Pairing Successful", msg);

                    // close the QR code dialog window
                    Window window = SwingUtilities.getWindowAncestor(QrCodeDialog.this);
                    if (window != null) {
                        window.dispose();
                    }
                });
            } else {
                log.error("pairWithDevice: failed to pair with device at {}:{}, error: {}", address, port, result);
                updateStatus("✗ Pairing failed: " + result, Color.RED);

                // reset flag so user can try again
                isPaired.set(false);
            }
        });
    }

    /**
     * update status label on UI thread
     */
    private void updateStatus(String message, Color color) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                statusLabel.setForeground(color);
            }
        });
    }

    /**
     * find the best address to use for pairing
     * prefers IPv4 over IPv6, filters out link-local IPv6 addresses
     *
     * @param addresses array of IP addresses
     * @return best address to use, or null if none suitable
     */
    private String findBestAddress(String[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }

        String firstIpv4 = null;
        String firstIpv6 = null;

        for (String address : addresses) {
            // remove brackets if present (IPv6 addresses are wrapped in brackets)
            String cleanAddress = address.replace("[", "").replace("]", "");

            // skip IPv6 link-local addresses (fe80::)
            if (cleanAddress.startsWith("fe80:")) {
                log.debug("findBestAddress: skipping IPv6 link-local address: {}", address);
                continue;
            }

            // determine if IPv4 or IPv6
            boolean isIpv4 = cleanAddress.contains("."); // simple check: IPv4 has dots
            if (isIpv4) {
                if (firstIpv4 == null) {
                    firstIpv4 = cleanAddress;
                }
            } else {
                if (firstIpv6 == null) {
                    firstIpv6 = cleanAddress;
                }
            }
        }

        // prefer IPv4 over IPv6
        if (firstIpv4 != null) {
            log.debug("findBestAddress: selected IPv4 address: {}", firstIpv4);
            return firstIpv4;
        } else if (firstIpv6 != null) {
            log.debug("findBestAddress: selected IPv6 address: {}", firstIpv6);
            return firstIpv6;
        }

        log.warn("findBestAddress: no suitable address found in: {}", (Object) addresses);
        return null;
    }

    /**
     * find the appropriate network interface for mDNS
     * prefers non-loopback, active interfaces
     */
    private InetAddress findNetworkInterface() throws IOException {
        List<RemoteConnectionUtils.Network> networkList = RemoteConnectionUtils.getActiveNetworkInfo();

        if (networkList.isEmpty()) {
            // last resort: use local host (will likely not work for mDNS)
            log.warn("findNetworkInterface: could not find suitable network interface, falling back to localhost");
            return InetAddress.getLocalHost();
        }

        // prefer site-local addresses (192.168.x.x, 10.x.x.x, etc)
        for (RemoteConnectionUtils.Network network : networkList) {
            InetAddress addr = InetAddress.getByName(network.ip);
            if (addr.isSiteLocalAddress()) {
                log.debug("findNetworkInterface: found suitable network interface: {} - {}", network.label, network.ip);
                return addr;
            }
        }

        // use first available network
        RemoteConnectionUtils.Network network = networkList.get(0);
        log.debug("findNetworkInterface: using fallback network interface: {} - {}", network.label, network.ip);
        return InetAddress.getByName(network.ip);
    }

    /**
     * disable jmDNS logging (too verbose)
     *
     * @param disable true to disable, false to re-enable default
     */
    private void configureLogging(boolean disable) {
        log.trace("configureLogging: {}", disable);
        String[] jmdnsLoggers = {
            "Announcer",
            "Canceler",
            "DNSCache",
            "DNSEntry",
            "DNSIncoming",
            "DNSLabel",
            "DNSMessage",
            "DNSOutgoing",
            "DNSQuestion",
            "DNSRecord",
            "DNSRecordClass",
            "DNSRecordType",
            "DNSResolverTask",
            "DNSState",
            "DNSStateTask",
            "HostInfo",
            "JmDNSImpl",
            "ListenerStatus",
            "Prober",
            "RecordReaper",
            "Renewer",
            "Responder",
            "ServiceInfoImpl",
            "ServiceInfoResolver",
            "ServiceResolver",
            "SocketListener",
            "TypeResolver"
        };

        ILoggerFactory iLoggerFactory = LoggerFactory.getILoggerFactory();
        if (iLoggerFactory instanceof AppLoggerFactory logger) {
            if (disable) {
                logger.setIgnoreArr(jmdnsLoggers);
            } else {
                logger.setIgnoreArr(null);
            }
        }
    }
}

