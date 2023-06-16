package com.jpage4500.devicemanager.viewmodel;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.table.AbstractTableModel;

public class DeviceTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(DeviceTableModel.class);

    private final List<Device> deviceList;
    private final List<String> appList;

    public enum Columns {
        SERIAL,
        MODEL,
        PHONE,
        IMEI,
        FREE,
        CUSTOM1,
        CUSTOM2,
        STATUS,
        ;
    }

    public DeviceTableModel() {
        deviceList = new ArrayList<>();
        appList = new ArrayList<>();
    }

    public void setDeviceList(List<Device> deviceList) {
        this.deviceList.clear();
        this.deviceList.addAll(deviceList);

        fireTableDataChanged();
    }

    /**
     * get device for given row
     * NOTE: make sure you use table.convertRowIndexToModel() first
     */
    public Device getDeviceAtRow(int row) {
        if (deviceList.size() > row) {
            return deviceList.get(row);
        }
        return null;
    }

    public void updateRowForDevice(Device device) {
        if (device == null) return;
        for (int row = 0; row < deviceList.size(); row++) {
            Device d = deviceList.get(row);
            if (TextUtils.equals(d.serial, device.serial)) {
                fireTableRowsUpdated(row, row);
                break;
            }
        }
    }

    public void setAppList(List<String> appList) {
        if (this.appList.equals(appList)) return;
        this.appList.clear();
        this.appList.addAll(appList);
        for (Device device : deviceList) {
            device.hasFetchedDetails = false;
        }

        fireTableStructureChanged();
    }

    public int getColumnCount() {
        return Columns.values().length + appList.size();
    }

    public String getColumnName(int i) {
        Columns[] columns = Columns.values();
        if (i < columns.length) {
            Columns colType = columns[i];
            return colType.name();
        } else {
            String appName = appList.get(i - columns.length);
            String[] split = appName.split("\\.");
            if (split.length >= 1) {
                return split[split.length - 1].toUpperCase(Locale.ROOT);
            } else {
                return split[0];
            }
        }
    }

    public int getRowCount() {
        return deviceList.size();
    }

    public Object getValueAt(int row, int col) {
        if (row >= deviceList.size()) return null;
        else if (col >= getColumnCount()) return null;

        Device device = deviceList.get(row);
        Columns[] columns = Columns.values();
        if (col < columns.length) {
            Columns colType = columns[col];
            switch (colType) {
                case SERIAL:
                    return device.serial;
                case MODEL:
                    return device.model;
                case PHONE:
                    return device.phone;
                case IMEI:
                    return device.imei;
                case FREE: {
                    return device.freeSpace;
                    //if (device.freeSpace != null) {
                    //    try {
                    //        long freeSpace = Long.parseLong(device.freeSpace);
                    //        return FileUtils.bytesToDisplayString(freeSpace * 1024);
                    //    } catch (Exception e) {
                    //        log.debug("getValueAt: Exception: {}: {}", device.freeSpace, e.getMessage());
                    //    }
                    //    return null;
                    //}
                }
                case CUSTOM1:
                    return device.custom1;
                case CUSTOM2:
                    return device.custom2;
                case STATUS:
                    return device.status;
                default:
                    log.debug("getValueAt: ERROR: {}", colType);
                    return "";
            }
        } else {
            // custom app version
            if (device.customAppList != null) {
                String appName = appList.get(col - columns.length);
                return device.customAppList.get(appName);
            } else {
                return null;
            }
        }
    }

}