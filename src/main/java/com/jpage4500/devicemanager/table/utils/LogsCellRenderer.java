package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;

public class LogsCellRenderer extends JTextField implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(LogsCellRenderer.class);

    private final static Color verboseColor = new Color(0, 38, 255, 255);
    private final static Color debugColor = new Color(0, 0, 0, 255);
    private final static Color infoColor = new Color(24, 134, 0, 255);
    private final static Color warnColor = new Color(251, 109, 8, 255);
    private final static Color errorColor = new Color(255, 0, 0, 255);

    private Highlighter.HighlightPainter highlightPainter;
    private Highlighter.HighlightPainter highlightPainter2;
    private boolean isHighlighted = false;

    public LogsCellRenderer() {
        setOpaque(true);
        setEditable(false);
        Border border = new EmptyBorder(0, 10, 0, 0);
        setBorder(border);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        LogEntry logEntry = (LogEntry) object;
        LogsTableModel model = (LogsTableModel) table.getModel();
        // convert table column to model column
        row = table.convertRowIndexToModel(row);
        column = table.convertColumnIndexToModel(column);
        String text = model.getTextValue(row, column);
        setText(text);

        int highlightStartPos = -1;
        String searchText = model.getSearchText();
        if (TextUtils.length(searchText) > 1 && text != null) {
            highlightStartPos = TextUtils.indexOfIgnoreCase(text, searchText);
        }

        Highlighter highlighter = getHighlighter();
        boolean doHighlight = highlightStartPos >= 0;
        if (doHighlight || isHighlighted) {
            // something changed..
            highlighter.removeAllHighlights();

            if (doHighlight) {
                isHighlighted = true;
                if (highlightPainter == null) {
                    highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW);
                    highlightPainter2 = new DefaultHighlighter.DefaultHighlightPainter(new Color(251, 109, 8));
                }
                Highlighter.HighlightPainter highlight = isSelected ? highlightPainter2 : highlightPainter;
                try {
                    highlighter.addHighlight(highlightStartPos, highlightStartPos + searchText.length(), highlight);
                } catch (BadLocationException e) {
                    log.error("BadLocationException: {}", e.getMessage());
                }
            }
        }

        Color textColor = isSelected ? Color.WHITE : Color.BLACK;
        if (!isSelected && logEntry.level != null) {
            switch (logEntry.level) {
                case "V":
                    textColor = verboseColor;
                    break;
                case "D":
                    textColor = debugColor;
                    break;
                case "I":
                    textColor = infoColor;
                    break;
                case "W":
                    textColor = warnColor;
                    break;
                case "E":
                    textColor = errorColor;
                    break;
            }
        }
        setForeground(textColor);
        setBackground(isSelected ? table.getSelectionBackground() : Color.WHITE);

        return this;
    }
}