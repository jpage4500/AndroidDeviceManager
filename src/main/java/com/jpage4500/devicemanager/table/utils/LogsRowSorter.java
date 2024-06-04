package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.LogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

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

    public void setFilter(LogFilter logFilter) {
        logsRowFilter.setFilter(logFilter);
    }

    public LogFilter getFilter() {
        return logsRowFilter.logFilter;
    }

    @Override
    public RowFilter<? super TableModel, ? super Integer> getRowFilter() {
        return logsRowFilter;
    }

    private class LogsRowFilter extends RowFilter<Object, Object> {
        private LogFilter logFilter;

        @Override
        public boolean include(Entry<?, ?> entry) {
            if (logFilter == null) return true;

            LogEntry logEntry = (LogEntry) entry.getValue(0);
            return logFilter.isMatch(logEntry);
        }

        public void setFilter(LogFilter logFilter) {
            this.logFilter = logFilter;
            sort();
        }
    }

}