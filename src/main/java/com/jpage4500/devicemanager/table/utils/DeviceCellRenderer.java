package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.ui.views.ComboIcon;
import com.jpage4500.devicemanager.utils.Colors;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class DeviceCellRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(DeviceCellRenderer.class);

    private final Icon statusOfflineIcon;
    private final Icon statusOnlineIcon;
    private final Icon statusBusyIcon;
    private final Icon statusNotReadyIcon;
    private final Map<String, Icon> chargingIconMap;

    public DeviceCellRenderer() {
        chargingIconMap = new HashMap<>();

        setOpaque(true);
        UiUtils.setEmptyBorder(this, 5, 5);

        BufferedImage image = UiUtils.getImage("device_status.png", 20, 20);

        BufferedImage offlineImage = UiUtils.replaceColor(image, Color.GRAY);
        statusOfflineIcon = new ImageIcon(offlineImage);

        BufferedImage onlineImage = UiUtils.replaceColor(image, Colors.COLOR_ONLINE);
        statusOnlineIcon = new ImageIcon(onlineImage);

        BufferedImage busyImage = UiUtils.replaceColor(image, Colors.COLOR_BUSY);
        statusBusyIcon = new ImageIcon(busyImage);

        BufferedImage notReadyImage = UiUtils.replaceColor(image, Colors.COLOR_NOT_READY);
        statusNotReadyIcon = new ImageIcon(notReadyImage);
    }

    /**
     * get or create and cache an icon made up of battery level and charging status
     */
    private Icon getChargingIcon(String level, boolean isCharging) {
        if (level == null) return null;
        String key = level + "-" + isCharging;
        Icon icon = chargingIconMap.get(key);
        if (icon == null) {
            // create overlay icon
            Icon levelIcon = UiUtils.getImageIcon(level, 20);
            if (isCharging) {
                Icon chargingIcon = UiUtils.getImageIcon("charging.png", 20);
                icon = new ComboIcon(levelIcon, chargingIcon);
            } else {
                // use as-is
                icon = levelIcon;
            }
            chargingIconMap.put(key, icon);
        }
        return icon;
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        Device device = (Device) object;
        DeviceTableModel model = (DeviceTableModel) table.getModel();
        // convert table column to model column
        column = table.convertColumnIndexToModel(column);
        DeviceTableModel.Columns columnType = model.getColumnType(column);

        Icon icon = null;
        String text = null;
        int align = SwingConstants.LEFT;

        if (columnType != null) {
            switch (columnType) {
                case BATTERY:
                    String level = null;
                    if (device.batteryLevel != null) {
                        if (device.batteryLevel > 95) level = "battery_level4.png";
                        else if (device.batteryLevel > 50) level = "battery_level3.png";
                        else if (device.batteryLevel > 25) level = "battery_level2.png";
                        else level = "battery_level1.png";
                    }
                    boolean isCharging = (device.powerStatus != Device.PowerStatus.POWER_NONE);
                    icon = getChargingIcon(level, isCharging);
                    text = ""; // no text just icon
                    break;
                case FREE:
                    align = SwingConstants.RIGHT;
                    break;
                case NAME:
                    if (device.busyCounter.get() > 0) {
                        icon = statusBusyIcon;
                    } else if (device.isOnline) {
                        if (!device.isBooted) icon = statusNotReadyIcon;
                        else icon = statusOnlineIcon;
                    } else {
                        icon = statusOfflineIcon;
                    }
            }
        }

        if (text == null) text = model.deviceValue(device, column);

        setHorizontalAlignment(align);
        setIcon(icon);
        setText(text);

        boolean isTableFocused = table.hasFocus();
        Color textColor = isSelected && isTableFocused ? Color.WHITE : Color.BLACK;
        Color backgroundColor = isSelected ? table.getSelectionBackground() : table.getBackground();
        if (!device.isOnline) {
            textColor = isSelected && isTableFocused ? Color.WHITE : Color.GRAY;
            //backgroundColor = isSelected ? Color.DARK_GRAY : Color.LIGHT_GRAY;
        }

        setForeground(textColor);
        setBackground(backgroundColor);

        return this;
    }
}