package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DeviceRowSorter extends TableRowSorter<TableModel> {
    private static final Logger log = LoggerFactory.getLogger(DeviceRowSorter.class);

    private final DeviceRowFilter deviceRowFilter;

    public DeviceRowSorter(TableModel model) {
        super(model);
        deviceRowFilter = new DeviceRowFilter();

        // default sort
        List<SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(0, SortOrder.ASCENDING));
        setSortKeys(sortKeys);
    }

    @Override
    protected boolean useToString(int c) {
        return false;
    }

    @Override
    public Comparator<?> getComparator(int c) {
        return (o1, o2) -> {
            Device d1 = (Device) o1;
            Device d2 = (Device) o2;

            //log.trace("getComparator: d1:{} = {}, d2:{} = {}", d1.serial, d1.isOnline, d2.serial, d2.isOnline);
            // always sort offline devices at the bottom
            if (d1.isOnline != d2.isOnline) {
                if (d1.isOnline) return -1;
                else return 1;
            }

            DeviceTableModel model = (DeviceTableModel) getModel();
            DeviceTableModel.Columns columnType = model.getColumnType(c);
            if (columnType == DeviceTableModel.Columns.BATTERY) {
                return Integer.compare(d1.batteryLevel, d2.batteryLevel);
            }
            String value1 = model.deviceValue(d1, c);
            String value2 = model.deviceValue(d2, c);
            int rc = TextUtils.compareToIgnoreCase(value1, value2);
            return rc;
        };
    }

    public void setFilterText(String text) {
        deviceRowFilter.setFilterText(text);
    }

    @Override
    public RowFilter<? super TableModel, ? super Integer> getRowFilter() {
        return deviceRowFilter;
    }

    private class DeviceRowFilter extends RowFilter<Object, Object> {
        private String filterText;

        @Override
        public boolean include(Entry<?, ?> entry) {
            if (filterText == null) return true;

            DeviceTableModel model = (DeviceTableModel) getModel();
            for (int i = entry.getValueCount() - 1; i >= 0; i--) {
                Device device = (Device) entry.getValue(i);
                String value = model.deviceValue(device, i);
                if (TextUtils.containsIgnoreCase(value, filterText)) {
                    return true;
                }
            }
            return false;
        }

        public void setFilterText(String text) {
            filterText = text;
            sort();
        }
    }

}