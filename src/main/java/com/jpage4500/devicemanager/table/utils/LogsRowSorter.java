package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.Comparator;

public class LogsRowSorter extends TableRowSorter<TableModel> {
    private static final Logger log = LoggerFactory.getLogger(LogsRowSorter.class);

    private final LogsRowFilter logsRowFilter;

    public LogsRowSorter(TableModel model) {
        super(model);
        logsRowFilter = new LogsRowFilter();
    }

    @Override
    public boolean isSortable(int i) {
        return false;
    }

    @Override
    protected boolean useToString(int c) {
        return false;
    }

    public void setFilterText(String text) {
        logsRowFilter.setFilterText(text);
    }

    @Override
    public RowFilter<? super TableModel, ? super Integer> getRowFilter() {
        return logsRowFilter;
    }

    private class LogsRowFilter extends RowFilter<Object, Object> {
        private String filterText;

        @Override
        public boolean include(Entry<?, ?> entry) {
            if (filterText == null) return true;

            LogEntry logEntry = (LogEntry) entry.getValue(0);
            if (TextUtils.containsIgnoreCase(logEntry.message, filterText)) return true;

            return false;
        }

        public void setFilterText(String text) {
            filterText = text;
            sort();
        }
    }

}