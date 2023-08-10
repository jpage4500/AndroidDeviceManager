package com.jpage4500.devicemanager.viewmodel;

import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.utils.FileUtils;

import net.coobird.thumbnailator.Thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;

public class ExploreTableModel extends AbstractTableModel {
    private static final Logger log = LoggerFactory.getLogger(ExploreTableModel.class);

    private final List<DeviceFile> fileList;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");

    private final Icon folderIcon;
    private final Icon fileIcon;

    public enum Columns {
        ICON,
        NAME,
        SIZE,
        DATE,
        ;
    }

    public ExploreTableModel() {
        fileList = new ArrayList<>();

        folderIcon = getIcon("icon_folder.png");
        fileIcon = getIcon("icon_file.png");
    }

    public void setFileList(List<DeviceFile> fileList) {
        this.fileList.clear();
        this.fileList.addAll(fileList);

        fireTableDataChanged();
    }

    private ImageIcon getIcon(String imageName) {
        Image icon = null;
        try {
            // library offers MUCH better image scaling than ImageIO
            icon = Thumbnails.of(getClass().getResource("/images/" + imageName)).size(20, 20).asBufferedImage();
            //Image image = ImageIO.read(getClass().getResource("/images/" + imageName));
        } catch (Exception e) {
            log.debug("createButton: Exception:{}", e.getMessage());
        }
        return new ImageIcon(icon);
    }

    /**
     * get device for given row
     * NOTE: make sure you use table.convertRowIndexToModel() first
     */
    public DeviceFile getDeviceFileAtRow(int row) {
        if (fileList.size() > row) {
            return fileList.get(row);
        }
        return null;
    }

    public int getColumnCount() {
        return Columns.values().length + fileList.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == ExploreTableModel.Columns.ICON.ordinal()) return Icon.class;
        return String.class;
    }

    public String getColumnName(int i) {
        Columns[] columns = Columns.values();
        if (i < columns.length) {
            Columns colType = columns[i];
            if (colType == Columns.ICON) return null;
            return colType.name();
        }
        return null;
    }

    public int getRowCount() {
        return fileList.size();
    }

    public Object getValueAt(int row, int col) {
        if (row >= fileList.size()) return null;
        else if (col >= getColumnCount()) return null;

        DeviceFile deviceFile = fileList.get(row);
        Columns[] columns = Columns.values();
        if (col < columns.length) {
            Columns colType = columns[col];
            switch (colType) {
                case ICON:
                    if (deviceFile.isDir || deviceFile.isLink) return folderIcon;
                    else return fileIcon;
                case NAME:
                    return deviceFile.name;
                case SIZE:
                    return FileUtils.bytesToDisplayString(deviceFile.size);
                case DATE:
                    if (deviceFile.date != null) {
                        return dateFormat.format(deviceFile.date);
                    }
                default:
                    //log.debug("getValueAt: ERROR: {}", colType);
                    return "";
            }
        }
        return null;
    }

}