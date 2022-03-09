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
        CUSTOM2
    }

    public DeviceTableModel() {
        deviceList = new ArrayList<>();
    }

    public void setDeviceList(List<Device> deviceList) {
        if (this.deviceList.equals(deviceList)) {
            log.debug("setDeviceList: SAME");
            return;
        }
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
        int row = deviceList.indexOf(device);
        if (row >= 0) {
            log.debug("updateDevice: updated row:{}", row);
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
        Device device = deviceList.get(row);
        Columns colType = Columns.values()[col];
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
            default:
                log.debug("getValueAt: ERROR: {}", colType);
                return "";
        }
    }
}