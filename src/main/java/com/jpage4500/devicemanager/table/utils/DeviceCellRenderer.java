package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.table.DeviceTableModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.Highlighter;
import java.awt.*;

public class DeviceCellRenderer extends JTextField implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(DeviceCellRenderer.class);

    private Highlighter.HighlightPainter highlightPainter;
    private Highlighter.HighlightPainter highlightPainter2;
    private boolean isHighlighted = false;

    public DeviceCellRenderer() {
        setOpaque(true);
        setEditable(false);
        Border border = new EmptyBorder(0, 5, 0, 0);
        setBorder(border);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        Device device = (Device) object;
        DeviceTableModel model = (DeviceTableModel) table.getModel();

        String text = model.deviceValue(device, column);
        setText(text);

        DeviceTableModel.Columns columnType = model.getColumnType(column);
        if (columnType == DeviceTableModel.Columns.FREE) {
            setHorizontalAlignment(SwingConstants.RIGHT);
        } else {
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        boolean isTableFocused = table.hasFocus();
        Color textColor = isSelected && isTableFocused ? Color.WHITE : Color.BLACK;
        Color backgroundColor = isSelected ? table.getSelectionBackground() : table.getBackground();
        if (!device.isOnline) {
            textColor = isSelected && isTableFocused ? Color.WHITE : Color.GRAY;
            //backgroundColor = isSelected ? Color.DARK_GRAY : Color.LIGHT_GRAY;
        }

        setForeground(textColor);
        setBackground(backgroundColor);

        // TODO: offer search to devices table
//        int highlightStartPos = -1;
//        String searchText = model.getSearchText();
//        if (TextUtils.length(searchText) > 1 && text != null) {
//            highlightStartPos = TextUtils.indexOfIgnoreCase(text, searchText);
//        }
//
//        Highlighter highlighter = getHighlighter();
//        boolean doHighlight = highlightStartPos >= 0;
//        if (doHighlight || isHighlighted) {
//            // something changed..
//            highlighter.removeAllHighlights();
//
//            if (doHighlight) {
//                isHighlighted = true;
//                if (highlightPainter == null) {
//                    highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
//                    highlightPainter2 = new DefaultHighlighter.DefaultHighlightPainter(new Color(251, 109, 8));
//                }
//                Highlighter.HighlightPainter highlight = isSelected ? highlightPainter2 : highlightPainter;
//                try {
//                    highlighter.addHighlight(highlightStartPos, highlightStartPos + searchText.length(), highlight);
//                } catch (BadLocationException e) {
//                    log.error("BadLocationException: {}", e.getMessage());
//                }
//            }
//        }

        return this;
    }
}