import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public class DeviceTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(DeviceTableModel.class);

    private List<Device> deviceList;

    public enum Columns {
        SERIAL,
        MODEL,
        PHONE,
        IMEI
    }

    public DeviceTableModel() {
        deviceList = new ArrayList<>();

        for (int i = 1; i < 100; i++) {
            Device d = new Device();
            d.serial = "2lkjsdfksjf";
            d.model = "Pixel";
            d.phone = "12322323";
            deviceList.add(d);
        }
    }

    public void setDeviceList(List<Device> deviceList) {
        log.debug("setDeviceList: {}", deviceList.size());
        this.deviceList.clear();
        this.deviceList.addAll(deviceList);

        fireTableDataChanged();
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
        if (col == Columns.SERIAL.ordinal()) {
            return device.serial;
        } else if (col == Columns.MODEL.ordinal()) {
            return device.model;
        } else if (col == Columns.PHONE.ordinal()) {
            return device.phone;
        } else if (col == Columns.IMEI.ordinal()) {
            return device.imei;
        } else {
            return "";
        }
    }
}