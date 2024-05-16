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
    private static final int MAX_LINES = 50000;
    private static final int REMOVE_EXTRA = 5000;

    private final ArrayList<LogEntry> logEntryList;
    // map of PID <-> app name
    private final Map<String, String> processMap;
    private String searchText;

    public enum Columns {
        DATE,
        APP,
        TID,
        PID,
        LEVEL,
        MSG,
    }

    public LogsTableModel() {
        logEntryList = new ArrayList<>();
        processMap = new HashMap<>();
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
        fireTableDataChanged();
    }

    public void setSearchText(String text) {
        if (TextUtils.equals(searchText, text)) return;
        searchText = text;
        fireTableDataChanged();
    }

    private void checkSizeAndUpdate(int numAdded) {
        if (logEntryList.size() > MAX_LINES) {
            // remove rows over the max and also a little more to prevent needing to do this on every new log
            int numRemove = (logEntryList.size() - MAX_LINES) + REMOVE_EXTRA;
            log.trace("checkSizeAndUpdate: removing:{}, size:{}", numRemove, logEntryList.size());
            logEntryList.subList(0, numRemove).clear();
            fireTableRowsDeleted(0, numRemove - 1);
            //fireTableDataChanged();
        } else {
            int startPos = logEntryList.size() - numAdded;
            int endPos = logEntryList.size() - 1;
            fireTableRowsInserted(startPos, endPos);
        }
    }

    public int getColumnCount() {
        return Columns.values().length;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return LogEntry.class;
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

}