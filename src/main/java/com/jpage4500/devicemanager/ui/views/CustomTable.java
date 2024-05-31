package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
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

    private static final Color COLOR_HEADER = new Color(197, 197, 197);
    private static final Color COLOR_ALTERNATE_ROW = new Color(246, 246, 246);

    private String prefKey;
    private TableListener listener;
    private JScrollPane scrollPane;

    private boolean showTooltips = false;

    public int selectedColumn = -1;

    public interface TableListener {
        /**
         * @param row    converted to model row
         * @param column converted to model col
         */
        void showPopupMenu(int row, int column, MouseEvent e);

        /**
         * @param column converted to model col
         */
        void showHeaderPopupMenu(int column, MouseEvent e);

        /**
         * @param row    converted to model row
         * @param column converted to model col
         */
        void handleTableDoubleClick(int row, int column, MouseEvent e);
    }

    public CustomTable(String prefKey, TableListener listener) {
        this.listener = listener;
        this.prefKey = prefKey;

        createScrollPane();

        setTableHeader(new CustomTableHeader(this));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // single click
                Point point = e.getPoint();
                int row = rowAtPoint(point);
                int column = columnAtPoint(point);
                if (SwingUtilities.isRightMouseButton(e)) {
                    // right-click
                    if (getSelectedRowCount() <= 1) {
                        changeSelection(row, column, false, false);
                    }
                    // convert table row/col to model row/col
                    row = convertRowIndexToModel(row);
                    column = convertColumnIndexToModel(column);
                    selectedColumn = column;
                    if (listener != null) listener.showPopupMenu(row, column, e);
                } else if (e.getClickCount() == 2) {
                    // double-click
                    selectedColumn = -1;
                    // convert table row/col to model row/col
                    row = convertRowIndexToModel(row);
                    column = convertColumnIndexToModel(column);
                    if (listener != null) listener.handleTableDoubleClick(row, column, e);
                }
            }
        });

        // TODO: REMOVE ME
        getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnAdded(TableColumnModelEvent e) {

            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {

            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {

            }

            @Override
            public void columnMarginChanged(ChangeEvent e) {
                TableColumn resizingColumn = getTableHeader().getResizingColumn();
                if (resizingColumn != null)
                    log.trace("columnMarginChanged: {}, w:{}", resizingColumn.getModelIndex(), resizingColumn.getWidth());
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {

            }
        });
    }

    private void createScrollPane() {
        scrollPane = new JScrollPane(this);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // single click outside of table should de-select row
                    clearSelection();
                }
            }
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

    public void setShowTooltips(boolean showTooltips) {
        this.showTooltips = showTooltips;
    }

    public void allowSorting(boolean allowSorting) {
        if (!allowSorting) return;
        setAutoCreateRowSorter(allowSorting);
    }

    @Override
    public void scrollRectToVisible(Rectangle aRect) {
        aRect.x = getVisibleRect().x;
        super.scrollRectToVisible(aRect);
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        //restore();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (c != null && !c.getBackground().equals(getSelectionBackground())) {
            Color color = (row % 2 == 0 ? Color.WHITE : COLOR_ALTERNATE_ROW);
            c.setBackground(color);
        }
        return c;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (!showTooltips) return null;
        // only show tooltip if value doesn't fit in column width
        String toolTipText = null;
        Point p = e.getPoint();
        int col = columnAtPoint(p);
        int row = rowAtPoint(p);
        Rectangle bounds = getCellRect(row, col, false);
        Component c = prepareRenderer(getCellRenderer(row, col), row, col);
        if (c != null && c.getPreferredSize().width > bounds.width) {
            if (c instanceof JLabel label) {
                toolTipText = label.getText();
            } else if (c instanceof JTextField textField) {
                toolTipText = textField.getText();
            } else {
                Object value = getValueAt(row, col);
                toolTipText = value.toString();
            }
        }
        return toolTipText;
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
        Object header;
        int width;
        int userPos;
        int modelPos;
    }

    public void restore() {
        if (prefKey == null) return;
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
            log.trace("restore: col:{}, w:{}", i, details.width);
            columnModel.getColumn(i).setPreferredWidth(details.width);
        }

        for (ColumnDetails details : detailsList) {
            if (details.modelPos != details.userPos) {
                log.trace("restore: move:{} to:{}", details.modelPos, details.userPos);
                columnModel.moveColumn(details.modelPos, details.userPos);
            }
        }
    }

    public void persist() {
        if (prefKey == null) return;

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
        log.trace("persist: {}: {}", prefKey, GsonHelper.toJson(detailList));
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

            arrowUpIcon = UiUtils.getImageIcon("arrow_down.png", 15);
            arrowDownIcon = UiUtils.getImageIcon("arrow_up.png", 15);

            setBackground(COLOR_HEADER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        Point point = e.getPoint();
                        int row = rowAtPoint(point);
                        int column = columnAtPoint(point);
                        // convert table row/col to model row/col
                        column = convertColumnIndexToModel(column);
                        if (listener != null) listener.showHeaderPopupMenu(column, e);
                    }
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
            int colIdx = this.columnAtPoint(e.getPoint());
            if (colIdx == -1) return null;
            Point p = e.getPoint();
            int col = columnAtPoint(p);
            int row = rowAtPoint(p);
            if (row == -1 || col == -1) return null;
            Rectangle bounds = getCellRect(row, col, false);
            Component c = prepareRenderer(getCellRenderer(row, col), row, col);
            if (c != null && c.getPreferredSize().width > bounds.width) {
                return this.columnModel.getColumn(colIdx).getHeaderValue().toString();
            }
            return null;
        }
    }

}
