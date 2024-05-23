package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.utils.TextUtils;

import javax.swing.*;

public class ExplorerRowFilter extends RowFilter<Object, Object> {
    public String searchFor;

    @Override
    public boolean include(Entry<?, ?> entry) {
        if (searchFor == null) return true;

        for (int i = entry.getValueCount() - 1; i >= 0; i--) {
            Object value = entry.getValue(i);
            if (value instanceof DeviceFile) {
                DeviceFile file = (DeviceFile) value;
                String name = file.name;
                if (TextUtils.containsIgnoreCase(name, searchFor)) return true;
                else if (TextUtils.equals(name, "..")) return true;
            } else {
                String stringValue = entry.getStringValue(i);
                if (TextUtils.containsIgnoreCase(stringValue, searchFor)) return true;
            }
        }
        return false;
    }
}
