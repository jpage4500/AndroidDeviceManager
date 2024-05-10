package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

import javax.swing.RowSorter.SortKey;

public class TableHeaderRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(TableHeaderRenderer.class);

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd h:mm aa");

    private final Icon arrowUpIcon;
    private final Icon arrowDownIcon;

    public TableHeaderRenderer() {
        setOpaque(true);

        Border margin = new EmptyBorder(0, 10, 0, 0);
        setBorder(margin);

        arrowUpIcon = UiUtils.getIcon("arrow_up.png", 20);
        arrowDownIcon = UiUtils.getIcon("arrow_up.png", 20);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        Icon sortIcon = null;
        if (table != null && table.getRowSorter() != null) {
            List<? extends SortKey> sortKeys = table.getRowSorter().getSortKeys();
            if (!sortKeys.isEmpty()) {
                SortKey key = sortKeys.get(0);
                if (key.getColumn() == table.convertColumnIndexToModel(column)) {
                    sortIcon = key.getSortOrder() == SortOrder.ASCENDING ? arrowUpIcon : arrowDownIcon;
                }
            }
        }
        setIcon(sortIcon);
        return this;
    }
}