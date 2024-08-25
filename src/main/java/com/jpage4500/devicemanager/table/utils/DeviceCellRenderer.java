package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;

public class DeviceCellRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(DeviceCellRenderer.class);

    private final Icon statusOfflineIcon;
    private final Icon statusOnlineIcon;
    private final Icon statusBusyIcon;

    private final Icon chargingIcon;
    private final Icon batteryLevel4;
    private final Icon batteryLevel3;
    private final Icon batteryLevel2;
    private final Icon batteryLevel1;

    public DeviceCellRenderer() {
        setOpaque(true);
        Border border = new EmptyBorder(0, 5, 0, 0);
        setBorder(border);

        BufferedImage image = UiUtils.getImage("device_status.png", 20, 20);

        BufferedImage offlineImage = UiUtils.replaceColor(image, Color.GRAY);
        statusOfflineIcon = new ImageIcon(offlineImage);

        BufferedImage onlineImage = UiUtils.replaceColor(image, new Color(24, 134, 0));
        statusOnlineIcon = new ImageIcon(onlineImage);

        BufferedImage busyImage = UiUtils.replaceColor(image, new Color(251, 109, 8));
        statusBusyIcon = new ImageIcon(busyImage);

        chargingIcon = UiUtils.getImageIcon("charging.png", 20);
        batteryLevel4 = UiUtils.getImageIcon("battery_level4.png", 20);
        batteryLevel3 = UiUtils.getImageIcon("battery_level3.png", 20);
        batteryLevel2 = UiUtils.getImageIcon("battery_level2.png", 20);
        batteryLevel1 = UiUtils.getImageIcon("battery_level1.png", 20);
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
                    if (device.batteryLevel != null) {
                        if (device.batteryLevel > 95) icon = batteryLevel4;
                        else if (device.batteryLevel > 50) icon = batteryLevel3;
                        else if (device.batteryLevel > 25) icon = batteryLevel2;
                        else icon = batteryLevel1;
                    } else {
                        icon = chargingIcon;
                    }
                    text = ""; // no text just icon
                    break;
                case FREE:
                    align = SwingConstants.RIGHT;
                    break;
                case NAME:
                    if (device.busyCounter.get() > 0) {
                        icon = statusBusyIcon;
                    } else if (device.isOnline) {
                        icon = statusOnlineIcon;
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