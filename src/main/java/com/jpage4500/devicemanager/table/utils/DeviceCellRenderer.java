package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.ui.views.ComboIcon;
import com.jpage4500.devicemanager.ui.views.IconTextField;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class DeviceCellRenderer extends IconTextField implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(DeviceCellRenderer.class);

    private enum DeviceState {
        OFFLINE,
        ONLINE,
        BUSY,
        REMOTE_OFFLINE,
        REMOTE_ONLINE,
        REMOTE_BUSY,
    }

    private final Map<String, Icon> deviceIconMap;

    // battery state icons
    private final Map<String, Icon> chargingIconMap;

    private Highlighter.HighlightPainter highlightPainter;
    private Highlighter.HighlightPainter highlightPainter2;
    private boolean isHighlighted = false;

    public DeviceCellRenderer() {
        chargingIconMap = new HashMap<>();
        deviceIconMap = new HashMap<>();

        setOpaque(true);
        UiUtils.setEmptyBorder(this, 5, 5);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        Device device = (Device) object;
        DeviceTableModel model = (DeviceTableModel) table.getModel();
        // convert table column to model column
        column = table.convertColumnIndexToModel(column);
        DeviceTableModel.Columns columnType = model.getColumnType(column);

        // hasFocus is for the cell only (not entire row)
        boolean isSelectedAndFocused = isSelected && table.hasFocus();

        Icon icon = null;
        String text = null;
        int align = SwingConstants.LEFT;

        if (columnType != null) {
            switch (columnType) {
                case BATTERY:
                    Icons level = null;
                    if (device.batteryLevel != null) {
                        if (device.batteryLevel > 95) level = Icons.BATTERY_LEVEL4;
                        else if (device.batteryLevel > 50) level = Icons.BATTERY_LEVEL3;
                        else if (device.batteryLevel > 25) level = Icons.BATTERY_LEVEL2;
                        else if (device.batteryLevel > 5) level = Icons.BATTERY_LEVEL1;
                        else level = Icons.BATTERY_LEVEL0;
                    }
                    boolean isCharging = (device.powerStatus != Device.PowerStatus.POWER_NONE);
                    icon = getChargingIcon(level, isCharging);
                    text = ""; // no text just icon
                    break;
                case FREE:
                    align = SwingConstants.RIGHT;
                    break;
                case NAME:
                    // show device status icon with optional remote indicator
                    icon = getDeviceIcon(device, isSelectedAndFocused);
                    text = model.deviceValue(device, column);
                    break;
            }
        }

        if (text == null) text = model.deviceValue(device, column);

        // modify true/false to icons (only for custom columns)
        if (columnType == null || columnType == DeviceTableModel.Columns.CUSTOM1 || columnType == DeviceTableModel.Columns.CUSTOM2) {
            if (TextUtils.equalsIgnoreCase(text, "true")) {
                text = "✅";
            } else if (TextUtils.equalsIgnoreCase(text, "false")) {
                text = "❌";
            }
        }

        setHorizontalAlignment(align);
        setIcon(icon);
        setText(text);

        Color textColor = isSelectedAndFocused ? Color.WHITE : Color.BLACK;
        Color backgroundColor = isSelected ? table.getSelectionBackground() : table.getBackground();
        if (!device.isOnline) {
            textColor = isSelectedAndFocused ? Color.WHITE : Color.GRAY;
        }

        int highlightStartPos = -1;
        String searchText = model.getSearchText();
        if (TextUtils.length(searchText) > 1 && text != null) {
            highlightStartPos = TextUtils.indexOfIgnoreCase(text, searchText);
        }

        Highlighter highlighter = getHighlighter();
        boolean doHighlight = highlightStartPos >= 0;
        if (doHighlight || isHighlighted) {
            // something changed..
            highlighter.removeAllHighlights();

            if (doHighlight) {
                isHighlighted = true;
                if (highlightPainter == null) {
                    highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
                    highlightPainter2 = new DefaultHighlighter.DefaultHighlightPainter(new Color(251, 109, 8));
                }
                Highlighter.HighlightPainter highlight = isSelectedAndFocused ? highlightPainter2 : highlightPainter;
                try {
                    highlighter.addHighlight(highlightStartPos, highlightStartPos + searchText.length(), highlight);
                } catch (BadLocationException e) {
                    log.error("BadLocationException: {}", e.getMessage());
                }
            }
        }
        setForeground(textColor);
        setBackground(backgroundColor);

        return this;
    }

    private Icon getDeviceIcon(Device device, boolean isSelected) {
        boolean isRemote = device.remoteConnection != null;
        boolean isBusy = device.getBusyCount() > 0;
        DeviceState state;
        Color color;
        if (!device.isOnline) {
            state = isRemote ? DeviceState.REMOTE_OFFLINE : DeviceState.OFFLINE;
            color = Colors.COLOR_OFFLINE;
        } else if (isBusy) {
            state = isRemote ? DeviceState.REMOTE_BUSY : DeviceState.BUSY;
            color = Colors.COLOR_BUSY;
        } else {
            state = isRemote ? DeviceState.REMOTE_ONLINE : DeviceState.ONLINE;
            color = Colors.COLOR_ONLINE;
        }
        String key = state + "-" + isSelected;
        Icon icon = deviceIconMap.get(key);
        if (icon == null) {
            // create icon
            Icons imageName = isRemote ? Icons.DEVICE_REMOTE : Icons.DEVICE_LOCAL;
            icon = UiUtils.getImageIcon(imageName, UiUtils.IMG_SIZE_ICON, UiUtils.IMG_SIZE_ICON, isSelected ? Color.WHITE : color);
            deviceIconMap.put(key, icon);
        }
        return icon;
    }

    /**
     * get or create and cache an icon made up of battery level and charging status
     */
    private Icon getChargingIcon(Icons level, boolean isCharging) {
        if (level == null) return null;
        String key = level + "-" + isCharging;
        Icon icon = chargingIconMap.get(key);
        if (icon == null) {
            // create overlay icon
            Icon levelIcon = UiUtils.getImageIcon(level, UiUtils.IMG_SIZE_ICON);
            if (isCharging) {
                Icon chargingIcon = UiUtils.getImageIcon(Icons.CHARGING, UiUtils.IMG_SIZE_ICON);
                icon = new ComboIcon(levelIcon, chargingIcon);
            } else {
                // use as-is
                icon = levelIcon;
            }
            chargingIconMap.put(key, icon);
        }
        return icon;
    }

}