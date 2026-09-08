package com.jpage4500.devicemanager.ui.dialog;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.jpage4500.devicemanager.utils.DialogHelper;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * dialog showing a QR code of this server's connection string
 */
public class ConnectionQrCodeDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ConnectionQrCodeDialog.class);

    private static final int QR_CODE_SIZE = 300;

    public static void showConnectionQrCodeDialog(Component parent, String connectionString) {
        ConnectionQrCodeDialog dialog = new ConnectionQrCodeDialog(connectionString);
        DialogHelper.showCustomDialog(parent, dialog, "Connection QR Code", new String[]{"Close"});
    }

    private ConnectionQrCodeDialog(String connectionString) {
        setLayout(new MigLayout("fillx, insets 20", "[center]"));

        JLabel titleLabel = new JLabel("Scan this code to add this server");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(titleLabel, "wrap, gapbottom 15");

        try {
            JLabel qrLabel = new JLabel(new ImageIcon(generateQrCode(connectionString)));
            qrLabel.setOpaque(true);
            qrLabel.setBackground(Color.WHITE);
            qrLabel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 2));
            add(qrLabel, "wrap, gapbottom 15");
        } catch (WriterException e) {
            log.error("ConnectionQrCodeDialog: failed to generate QR code: {}", e.getMessage());
            JLabel errorLabel = new JLabel("Failed to generate QR code");
            errorLabel.setForeground(Color.RED);
            add(errorLabel, "wrap, gapbottom 15");
        }

        // connection string (selectable so it can be copied from here too)
        JTextArea textArea = new JTextArea(connectionString);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(QR_CODE_SIZE, 60));
        add(scrollPane, "growx, wrap");
    }

    private BufferedImage generateQrCode(String content) throws WriterException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE, hints);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);
    }
}
