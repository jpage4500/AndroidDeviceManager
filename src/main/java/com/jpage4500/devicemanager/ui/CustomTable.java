package com.jpage4500.devicemanager.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

/**
 *
 */
public class CustomTable extends JTable {
    private static final Logger log = LoggerFactory.getLogger(CustomTable.class);

    public CustomTable() {
        setRowHeight(30);
        //setOpaque(false);
        setAutoCreateRowSorter(true);
        getTableHeader().setBackground(new Color(197, 197, 197));
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        restore();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        Color color1 = Color.WHITE;
        Color color2 = new Color(246, 246, 246);
        if (!c.getBackground().equals(getSelectionBackground())) {
            Color color = (row % 2 == 0 ? color1 : color2);
            c.setBackground(color);
        }
        return c;
    }

    static class ColumnDetails {
        Object header;
        int width;
        int userPos;
        int modelPos;
    }

    public void restore() {
//        Preferences prefs = Preferences.userRoot();
//        String detailsStr = prefs.get(propKey + "-details", null);
//        if (detailsStr == null) return;
//        List<ColumnDetails> detailsList = GsonHelper.stringToList(detailsStr, ColumnDetails.class);
//        log.debug("restore: {}", GsonHelper.toJson(detailsList));
//
//        TableColumnModel columnModel = getColumnModel();
//        if (detailsList.size() != columnModel.getColumnCount()) {
//            log.debug("restore: wrong number of columns! {} vs {}", detailsList.size(), columnModel.getColumnCount());
//            return;
//        }
//
//        for (ColumnDetails details : detailsList) {
//            if (details.modelPos != details.userPos) {
//                log.debug("restore: move:{} to:{}", details.modelPos, details.userPos);
//                columnModel.moveColumn(details.modelPos, details.userPos);
//            }
//        }
    }

    public void persist() {
//        Enumeration<TableColumn> columns = getColumnModel().getColumns();
//        Iterator<TableColumn> iter = columns.asIterator();
//        List<ColumnDetails> detailList = new ArrayList<>();
//        for (int i = 0; iter.hasNext(); i++) {
//            TableColumn column = iter.next();
//            ColumnDetails details = new ColumnDetails();
//            details.header = column.getHeaderValue();
//            details.userPos = i;
//            details.modelPos = column.getModelIndex();
//            details.width = column.getWidth();
//            detailList.add(details);
//        }
//
//        Preferences prefs = Preferences.userRoot();
//        prefs.put(propKey + "-details", GsonHelper.toJson(detailList));
//        log.debug("persist: {}", GsonHelper.toJson(detailList));
    }
}
