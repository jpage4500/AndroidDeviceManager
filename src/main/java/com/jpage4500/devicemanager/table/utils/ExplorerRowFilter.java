package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.utils.TextUtils;
import se.vidstige.jadb.RemoteFile;

import javax.swing.*;

public class ExplorerRowFilter extends RowFilter<Object, Object> {
    public String searchFor;

    @Override
    public boolean include(Entry<?, ?> entry) {
        if (searchFor == null) return true;

        for (int i = entry.getValueCount() - 1; i >= 0; i--) {
            Object value = entry.getValue(i);
            if (value instanceof RemoteFile) {
                RemoteFile file = (RemoteFile) value;
                String name = file.getName();
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
