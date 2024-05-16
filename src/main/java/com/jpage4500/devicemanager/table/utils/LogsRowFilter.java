package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

public class LogsRowFilter extends RowFilter<Object, Object> {
    private static final Logger log = LoggerFactory.getLogger(LogsRowFilter.class);

    private String searchFor;

    public void setFilter(String searchFor) {
        this.searchFor = searchFor;
    }

    @Override
    public boolean include(Entry<?, ?> entry) {
        if (TextUtils.isEmpty(searchFor)) return true;

        LogEntry logEntry = (LogEntry) entry.getValue(0);
        if (TextUtils.containsIgnoreCase(logEntry.message, searchFor)) return true;

        return false;
    }
}
