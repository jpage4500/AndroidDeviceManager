package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.UiUtils;
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
    private JLabel thumbnailLabel;
    private JLabel nameLabel;
    private JLabel batteryLabel;
    private boolean isSelected;
    
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
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        setBackground(Color.WHITE);
        
        // Thumbnail area
        thumbnailLabel = new JLabel();
        thumbnailLabel.setPreferredSize(new Dimension(DEFAULT_TILE_WIDTH - 10, THUMBNAIL_HEIGHT));
        thumbnailLabel.setHorizontalAlignment(SwingConstants.CENTER);
        thumbnailLabel.setVerticalAlignment(SwingConstants.CENTER);
        thumbnailLabel.setOpaque(true);
        thumbnailLabel.setBackground(Color.LIGHT_GRAY);
        add(thumbnailLabel, BorderLayout.CENTER);
        
        // Bottom panel with name and battery
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        
        // Device name
        nameLabel = new JLabel();
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        bottomPanel.add(nameLabel, BorderLayout.CENTER);
        
        // Battery indicator (bottom-left overlay)
        batteryLabel = new JLabel();
        batteryLabel.setPreferredSize(new Dimension(BATTERY_ICON_SIZE, BATTERY_ICON_SIZE));
        bottomPanel.add(batteryLabel, BorderLayout.WEST);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Add busy indicator overlay
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
    
    public void updateDeviceInfo() {
        if (device == null) return;
        
        SwingUtilities.invokeLater(() -> {
            // Update thumbnail
            updateThumbnail();
            
            // Update device name
            String displayName = device.getDisplayName();
            if (displayName.length() > 25) {
                displayName = displayName.substring(0, 22) + "...";
            }
            nameLabel.setText(displayName);
            
            // Update battery indicator
            updateBatteryIndicator();
            
            // Update busy state
            updateBusyState();
            
            // Update selection state
            updateSelectionState();
        });
    }
    
    private void updateThumbnail() {
        BufferedImage thumbnail = null;
        
        // Try to use preview image if available
        if (device.previewImage != null) {
            thumbnail = device.previewImage;
        } else {
            // Use device icon as placeholder
            thumbnail = UiUtils.getImage("android.png", DEFAULT_TILE_WIDTH - 20, THUMBNAIL_HEIGHT - 20);
        }
        
        if (thumbnail != null) {
            thumbnailLabel.setIcon(new ImageIcon(thumbnail));
        } else {
            thumbnailLabel.setIcon(null);
            thumbnailLabel.setText("No Image");
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
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
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
        thumbnailLabel.setPreferredSize(new Dimension(width - 10, THUMBNAIL_HEIGHT));
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
