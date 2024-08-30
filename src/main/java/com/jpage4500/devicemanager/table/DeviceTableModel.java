package com.jpage4500.devicemanager.table;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class DeviceTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(DeviceTableModel.class);

    private final List<Device> deviceList;
    private final List<String> appList;
    private Columns[] visibleColumns;

    public enum Columns {
        NAME("Name"),
        SERIAL("Serial"),
        MODEL("Model"),
        PHONE("Phone"),
        IMEI("IMEI"),
        BATTERY("Battery"),
        FREE("Free"),
        CUSTOM1("Custom 1"),
        CUSTOM2("Custom 2"),
        ;
        String desc;

        Columns(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }

    public DeviceTableModel() {
        deviceList = new ArrayList<>();
        appList = new ArrayList<>();
        setHiddenColumns(null);
    }

    public void setDeviceList(List<Device> deviceList) {
        if (this.deviceList.equals(deviceList)) return;
        this.deviceList.clear();
        this.deviceList.addAll(deviceList);
        fireTableDataChanged();
    }

    public void setHiddenColumns(List<String> hiddenColumns) {
        Columns[] columns = Columns.values();
        int numColumns = columns.length;
        int numHiddenColumns = hiddenColumns == null ? 0 : hiddenColumns.size();
        if (numHiddenColumns > numColumns) numHiddenColumns = 0;
        int numVisible = numColumns - numHiddenColumns;
        visibleColumns = new Columns[numVisible];

        int index = 0;
        for (Columns column : columns) {
            if (hiddenColumns == null || !hiddenColumns.contains(column.name())) {
                visibleColumns[index] = column;
                index++;
            }
        }
        fireTableStructureChanged();
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

    /**
     * return one of the predefined columns or NULL if this is an app version column
     */
    public Columns getColumnType(int colIndex) {
        if (colIndex >= 0 && colIndex < visibleColumns.length) {
            return visibleColumns[colIndex];
        }
        return null;
    }

    public void updateDevice(Device device) {
        int row = getRowForDevice(device);
        if (row >= 0) {
            fireTableRowsUpdated(row, row);
        } else {
            // TODO: make sure this logic works
            deviceList.add(device);
            int lastRow = deviceList.size() - 1;
            fireTableRowsInserted(lastRow, lastRow);
        }
    }

    public void removeDevice(Device device) {
        int row = getRowForDevice(device);
        if (row >= 0) {
            deviceList.remove(row);
            fireTableRowsDeleted(row, row);
        }
    }

    public int getRowForDevice(Device device) {
        if (device == null) return -1;
        for (int row = 0; row < deviceList.size(); row++) {
            Device d = deviceList.get(row);
            if (TextUtils.equals(d.serial, device.serial)) {
                return row;
            }
        }
        return -1;
    }

    public void setAppList(List<String> appList) {
        if (this.appList.equals(appList)) return;
        this.appList.clear();
        this.appList.addAll(appList);

        // update columns
        fireTableStructureChanged();
    }

    public int getColumnCount() {
        return visibleColumns.length + appList.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return Device.class;
    }

    public String getColumnName(int i) {
        if (i < visibleColumns.length) {
            Columns colType = visibleColumns[i];
            return colType.toString();
        } else {
            return appList.get(i - visibleColumns.length);
        }
    }

    public int getRowCount() {
        return deviceList.size();
    }

    public Object getValueAt(int row, int col) {
        return deviceList.get(row);
    }

    public String deviceValue(Device device, int column) {
        if (column >= 0 && column < visibleColumns.length) {
            DeviceTableModel.Columns colType = visibleColumns[column];
            return switch (colType) {
                case SERIAL -> device.serial;
                case NAME -> TextUtils.firstValid(device.nickname, device.getProperty(Device.PROP_MODEL));
                case MODEL -> device.getProperty(Device.PROP_MODEL);
                case PHONE -> device.phone;
                case IMEI -> device.imei;
                case FREE -> FileUtils.bytesToGigDisplayString(device.freeSpace);
                case BATTERY -> {
                    String level = device.batteryLevel != null ? String.valueOf(device.batteryLevel) : "";
                    if (device.powerStatus != Device.PowerStatus.POWER_NONE) level += " - " + device.powerStatus;
                    yield level;
                }
                case CUSTOM1 -> device.getCustomProperty(Device.CUST_PROP_1);
                case CUSTOM2 -> device.getCustomProperty(Device.CUST_PROP_2);
            };
        } else {
            // custom app version
            if (device.customAppVersionList != null) {
                String appName = appList.get(column - visibleColumns.length);
                return device.customAppVersionList.get(appName);
            } else {
                return null;
            }
        }
    }

}