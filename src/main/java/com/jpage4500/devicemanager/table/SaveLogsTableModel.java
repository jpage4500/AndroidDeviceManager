package com.jpage4500.devicemanager.table;

import com.jpage4500.devicemanager.data.SaveLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class SaveLogsTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(SaveLogsTableModel.class);

    private final List<SaveLogEntry> entryList;

    public enum Columns {
        NAME("Name"),
        SERIAL("Serial"),
        SIZE("Size"),
        ;
        String desc;

        Columns(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }

    public SaveLogsTableModel() {
        entryList = new ArrayList<>();
    }

    public void setEntryList(List<SaveLogEntry> entryList) {
        this.entryList.clear();
        this.entryList.addAll(entryList);

        fireTableDataChanged();
    }

    public List<SaveLogEntry> getEntryList() {
        return entryList;
    }

    public void notifyEntryUpdated(SaveLogEntry entry) {
        int index = entryList.indexOf(entry);
        if (index >= 0) {
            fireTableRowsUpdated(index, index);
        }
    }

    public int getColumnCount() {
        return Columns.values().length;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return SaveLogEntry.class;
    }

    public String getColumnName(int i) {
        Columns[] columns = Columns.values();
        Columns colType = columns[i];
        return colType.toString();
    }

    public int getRowCount() {
        return entryList.size();
    }

    public Object getValueAt(int row, int col) {
        if (row >= entryList.size()) return null;
        else if (col >= getColumnCount()) return null;

        return entryList.get(row);
    }

}