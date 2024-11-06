package com.jpage4500.devicemanager.table;

import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogsTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(LogsTableModel.class);
    private static final int MAX_LINES = 90000;
    private static final int REMOVE_EXTRA = 5000;

    private final ArrayList<LogEntry> logEntryList;
    // map of PID <-> app name
    private final Map<String, String> processMap;
    private String searchText;

    private Columns[] visibleColumns;

    /**
     * get text value for a given LogEntry and column
     */
    public String getTextValue(int row, int column) {
        LogEntry logEntry = (LogEntry) getValueAt(row, column);
        if (logEntry == null) return null;
        Columns col = visibleColumns[column]; //LogsTableModel.Columns.values()[column];
        return switch (col) {
            case DATE -> logEntry.date;
            case APP -> {
                // set app using app <-> pid list
                logEntry.app = getAppForPid(logEntry.pid);
                yield logEntry.app;
            }
            case TID -> {
                if (TextUtils.equals(logEntry.tid, logEntry.pid)) yield "-";
                yield logEntry.tid;
            }
            case PID -> logEntry.pid;
            case LEVEL -> logEntry.level;
            case TAG -> logEntry.tag;
            case MSG -> logEntry.message;
        };
    }

    public enum Columns {
        DATE("Date"),
        APP("App"),
        TID("TID"),
        PID("PID"),
        LEVEL("Level"),
        TAG("Tag"),
        MSG("Message"),
        ;
        public String desc;

        Columns(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }

        public static Columns fromDesc(String desc) {
            for (Columns col : Columns.values()) {
                if (col.desc.equals(desc)) return col;
            }
            return null;
        }
    }

    public LogsTableModel() {
        logEntryList = new ArrayList<>();
        processMap = new HashMap<>();
        setHiddenColumns(null);
    }

    public void clearLogs() {
        this.logEntryList.clear();
        fireTableDataChanged();
    }

    public void addLogEntry(List<LogEntry> logEntryList) {
        this.logEntryList.addAll(logEntryList);
        checkSizeAndUpdate(logEntryList.size());
    }

    public void setProcessMap(Map<String, String> processMap) {
        this.processMap.clear();
        this.processMap.putAll(processMap);

        // NOTE: is it worth refreshing all rows just to update old log entries?
        //fireTableDataChanged();
    }

    public void setSearchText(String text) {
        if (TextUtils.equals(searchText, text)) return;
        searchText = text;
        fireTableDataChanged();
    }

    /**
     * @return the latest log entry time
     */
    public Long getLastLogTime() {
        if (logEntryList.isEmpty()) return null;
        LogEntry last = logEntryList.get(logEntryList.size() - 1);
        return last.timestamp;
    }

    private void checkSizeAndUpdate(int numAdded) {
        if (logEntryList.size() > MAX_LINES) {
            // remove rows over the max and also a little more to prevent needing to do this on every new log
            int numRemove = (logEntryList.size() - MAX_LINES) + REMOVE_EXTRA;
            //log.trace("checkSizeAndUpdate: removing:{}, size:{}", numRemove, logEntryList.size());
            logEntryList.subList(0, numRemove).clear();
            fireTableRowsDeleted(0, numRemove - 1);
        } else {
            int startPos = logEntryList.size() - numAdded;
            int endPos = logEntryList.size() - 1;
            fireTableRowsInserted(startPos, endPos);
        }
    }

    public int getColumnCount() {
        return visibleColumns.length;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return LogEntry.class;
    }

    /**
     * return one of the predefined columns
     */
    public LogsTableModel.Columns getColumnType(int colIndex) {
        if (colIndex >= 0 && colIndex < visibleColumns.length) {
            return visibleColumns[colIndex];
        }
        return null;
    }

    public String getColumnName(int i) {
        if (i < visibleColumns.length) {
            Columns colType = visibleColumns[i];
            return colType.toString();
        }
        return null;
    }

    public int getRowCount() {
        return logEntryList.size();
    }

    public Object getValueAt(int row, int col) {
        if (row >= logEntryList.size()) return null;
        else if (col >= getColumnCount()) return null;
        return logEntryList.get(row);
    }

    public String getAppForPid(String pid) {
        return processMap.get(pid);
    }

    public String getSearchText() {
        return searchText;
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

}