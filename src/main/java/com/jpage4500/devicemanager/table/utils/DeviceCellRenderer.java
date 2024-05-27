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

    public DeviceCellRenderer() {
        setOpaque(true);
        Border border = new EmptyBorder(0, 5, 0, 0);
        setBorder(border);

        BufferedImage image = UiUtils.getImage("status_offline.png", 20, 20, Color.GRAY);
        statusOfflineIcon = new ImageIcon(image);

        image = UiUtils.getImage("status_online.png", 20, 20, new Color(24, 134, 0));
        statusOnlineIcon = new ImageIcon(image);

        image = UiUtils.getImage("status_busy.png", 20, 20, new Color(251, 109, 8));
        statusBusyIcon = new ImageIcon(image);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        Device device = (Device) object;
        DeviceTableModel model = (DeviceTableModel) table.getModel();

        DeviceTableModel.Columns columnType = model.getColumnType(column);
        if (columnType == DeviceTableModel.Columns.FREE) {
            setHorizontalAlignment(SwingConstants.RIGHT);
        } else {
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        Icon icon = null;
        if (columnType == DeviceTableModel.Columns.NAME) {
            if (device.isBusy) {
                icon = statusBusyIcon;
            } else if (device.isOnline) {
                icon = statusOnlineIcon;
            } else {
                icon = statusOfflineIcon;
            }
        }
        String text = model.deviceValue(device, column);
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