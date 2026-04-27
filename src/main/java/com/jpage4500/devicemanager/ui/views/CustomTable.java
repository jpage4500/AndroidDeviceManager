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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 *
 */
public class CustomTable extends JTable {
    private static final Logger log = LoggerFactory.getLogger(CustomTable.class);

    private static final int MIN_COLUMN_WIDTH = 10;
    private static final int MAX_COLUMN_WIDTH = 2000;

    private String prefKey;
    private TooltipListener tooltipListener;
    private DoubleClickListener doubleClickListener;
    private PopupMenuListener popupMenuListener;
    private JScrollPane scrollPane;

    // column-name -> max/min width; re-applied after every restore so constraints
    // survive structure changes that rebuild TableColumn instances
    private final Map<String, Integer> maxWidthByName = new HashMap<>();
    private final Map<String, Integer> minWidthByName = new HashMap<>();

    private int selectedColumn = -1;

    private boolean showBackground;
    private String emptyText;
    private Image emptyImage;
    private Font emptyTextFont;

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

        showBackground = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true);

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
        this.emptyText = emptyText;
        emptyImage = UiUtils.getImage("empty_image.png", 500);
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
                if (emptyImage != null && showBackground) {
                    int headerH = getTableHeader().getHeight();
                    int width = getWidth();
                    int imgW = emptyImage.getWidth(null);
                    int imgH = emptyImage.getHeight(null);
                    double aspectRatio = width / (double) imgW;
                    double drawImageH = imgH * aspectRatio;
                    // make image semi-transparent
                    Graphics2D g2d = (Graphics2D) graphics.create();
                    g2d.setComposite(AlphaComposite.SrcOver.derive(0.2f));
                    g2d.drawImage(emptyImage, 0, headerH, width, (int) drawImageH, null);
                    g2d.dispose();
                }
                if (getRowCount() == 0 && emptyText != null) {
                    // draw empty text in center
                    if (emptyTextFont == null) {
                        emptyTextFont = graphics.getFont().deriveFont(Font.BOLD, 22);
                    }
                    graphics.setFont(emptyTextFont);
                    FontMetrics fontMetrics = graphics.getFontMetrics(emptyTextFont);
                    int textH = emptyTextFont.getSize() * (fontMetrics.getAscent() + fontMetrics.getDescent()) / fontMetrics.getAscent();
                    int textW = fontMetrics.stringWidth(emptyText);
                    int width = getWidth();
                    int height = getHeight();
                    int headerH = getTableHeader().getHeight();
                    int x = width / 2 - (textW / 2);
                    int y = (height / 2);
                    // prevent drawing on top of header
                    if (y < (headerH * 2)) y = headerH * 2;
                    // don't draw if no available space
                    if (x >= 0 && y >= 0 && (height - headerH > textH)) {
                        graphics.drawString(emptyText, x, y);
                    }
                }
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
            showBackground = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true);
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
        int modelPos;

        @ExcludeFromSerialization
        transient TableColumn column;
    }

    public boolean restoreTable() {
        if (prefKey == null) return false;

        try {
            Preferences prefs = Preferences.userRoot();
            String detailsStr = prefs.get(prefKey + "-details", null);
            if (detailsStr == null || detailsStr.isEmpty()) {
                if (log.isTraceEnabled()) log.trace("restoreTable: no saved state for {}", prefKey);
                return false;
            }

            List<ColumnDetails> detailsList = GsonHelper.stringToList(detailsStr, ColumnDetails.class);
            if (detailsList == null || detailsList.isEmpty()) {
                log.warn("restoreTable: failed to parse saved state for {}", prefKey);
                return false;
            }

            return restoreColumnOrder(detailsList);

        } catch (Exception e) {
            log.error("restoreTable: error restoring table state for {}: {}", prefKey, e.getMessage());
            return false;
        }
    }

    private boolean restoreColumnOrder(List<ColumnDetails> detailsList) {
        TableColumnModel columnModel = getColumnModel();

        // First pass: validate all columns exist and build ordered list
        List<TableColumn> orderedColumns = new ArrayList<>();
        for (ColumnDetails details : detailsList) {
            if (details.name == null) continue;

            TableColumn column = getColumnByName(details.name);
            if (column == null) {
                log.warn("restoreTable: column '{}' not found, skipping", details.name);
                continue;
            }
            orderedColumns.add(column);
        }

        if (orderedColumns.isEmpty()) {
            log.warn("restoreTable: no valid columns to restore for {}", prefKey);
            return false;
        }

        // Check if reordering is actually needed
        boolean needsReorder = false;
        int currentColumnCount = columnModel.getColumnCount();
        if (orderedColumns.size() != currentColumnCount) {
            needsReorder = true;
        } else {
            for (int i = 0; i < orderedColumns.size(); i++) {
                if (columnModel.getColumn(i) != orderedColumns.get(i)) {
                    needsReorder = true;
                    break;
                }
            }
        }

        if (!needsReorder) {
            if (log.isTraceEnabled()) log.trace("restoreTable: columns already in correct order for {}", prefKey);
            // Still apply widths even if order is correct
            applyColumnWidths(detailsList);
            applyConstraints();
            return true;
        }

        // add in any columns that were not found to the end
        Enumeration<TableColumn> columns = getColumnModel().getColumns();
        while (columns.hasMoreElements()) {
            TableColumn column = columns.nextElement();
            if (!orderedColumns.contains(column)) {
                log.trace("restoreColumnOrder: adding: {}", column.getHeaderValue());
                orderedColumns.add(column);
            }
        }

        // Remove all columns
        while (columnModel.getColumnCount() > 0) {
            columnModel.removeColumn(columnModel.getColumn(0));
        }

        // Re-add in saved order
        for (TableColumn column : orderedColumns) {
            columnModel.addColumn(column);
        }

        applyColumnWidths(detailsList);
        applyConstraints();
        log.debug("restoreTable: restored {} columns for {}", orderedColumns.size(), prefKey);
        return true;
    }

    private void applyColumnWidths(List<ColumnDetails> detailsList) {
        for (ColumnDetails details : detailsList) {
            if (details.name == null) continue;
            TableColumn column = getColumnByName(details.name);
            if (column != null && details.width >= MIN_COLUMN_WIDTH && details.width <= MAX_COLUMN_WIDTH) {
                log.trace("applyColumnWidths: setting width {} for column '{}'", details.width, details.name);
                column.setPreferredWidth(details.width);
            }
        }
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
        if (log.isTraceEnabled()) log.trace("getColumnByName: NOT_FOUND:{}", searchName);
        return null;
    }

    public void setPreferredColWidth(String colName, int preferredWidth) {
        TableColumn column = getColumnByName(colName);
        if (column == null) return;
        column.setPreferredWidth(preferredWidth);
    }

    public void setMaxColWidth(String colName, int maxWidth) {
        maxWidthByName.put(colName, maxWidth);
        TableColumn column = getColumnByName(colName);
        if (column == null) return;
        column.setMaxWidth(maxWidth);
    }

    public void setMinColWidth(String colName, int minWidth) {
        minWidthByName.put(colName, minWidth);
        TableColumn column = getColumnByName(colName);
        if (column == null) return;
        column.setMinWidth(minWidth);
    }

    /**
     * Re-apply registered min/max width constraints. Called after restoreTable so constraints
     * survive structure changes that recreate TableColumn instances.
     */
    private void applyConstraints() {
        for (Map.Entry<String, Integer> e : maxWidthByName.entrySet()) {
            TableColumn column = getColumnByName(e.getKey());
            if (column != null) column.setMaxWidth(e.getValue());
        }
        for (Map.Entry<String, Integer> e : minWidthByName.entrySet()) {
            TableColumn column = getColumnByName(e.getKey());
            if (column != null) column.setMinWidth(e.getValue());
        }
    }

    public void saveTable() {
        if (prefKey == null) return;

        try {
            List<ColumnDetails> detailList = new ArrayList<>();
            TableColumnModel columnModel = getColumnModel();

            // Save columns in display order
            for (int i = 0; i < columnModel.getColumnCount(); i++) {
                TableColumn column = columnModel.getColumn(i);
                ColumnDetails details = new ColumnDetails();
                details.name = column.getHeaderValue().toString();
                details.modelPos = column.getModelIndex();
                // Validate and clamp width to reasonable range
                details.width = Math.max(MIN_COLUMN_WIDTH, Math.min(column.getWidth(), MAX_COLUMN_WIDTH));

                if (details.name == null) {
                    log.debug("saveTable: skipping column with null name at position {} ({})", i, prefKey);
                    continue;
                }
                detailList.add(details);
            }

            if (detailList.isEmpty()) {
                log.warn("saveTable: no columns to save for {}", prefKey);
                return;
            }

            Preferences prefs = Preferences.userRoot();
            prefs.put(prefKey + "-details", GsonHelper.toJson(detailList));
            prefs.flush(); // Ensure written to disk
            if (log.isTraceEnabled()) log.trace("saveTable: successfully saved {} columns for {}", detailList.size(), prefKey);

        } catch (Exception e) {
            log.error("saveTable: failed to save state for {}: {}", prefKey, e.getMessage());
        }
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
