package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.utils.GsonHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 *
 */
public class CustomTable extends JTable {
    private static final Logger log = LoggerFactory.getLogger(CustomTable.class);

    private final Color lightGreyColor = new Color(246, 246, 246);
    private final Color headerColor = new Color(197, 197, 197);

    private String prefKey;

    public CustomTable(String prefKey) {
        this.prefKey = prefKey;
        //setOpaque(false);
        setAutoCreateRowSorter(true);
        getTableHeader().setBackground(headerColor);
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        restore();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (!c.getBackground().equals(getSelectionBackground())) {
            Color color = (row % 2 == 0 ? Color.WHITE : lightGreyColor);
            c.setBackground(color);
        }
        return c;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        // only show tooltip if value doesn't fit in column width
        String toolTipText = null;
        Point p = e.getPoint();
        int col = columnAtPoint(p);
        int row = rowAtPoint(p);
        Rectangle bounds = getCellRect(row, col, false);
        Component comp = prepareRenderer(getCellRenderer(row, col), row, col);
        if (comp.getPreferredSize().width > bounds.width) {
            Object value = getValueAt(row, col);
            toolTipText = value.toString();
        }
        return toolTipText;
    }

    public static class ColumnDetails {
        Object header;
        int width;
        int userPos;
        int modelPos;
    }

    public void restore() {
        Preferences prefs = Preferences.userRoot();
        String detailsStr = prefs.get(prefKey + "-details", null);
        if (detailsStr == null) return;
        List<ColumnDetails> detailsList = GsonHelper.stringToList(detailsStr, ColumnDetails.class);
        //log.debug("restore: {}", GsonHelper.toJson(detailsList));

        TableColumnModel columnModel = getColumnModel();
        if (detailsList.size() != columnModel.getColumnCount()) {
            log.debug("restore: wrong number of columns! {} vs {}", detailsList.size(), columnModel.getColumnCount());
            return;
        }

        for (int i = 0; i < detailsList.size(); i++) {
            ColumnDetails details = detailsList.get(i);
            columnModel.getColumn(i).setPreferredWidth(details.width);
        }

        for (int i = 0; i < detailsList.size(); i++) {
            ColumnDetails details = detailsList.get(i);
            if (details.modelPos != details.userPos) {
                //log.debug("restore: move:{} to:{}", details.modelPos, details.userPos);
                columnModel.moveColumn(details.modelPos, details.userPos);
            }
        }
    }

    public void persist() {
        Enumeration<TableColumn> columns = getColumnModel().getColumns();
        Iterator<TableColumn> iter = columns.asIterator();
        List<ColumnDetails> detailList = new ArrayList<>();
        for (int i = 0; iter.hasNext(); i++) {
            TableColumn column = iter.next();
            ColumnDetails details = new ColumnDetails();
            details.header = column.getHeaderValue();
            details.userPos = i;
            details.modelPos = column.getModelIndex();
            details.width = column.getWidth();
            detailList.add(details);
        }

        Preferences prefs = Preferences.userRoot();
        prefs.put(prefKey + "-details", GsonHelper.toJson(detailList));
        //log.debug("persist: {}", GsonHelper.toJson(detailList));
    }
}
