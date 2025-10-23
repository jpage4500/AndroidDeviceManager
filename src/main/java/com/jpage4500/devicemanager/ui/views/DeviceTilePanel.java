package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Individual device tile component for grid view
 */
public class DeviceTilePanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(DeviceTilePanel.class);

    private static final int DEFAULT_TILE_WIDTH = 200;
    private static final int DEFAULT_TILE_HEIGHT = 150;
    private static final int THUMBNAIL_HEIGHT = 100;
    private static final int BATTERY_ICON_SIZE = 20;

    private Device device;
    private JLabel nameLabel;
    private JLabel serialLabel;
    private JLabel batteryLabel;
    private boolean isSelected;

    private BufferedImage backgroundImage;

    public DeviceTilePanel(Device device) {
        this.device = device;
        initializeComponents();
        updateDeviceInfo();
    }

    private void initializeComponents() {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(DEFAULT_TILE_WIDTH, DEFAULT_TILE_HEIGHT));

        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));
        setBackground(Color.WHITE);

        // Battery icon (top-left)
        batteryLabel = new JLabel();
        batteryLabel.setOpaque(false);
        JPanel topLeftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        topLeftPanel.setOpaque(false);
        topLeftPanel.add(batteryLabel);
        add(topLeftPanel, BorderLayout.NORTH);

        // Device name (bottom center, size to fit text, semi-transparent background)
        nameLabel = new JLabel();
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setOpaque(true);
        nameLabel.setBackground(new Color(0, 0, 0, 128));
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setVerticalAlignment(SwingConstants.BOTTOM);

        serialLabel = new JLabel();
        serialLabel.setFont(serialLabel.getFont().deriveFont(Font.PLAIN));
        serialLabel.setForeground(Color.WHITE);
        serialLabel.setOpaque(true);
        serialLabel.setBackground(new Color(0, 0, 0, 128));
        serialLabel.setHorizontalAlignment(SwingConstants.CENTER);
        serialLabel.setVerticalAlignment(SwingConstants.BOTTOM);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setOpaque(false);
        // Center the labels horizontally
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        serialLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottomPanel.add(nameLabel);
        bottomPanel.add(serialLabel);
        add(bottomPanel, BorderLayout.SOUTH);

        // Add busy indicator overlay (centered)
        setupBusyIndicator();
    }

    private void setupBusyIndicator() {
        // Add a busy indicator that can be shown/hidden
        JLabel busyLabel = new JLabel();
        busyLabel.setIcon(UiUtils.getImageIcon("status_busy.png", 24));
        busyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        busyLabel.setVerticalAlignment(SwingConstants.CENTER);
        busyLabel.setOpaque(true);
        busyLabel.setBackground(new Color(0, 0, 0, 128));
        busyLabel.setVisible(false);
        busyLabel.setName("busyIndicator");
        add(busyLabel, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (backgroundImage != null) {
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            float imgRatio = (float) backgroundImage.getWidth() / backgroundImage.getHeight();
            float panelRatio = (float) panelWidth / panelHeight;
            int drawWidth, drawHeight;
            if (imgRatio > panelRatio) {
                drawHeight = panelHeight;
                drawWidth = (int) (panelHeight * imgRatio);
            } else {
                drawWidth = panelWidth;
                drawHeight = (int) (panelWidth / imgRatio);
            }
            int x = (panelWidth - drawWidth) / 2;
            int y = (panelHeight - drawHeight) / 2;
            g.drawImage(backgroundImage, x, y, drawWidth, drawHeight, null);
        }
    }

    public void updateDeviceInfo() {
        if (device == null) return;
        SwingUtilities.invokeLater(() -> {
            updateThumbnail();
            // Set name and serial in separate labels
            String displayName = TextUtils.firstValid(device.nickname, device.getProperty(Device.PROP_MODEL));
            boolean hasDisplayName = TextUtils.notEmpty(displayName);
            nameLabel.setVisible(hasDisplayName);
            if (hasDisplayName) nameLabel.setText(displayName);
            serialLabel.setVisible(device.serial != null);
            serialLabel.setText(device.serial);
            updateBatteryIndicator();
            updateBusyState();
            updateSelectionState();
        });
    }

    private void updateThumbnail() {
        BufferedImage thumbnail = null;

        // Try to use preview image if available
        if (device.previewImage != null) {
            thumbnail = device.previewImage;
            log.debug("updateThumbnail: Using preview image {}x{}", thumbnail.getWidth(), thumbnail.getHeight());
        } else {
            // Use device icon as placeholder
            thumbnail = UiUtils.getImage("android.png", DEFAULT_TILE_WIDTH - 20, THUMBNAIL_HEIGHT - 20);
            log.debug("updateThumbnail: Using placeholder icon");
        }

        if (thumbnail != null) {
            backgroundImage = thumbnail;
            repaint();
        } else {
            backgroundImage = null;
            repaint();
        }
    }

    private void updateBatteryIndicator() {
        if (device.batteryLevel != null) {
            String level = null;
            if (device.batteryLevel > 95) level = "battery_level4.png";
            else if (device.batteryLevel > 50) level = "battery_level3.png";
            else if (device.batteryLevel > 25) level = "battery_level2.png";
            else level = "battery_level1.png";

            boolean isCharging = (device.powerStatus != Device.PowerStatus.POWER_NONE);

            // Reuse the charging icon logic from DeviceCellRenderer
            Icon batteryIcon = getChargingIcon(level, isCharging);
            batteryLabel.setIcon(batteryIcon);
            batteryLabel.setVisible(true);
        } else {
            batteryLabel.setVisible(false);
        }
    }

    private Icon getChargingIcon(String level, boolean isCharging) {
        if (level == null) return null;

        Icon levelIcon = UiUtils.getImageIcon(level, BATTERY_ICON_SIZE);
        if (isCharging) {
            Icon chargingIcon = UiUtils.getImageIcon("charging.png", BATTERY_ICON_SIZE);
            return new ComboIcon(levelIcon, chargingIcon);
        } else {
            return levelIcon;
        }
    }

    private void updateBusyState() {
        Component busyIndicator = null;
        for (Component comp : getComponents()) {
            if ("busyIndicator".equals(comp.getName())) {
                busyIndicator = comp;
                break;
            }
        }

        if (busyIndicator != null) {
            busyIndicator.setVisible(device.isBusy());
        }
    }

    private void updateSelectionState() {
        if (isSelected) {
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLUE, 2),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
            ));
            setBackground(new Color(240, 248, 255)); // Light blue
        } else {
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
            ));
            setBackground(Color.WHITE);
        }
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
        updateSelectionState();
    }

    public boolean isSelected() {
        return isSelected;
    }

    public Device getDevice() {
        return device;
    }

    public void setTileSize(int width, int height) {
        setPreferredSize(new Dimension(width, height));
        revalidate();
        repaint();
    }

    public void requestPreviewUpdate() {
        if (device != null && device.isOnline && !device.isBusy()) {
            // Check if preview is stale (older than 5 seconds)
            long now = System.currentTimeMillis();
            if (device.previewTimestamp == null || (now - device.previewTimestamp) > 5000) {
                // Request preview update from DeviceManager
                firePropertyChange("requestPreview", null, device);
            }
        }
    }

    public void refreshPreview() {
        // Force refresh the thumbnail when preview image is updated
        SwingUtilities.invokeLater(() -> {
            updateThumbnail();
            repaint();
        });
    }

    /**
     * Simple icon combiner for battery + charging overlay
     */
    private static class ComboIcon implements Icon {
        private final Icon baseIcon;
        private final Icon overlayIcon;

        public ComboIcon(Icon baseIcon, Icon overlayIcon) {
            this.baseIcon = baseIcon;
            this.overlayIcon = overlayIcon;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (baseIcon != null) {
                baseIcon.paintIcon(c, g, x, y);
            }
            if (overlayIcon != null) {
                // Paint overlay in bottom-right corner
                int overlayX = x + getIconWidth() - overlayIcon.getIconWidth();
                int overlayY = y + getIconHeight() - overlayIcon.getIconHeight();
                overlayIcon.paintIcon(c, g, overlayX, overlayY);
            }
        }

        @Override
        public int getIconWidth() {
            return baseIcon != null ? baseIcon.getIconWidth() : 0;
        }

        @Override
        public int getIconHeight() {
            return baseIcon != null ? baseIcon.getIconHeight() : 0;
        }
    }
}
