package com.jpage4500.devicemanager.viewmodel;

import com.jpage4500.devicemanager.data.Device;

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
        // TODO: this checks only that the same devices are in the list (serialNumber)
        if (this.deviceList.equals(deviceList)) return;
        this.deviceList.clear();
        this.deviceList.addAll(deviceList);

        fireTableDataChanged();
    }

    public Device getDeviceAtRow(int row) {
        if (deviceList.size() > row) {
            return deviceList.get(row);
        }
        return null;
    }

    public void updateDevice(Device device) {
        if (device == null) return;
        int row = deviceList.indexOf(device);
        if (row >= 0) {
            fireTableRowsUpdated(row, row);
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