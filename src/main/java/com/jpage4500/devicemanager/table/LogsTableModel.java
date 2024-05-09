package com.jpage4500.devicemanager.table;

import com.jpage4500.devicemanager.data.LogEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public class LogsTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(LogsTableModel.class);
    private static final int MAX_LINES = 50000;
    private static final int REMOVE_LINES = 10000;

    private List<LogEntry> logEntryList;
    private final static Object lock = new Object();

    public enum Columns {
        DATE,
        APP,
        TID,
        PID,
        LEVEL,
        MSG,
        ;
    }

    public LogsTableModel() {
        logEntryList = new ArrayList<>();
    }

    public void addLogEntry(List<LogEntry> logEntryList) {
        synchronized (lock) {
            this.logEntryList.addAll(logEntryList);
            checkSizeAndUpdate(logEntryList.size());
        }
    }

    public void addLogEntry(LogEntry logEntry) {
        synchronized (lock) {
            this.logEntryList.add(logEntry);
            checkSizeAndUpdate(1);
        }
    }

    private void checkSizeAndUpdate(int numAdded) {
        if (logEntryList.size() > MAX_LINES) {
            // remove top 20%
            int pos = logEntryList.size() - MAX_LINES - REMOVE_LINES;
            pos = Math.max(pos, 0);
            log.debug("checkSizeAndUpdate: truncating..");
            logEntryList = logEntryList.subList(pos, logEntryList.size());
            fireTableDataChanged();
        } else {
            int startPos = logEntryList.size() - numAdded;
            int endPos = logEntryList.size() - 1;
            fireTableRowsInserted(startPos, endPos);
        }
    }

    public int getColumnCount() {
        synchronized (lock) {
            return Columns.values().length + logEntryList.size();
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    public String getColumnName(int i) {
        Columns[] columns = Columns.values();
        if (i < columns.length) {
            Columns colType = columns[i];
            return colType.name();
        }
        return null;
    }

    public int getRowCount() {
        synchronized (lock) {
            return logEntryList.size();
        }
    }

    public Object getValueAt(int row, int col) {
        LogEntry logEntry = null;
        synchronized (lock) {
            if (row >= logEntryList.size()) return null;
            else if (col >= getColumnCount()) return null;
            logEntry = logEntryList.get(row);
        }
        Columns[] columns = Columns.values();
        if (col < columns.length) {
            Columns colType = columns[col];
            switch (colType) {
                case DATE:
                    return logEntry.date;
                case APP:
                    // lookup app from process table using PID
                    return null;
                case TID:
                    return logEntry.tid;
                case PID:
                    return logEntry.pid;
                case LEVEL:
                    return logEntry.level;
                case MSG:
                    return logEntry.message;
                default:
                    log.error("getValueAt: ERROR: {}", colType);
            }
        }
        return null;
    }

}