package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

/**
 *
 */
public class CustomTable extends JTable {
    private static final Logger log = LoggerFactory.getLogger(CustomTable.class);

    private String prefKey;
    private TooltipListener tooltipListener;
    private DoubleClickListener doubleClickListener;
    private PopupMenuListener popupMenuListener;
    private JScrollPane scrollPane;

    private int selectedColumn = -1;

    private EmptyView emptyView;

    public interface DoubleClickListener {
        /**
         * @param row    converted to model row
         * @param column converted to model col
         */
        void handleDoubleClick(int row, int column, MouseEvent e);
    }

    public interface PopupMenuListener {
        /**
         * show popup menu on right-click
         *
         * @param row    table row, converted to model data (-1 for header)
         * @param column table column, converted to model data
         * @return popup menu to display or null for no action
         */
        JPopupMenu getPopupMenu(int row, int column);
    }

    public interface TooltipListener {
        /**
         * return tooltip text to display
         *
         * @param row table row, converted to model data (-1 for header)
         * @param col table column, converted to model data
         * @see #getTextIfTruncated to show a tooltip only if value doesn't fit
         */
        String getToolTipText(int row, int col);
    }

    @Override
    public int getSelectedColumn() {
        return selectedColumn;
    }

    public CustomTable(String prefKey) {
        this.prefKey = prefKey;
        setOpaque(false);
        setBackground(Colors.COLOR_BACKGROUND);

        emptyView = new EmptyView();
        emptyView.setShowBackground(PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true));

        createScrollPane();

        setTableHeader(new CustomTableHeader(this));

        UiUtils.addClickListener(this, e -> {
            // single click
            Point point = e.getPoint();
            int row = rowAtPoint(point);
            int column = columnAtPoint(point);
            if (SwingUtilities.isRightMouseButton(e)) {
                // right-click
                if (getSelectedRowCount() <= 1) {
                    changeSelection(row, column, false, false);
                }
                selectedColumn = column;
                if (popupMenuListener != null) {
                    // convert table row/col to model row/col
                    row = convertRowIndexToModel(row);
                    column = convertColumnIndexToModel(column);
                    JPopupMenu popupMenu = popupMenuListener.getPopupMenu(row, column);
                    if (popupMenu != null) {
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            } else if (e.getClickCount() == 2) {
                // double-click
                // convert table row/col to model row/col
                row = convertRowIndexToModel(row);
                column = convertColumnIndexToModel(column);
                if (doubleClickListener != null) doubleClickListener.handleDoubleClick(row, column, e);
            }
        });

        // TODO: add to log column sizes
//        getColumnModel().addColumnModelListener(new TableColumnModelListener() {
//            @Override
//            public void columnAdded(TableColumnModelEvent tableColumnModelEvent) {
//            }
//
//            @Override
//            public void columnRemoved(TableColumnModelEvent tableColumnModelEvent) {
//            }
//
//            @Override
//            public void columnMoved(TableColumnModelEvent tableColumnModelEvent) {
//            }
//
//            @Override
//            public void columnMarginChanged(ChangeEvent changeEvent) {
//                TableColumn resizingColumn = getTableHeader().getResizingColumn();
//                if (resizingColumn != null) {
//                    log.trace("columnMarginChanged: {}, {}", resizingColumn.getHeaderValue(), resizingColumn.getWidth());
//                }
//            }
//
//            @Override
//            public void columnSelectionChanged(ListSelectionEvent listSelectionEvent) {
//            }
//        });
    }

    public void setDoubleClickListener(DoubleClickListener doubleClickListener) {
        this.doubleClickListener = doubleClickListener;
    }

    public void setTooltipListener(TooltipListener tooltipListener) {
        this.tooltipListener = tooltipListener;
    }

    public void setPopupMenuListener(PopupMenuListener popupMenuListener) {
        this.popupMenuListener = popupMenuListener;
    }

    public void setEmptyText(String emptyText) {
        emptyView.setEmptyText(emptyText);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        scrollPane.repaint();
    }

    private void createScrollPane() {
        scrollPane = new JScrollPane(this) {
            @Override
            public void paint(Graphics graphics) {
                super.paint(graphics);
                emptyView.setEmptyText(getRowCount() == 0 ? "No Devices" : null);
                emptyView.paint(graphics, getWidth(), getHeight(), getTableHeader().getHeight());
            }
        };
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        UiUtils.addLeftClickListener(scrollPane, e -> {
            // single click outside of table should de-select row
            clearSelection();
        });
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public void setupDragAndDrop() {
        // support drag and drop of files
        //MyDragDropListener dragDropListener = new MyDragDropListener(this, false, this::handleFilesDropped);
        getScrollPane().setDropTarget(new DropTarget() {
            @Override
            public synchronized void dragOver(DropTargetDragEvent dtde) {
                super.dragOver(dtde);
            }

            @Override
            public synchronized void dragExit(DropTargetEvent dte) {
                super.dragExit(dte);
            }
        });
    }

    public void allowSorting(boolean allowSorting) {
        if (!allowSorting) return;
        setAutoCreateRowSorter(allowSorting);
    }

    /**
     * NOTE: keeps scroll position to left edge of table
     */
    @Override
    public void scrollRectToVisible(Rectangle aRect) {
        aRect.x = getVisibleRect().x;
        super.scrollRectToVisible(aRect);
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);

        dataModel.addTableModelListener(tableModelEvent -> {
            // table data changed, update empty view
            emptyView.setShowBackground(PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true));
            scrollPane.repaint();
        });
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (c != null && !c.getBackground().equals(getSelectionBackground())) {
            Color color = (row % 2 == 0 ? Color.WHITE : Colors.COLOR_ALTERNATE_ROW);
            c.setBackground(color);
        }
        return c;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (tooltipListener == null) return null;
        Point p = e.getPoint();
        int col = columnAtPoint(p);
        int row = rowAtPoint(p);
        return tooltipListener.getToolTipText(row, col);
    }

