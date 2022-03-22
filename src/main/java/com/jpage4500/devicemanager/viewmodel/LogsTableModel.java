package com.jpage4500.devicemanager.viewmodel;

import com.jpage4500.devicemanager.data.LogEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

public class LogsTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(LogsTableModel.class);

    private final List<LogEntry> logsList;

    public enum Columns {
        DATE,
        THREAD,
        LOGLEVEL,
        MESSAGE
    }

    public LogsTableModel() {
        logsList = new ArrayList<>();
    }

    public void setLogsList(List<LogEntry> logsList) {
        this.logsList.clear();
        this.logsList.addAll(logsList);

        fireTableDataChanged();
    }

    public String getColumnName(int i) {
        Columns[] columns = Columns.values();
        Columns colType = columns[i];
        switch (colType) {
            case THREAD:
                return "T";
            case LOGLEVEL:
                return "L";
            default:
                return colType.name();
        }
    }

    public int getRowCount() {
        return logsList.size();
    }

    @Override
    public int getColumnCount() {
        return Columns.values().length;
    }

    public Object getValueAt(int row, int col) {
        if (row >= logsList.size()) return null;
        else if (col >= getColumnCount()) return null;

        LogEntry logEntry = logsList.get(row);

        Columns[] columns = Columns.values();
        Columns colType = columns[col];
        switch (colType) {
            case DATE:
                return logEntry.date;
            case THREAD:
                return logEntry.thread;
            case LOGLEVEL:
                return logEntry.level;
            case MESSAGE:
                return logEntry.message;
            default:
                log.debug("getValueAt: ERROR: {}", colType);
                return "";
        }
    }

}