package com.jpage4500.devicemanager.viewmodel;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public class DeviceTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(DeviceTableModel.class);

    private final List<Device> deviceList;

    public enum Columns {
        SERIAL,
        MODEL,
        PHONE,
        IMEI,
        CUSTOM1,
        CUSTOM2,
        STATUS,
    }

    public DeviceTableModel() {
        deviceList = new ArrayList<>();
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

    public int getColumnCount() {
        return Columns.values().length;
    }

    public String getColumnName(int i) {
        return Columns.values()[i].name();
    }

    public int getRowCount() {
        return deviceList.size();
    }

    public Object getValueAt(int row, int col) {
        Columns[] columns = Columns.values();
        if (row >= deviceList.size() || col >= columns.length) return null;

        Device device = deviceList.get(row);
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
    }
}