    /**
     * get text for value at row/col *ONLY* if it doesn't fit
     */
    public String getTextIfTruncated(int row, int col) {
        if (row == -1) {
            // header
            JTableHeader header = getTableHeader();
            TableColumnModel headerColumnModel = header.getColumnModel();
            if (col < 0 || col >= headerColumnModel.getColumnCount()) return "";
            TableColumn column = headerColumnModel.getColumn(col);
            Object value = column.getHeaderValue();
            int width = column.getWidth();
            Component c = header.getDefaultRenderer().getTableCellRendererComponent(this, value, false, false, row, col);
            if (c != null && c.getPreferredSize().width > width) {
                if (c instanceof JLabel label) {
                    return label.getText();
                }
            }
        } else {
            Rectangle bounds = getCellRect(row, col, false);
            Component c = prepareRenderer(getCellRenderer(row, col), row, col);
            if (c != null && c.getPreferredSize().width > bounds.width) {
                if (c instanceof JLabel label) {
                    return label.getText();
                } else if (c instanceof JTextField textField) {
                    return textField.getText();
                } else {
                    Object value = getValueAt(row, col);
                    return value.toString();
                }
            }
        }
        return null;
    }

    public void scrollToBottom() {
        scrollRectToVisible(getCellRect(getRowCount() - 1, 0, true));
    }

    public void scrollToTop() {
        scrollRectToVisible(getCellRect(0, 0, true));
    }

    public void pageUp() {
        scrollPage(true);
    }

    public void pageDown() {
        scrollPage(false);
    }

    private void scrollPage(boolean isUp) {
        Rectangle visibleRect = getVisibleRect();
        int firstRow = rowAtPoint(visibleRect.getLocation());
        visibleRect.translate(0, visibleRect.height);
        int lastRow = rowAtPoint(visibleRect.getLocation());
        int numRows = lastRow - firstRow;
        int scrollToRow;
        if (isUp) {
            scrollToRow = Math.max(firstRow - numRows, 0);
        } else {
            scrollToRow = Math.min(lastRow + numRows, getRowCount() - 1);
        }
        scrollRectToVisible(getCellRect(scrollToRow, 0, true));
    }

    public static class ColumnDetails {
        String name;
        int width;
        int userPos;
        int modelPos;

        @ExcludeFromSerialization
        TableColumn column;
    }

    public boolean restoreTable() {
        if (prefKey == null) return false;
        Preferences prefs = Preferences.userRoot();
        String detailsStr = prefs.get(prefKey + "-details", null);
        if (detailsStr == null) return false;
        List<ColumnDetails> detailsList = GsonHelper.stringToList(detailsStr, ColumnDetails.class);

        // TODO: this is messy but it's the most reliable way I've found to retain user column order..
        TableColumnModel columnModel = getColumnModel();
        // 1) backup columns to ColumnDetails
        for (ColumnDetails details : detailsList) {
            if (details.name == null) {
                //log.debug("restoreTable: invalid: {}", GsonHelper.toJson(details));
                continue;
            }
            details.column = getColumnByName(details.name);
            if (details.column != null) {
                columnModel.removeColumn(details.column);
            }
        }

        // 2) backup any additional columns (if any)
        List<TableColumn> additionalColumnList = new ArrayList<>();
        Iterator<TableColumn> iterator = columnModel.getColumns().asIterator();
        while (iterator.hasNext()) {
            TableColumn column = iterator.next();
            additionalColumnList.add(column);
            columnModel.removeColumn(column);
        }

        // 4) re-add columns in order they were saved
        for (ColumnDetails details : detailsList) {
            if (details.column != null) {
                columnModel.addColumn(details.column);
                details.column.setPreferredWidth(details.width);
            }
        }

        // 5) re-add additional columns
        for (TableColumn column : additionalColumnList) {
            columnModel.addColumn(column);
        }
        return true;
    }

