package com.jpage4500.devicemanager.table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.vidstige.jadb.RemoteFile;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ExploreTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(ExploreTableModel.class);

    private final List<RemoteFile> fileList;

    public enum Columns {
        NAME,
        SIZE,
        DATE,
    }

    public ExploreTableModel() {
        fileList = new ArrayList<>();
    }

    public void setFileList(List<RemoteFile> fileList) {
        this.fileList.clear();
        this.fileList.addAll(fileList);

        fireTableDataChanged();
    }

    /**
     * get device for given row
     * NOTE: make sure you use table.convertRowIndexToModel() first
     */
    public RemoteFile getDeviceFileAtRow(int row) {
        if (fileList.size() > row) {
            return fileList.get(row);
        }
        return null;
    }

    public int getColumnCount() {
        return Columns.values().length;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return RemoteFile.class;
    }

    public String getColumnName(int i) {
        Columns[] columns = Columns.values();
        Columns colType = columns[i];
        return colType.name();
    }

    public int getRowCount() {
        return fileList.size();
    }

    public Object getValueAt(int row, int col) {
        if (row >= fileList.size()) return null;
        else if (col >= getColumnCount()) return null;

        return fileList.get(row);
    }

}