package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
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
    private TooltipListener tooltipListener;
    private DoubleClickListener doubleClickListener;
    private PopupMenuListener popupMenuListener;
    private JScrollPane scrollPane;

    private int selectedColumn = -1;

    private boolean showBackground;
    private String emptyText;
    private Image emptyImage;

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

        showBackground = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true);

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
            }
        });
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
                    Font font = graphics.getFont().deriveFont(Font.BOLD, 22);
                    graphics.setFont(font);
                    int textW = graphics.getFontMetrics().stringWidth(emptyText);
                    int width = getWidth();
                    int headerH = getTableHeader().getHeight();
                    int x = width / 2 - (textW / 2);
                    int y = headerH * 2;
                    if (x >= 0 && y >= 0) {
                        graphics.drawString(emptyText, x, y);
                    }
                }
            }
        };
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

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

        dataModel.addTableModelListener(tableModelEvent -> {
            showBackground = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true);
            scrollPane.repaint();
        });
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
            TableColumn column = header.getColumnModel().getColumn(col);
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
        Object header;
        int width;
        int maxWidth;
        int userPos;
        int modelPos;
    }

    public boolean restore() {
        if (prefKey == null) return false;
        Preferences prefs = Preferences.userRoot();
        String detailsStr = prefs.get(prefKey + "-details", null);
        if (detailsStr == null) return false;
        List<ColumnDetails> detailsList = GsonHelper.stringToList(detailsStr, ColumnDetails.class);
        for (int i = 0; i < detailsList.size(); i++) {
            ColumnDetails details = detailsList.get(i);
            // lookup column by name
            TableColumn column = getColumnByName(details.header);
            if (column == null) continue;
            //log.trace("restore: {}: w:{}, max:{}", details.header, details.width, details.maxWidth);
            column.setPreferredWidth(details.width);
            if (details.maxWidth > 0) column.setMaxWidth(details.maxWidth);

            int modelIndex = column.getModelIndex();
            if (modelIndex != details.userPos) {
                log.trace("restore: moving: {}, from:{}, to:{}", details.header, modelIndex, details.userPos);
                getColumnModel().moveColumn(modelIndex, details.userPos);
            }
        }

//        TableColumnModel columnModel = getColumnModel();
//        for (ColumnDetails details : detailsList) {
//            if (details.modelPos != details.userPos) {
//                log.trace("restore: move:{} to:{}", details.modelPos, details.userPos);
//                columnModel.moveColumn(details.modelPos, details.userPos);
//            }
//        }
        return true;
    }

    /**
     * get column by header name (NOTE: will return null and not throw an Exception when not found)
     */
    public TableColumn getColumnByName(Object header) {
        Enumeration<TableColumn> columns = getColumnModel().getColumns();
        Iterator<TableColumn> iterator = columns.asIterator();
        while (iterator.hasNext()) {
            TableColumn column = iterator.next();
            if (column.getHeaderValue().equals(header)) {
                return column;
            }
        }
        return null;
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
            int maxWidth = column.getMaxWidth();
            // only need to set maxWidth if one is defined (and it won't typicically be very large if it is)
            if (maxWidth < 500) details.maxWidth = maxWidth;
            detailList.add(details);
            //log.trace("persist: {}", GsonHelper.toJson(details));
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

            arrowUpIcon = UiUtils.getImageIcon("arrow_down.png", 15);
            arrowDownIcon = UiUtils.getImageIcon("arrow_up.png", 15);

            setBackground(COLOR_HEADER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        if (popupMenuListener != null) {
                            Point point = e.getPoint();
                            int column = columnAtPoint(point);
                            // convert table row/col to model row/col
                            column = convertColumnIndexToModel(column);
                            // NOTE: row fixed at -1 for header
                            JPopupMenu popupMenu = popupMenuListener.getPopupMenu(-1, column);
                            if (popupMenu != null) popupMenu.show(e.getComponent(), e.getX(), e.getY());
                        }
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
            if (tooltipListener == null) return null;
            Point p = e.getPoint();
            int col = columnAtPoint(p);
            return tooltipListener.getToolTipText(-1, col);
        }
    }

}