    /**
     * get column by header name (NOTE: will return null and not throw an Exception when not found)
     */
    public TableColumn getColumnByName(String searchName) {
        Enumeration<TableColumn> columns = getColumnModel().getColumns();
        Iterator<TableColumn> iterator = columns.asIterator();
        while (iterator.hasNext()) {
            TableColumn column = iterator.next();
            String columnName = column.getHeaderValue().toString();
            if (TextUtils.equalsIgnoreCase(columnName, searchName)) {
                return column;
            }
        }
        if (log.isTraceEnabled()) log.trace("getColumnByName: NOT_FOUND:{}, {}", searchName, Utils.getStackTraceString());
        return null;
    }

    public void setPreferredColWidth(String colName, int preferredWidth) {
        TableColumn column = getColumnByName(colName);
        if (column == null) return;
        column.setPreferredWidth(preferredWidth);
    }

    public void setMaxColWidth(String colName, int maxWidth) {
        TableColumn column = getColumnByName(colName);
        if (column == null) return;
        column.setMaxWidth(maxWidth);
    }

    public void saveTable() {
        if (prefKey == null) return;

        // save columns in display order
        Enumeration<TableColumn> columns = getColumnModel().getColumns();
        Iterator<TableColumn> iter = columns.asIterator();
        List<ColumnDetails> detailList = new ArrayList<>();
        for (int i = 0; iter.hasNext(); i++) {
            TableColumn column = iter.next();
            ColumnDetails details = new ColumnDetails();
            details.name = column.getHeaderValue().toString();
            details.userPos = i;
            details.modelPos = column.getModelIndex();
            details.width = column.getWidth();
            //int maxWidth = column.getMaxWidth();
            if (details.name == null) {
                log.debug("saveTable: invalid name:{} ({})", GsonHelper.toJson(details), prefKey);
                continue;
            }
            detailList.add(details);
            //log.trace("persist: {}, pos:{}, i:{}, w:{}, max:{}", details.header, i, details.modelPos, details.width, details.maxWidth);
        }

        Preferences prefs = Preferences.userRoot();
        prefs.put(prefKey + "-details", GsonHelper.toJson(detailList));
        //log.trace("persist: {}: {}", prefKey, GsonHelper.toJson(detailList));
    }

    /**
     * default table header PLUS:
     * - more visible sort icons
     * - tooltips when header text is truncated
     */
    private class CustomTableHeader extends JTableHeader {
        private final Icon arrowUpIcon;
        private final Icon arrowDownIcon;

        public CustomTableHeader(JTable t) {
            super(t.getColumnModel());

            arrowUpIcon = UiUtils.getImageIcon("arrow_down.png", UiUtils.IMG_SIZE_SMALL);
            arrowDownIcon = UiUtils.getImageIcon("arrow_up.png", UiUtils.IMG_SIZE_SMALL);

            setBackground(Colors.COLOR_TABLE_HEADER);

            UiUtils.addRightClickListener(this, e -> {
                if (popupMenuListener != null) {
                    Point point = e.getPoint();
                    int column = columnAtPoint(point);
                    // convert table row/col to model row/col
                    column = convertColumnIndexToModel(column);
                    // NOTE: row fixed at -1 for header
                    JPopupMenu popupMenu = popupMenuListener.getPopupMenu(-1, column);
                    if (popupMenu != null) popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            });

            // get original renderer and just modify label icons (up/down arrows)
            final TableCellRenderer defaultRenderer = t.getTableHeader().getDefaultRenderer();
            setDefaultRenderer((table, value, isSelected, hasFocus, row, column) -> {
                Component comp = defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (comp instanceof JLabel label) {
                    label.setIcon(getSortIcon(column));
                }
                return comp;
            });
        }

        private Icon getSortIcon(int column) {
            Icon sortIcon = null;
            if (getRowSorter() != null) {
                List<? extends RowSorter.SortKey> sortKeys = getRowSorter().getSortKeys();
                if (!sortKeys.isEmpty()) {
                    RowSorter.SortKey key = sortKeys.get(0);
                    if (key.getColumn() == convertColumnIndexToModel(column)) {
                        sortIcon = key.getSortOrder() == SortOrder.ASCENDING ? arrowDownIcon : arrowUpIcon;
                    }
                }
            }
            return sortIcon;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            if (tooltipListener == null) return null;
            Point p = e.getPoint();
            int col = columnAtPoint(p);
            return tooltipListener.getToolTipText(-1, col);
        }
    }

}
