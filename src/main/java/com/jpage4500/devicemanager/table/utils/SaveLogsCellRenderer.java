package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.SaveLogEntry;
import com.jpage4500.devicemanager.table.SaveLogsTableModel;
import com.jpage4500.devicemanager.utils.Colors;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;

public class SaveLogsCellRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(SaveLogsCellRenderer.class);

    private final Icon statusOfflineIcon;
    private final Icon statusOnlineIcon;
    private final Icon statusBusyIcon;
    private final Icon statusNotReadyIcon;

    public SaveLogsCellRenderer() {
        setOpaque(true);

        UiUtils.setEmptyBorder(this, 10, 10);

        BufferedImage image = UiUtils.getImage("device_local.png", UiUtils.IMG_SIZE_ICON, UiUtils.IMG_SIZE_ICON);

        BufferedImage offlineImage = UiUtils.replaceColor(image, Color.GRAY);
        statusOfflineIcon = new ImageIcon(offlineImage);

        BufferedImage onlineImage = UiUtils.replaceColor(image, Colors.COLOR_ONLINE);
        statusOnlineIcon = new ImageIcon(onlineImage);

        BufferedImage busyImage = UiUtils.replaceColor(image, Colors.COLOR_BUSY);
        statusBusyIcon = new ImageIcon(busyImage);

        BufferedImage notReadyImage = UiUtils.replaceColor(image, Colors.COLOR_NOT_READY);
        statusNotReadyIcon = new ImageIcon(notReadyImage);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        SaveLogEntry entry = (SaveLogEntry) object;
        // convert table column to model column
        column = table.convertColumnIndexToModel(column);

        SaveLogsTableModel.Columns col = SaveLogsTableModel.Columns.values()[column];
        Icon icon = null;
        String text = null;
        int align = SwingConstants.LEFT;
        switch (col) {
            case NAME:
                text = entry.device.nickname;
                if (entry.device.isBusy()) {
                    icon = statusBusyIcon;
                } else if (entry.device.isOnline) {
                    if (!entry.device.isBooted) icon = statusNotReadyIcon;
                    else icon = statusOnlineIcon;
                } else {
                    icon = statusOfflineIcon;
                }
                break;
            case SERIAL:
                text = entry.device.serial;
                break;
            case SIZE:
                // right-align size column
                align = SwingConstants.RIGHT;
                if (entry.size > 0) {
                    text = FileUtils.bytesToDisplayString(entry.size);
                } else {
                    text = "-";
                }
                break;
        }

        boolean isTableFocused = table.hasFocus();
        Color textColor = isSelected && isTableFocused ? Color.WHITE : Color.BLACK;
        Color backgroundColor = isSelected ? table.getSelectionBackground() : table.getBackground();

        setForeground(textColor);
        setBackground(backgroundColor);

        setIcon(icon);
        setText(text);
        setHorizontalAlignment(align);

        return this;
    }
}