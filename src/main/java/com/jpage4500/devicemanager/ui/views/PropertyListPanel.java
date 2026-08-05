package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.utils.TextUtils;

import net.miginfocom.swing.MigLayout;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import java.awt.Font;

/**
 * a card of name/value rows (serial, model, OS, ..) separated by hairlines
 */
public class PropertyListPanel extends CardPanel {
    private static final int PAD_X = 14;
    private static final int PAD_Y = 6;
    // between a name and its value ("OS - 16")
    private static final int PAD_VALUE = 5;

    private boolean hasRows;

    public PropertyListPanel() {
        super(new MigLayout("wrap 1, fillx, insets 0, gapy 0", "[grow]"));
    }

    /**
     * @return true if no rows were added (eg: an offline device that hasn't reported anything yet)
     */
    public boolean isEmpty() {
        return !hasRows;
    }

    /**
     * add a "name - value" row
     * <p>
     * empty values are dropped rather than shown as a blank row - most devices don't report every
     * value (a tablet has no IMEI or carrier) and an empty row says nothing
     */
    public void addProperty(String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        addSeparatorIfNeeded();

        // both halves share one cell ("split 2") so the value sits right after its own name. giving
        // the name a column of its own would instead indent every value past the longest name here
        JLabel labelView = new JLabel(label + " -");
        labelView.setFont(labelView.getFont().deriveFont(Font.BOLD));
        labelView.setForeground(Colors.COLOR_CARD_LABEL);
        add(labelView, "split 2, gaptop " + PAD_Y + ", gapbottom " + PAD_Y + ", gapleft " + PAD_X);

        JLabel valueView = new JLabel(value);
        valueView.setForeground(Colors.COLOR_CARD_ACCENT);
        add(valueView, "growx, gapleft " + PAD_VALUE + ", gapright " + PAD_X);
    }

    /**
     * add a row of the caller's own (eg: a clickable link) with the same padding and separator as a
     * name/value row
     */
    public void addRow(JComponent row) {
        addSeparatorIfNeeded();
        add(row, "growx, gaptop " + PAD_Y + ", gapbottom " + PAD_Y + ", gapleft " + PAD_X + ", gapright " + PAD_X);
    }

    /**
     * rows are divided by a hairline; the first row doesn't get one (it would read as an edge of the
     * card rather than a divider between rows)
     */
    private void addSeparatorIfNeeded() {
        if (hasRows) {
            JPanel separator = new JPanel();
            separator.setBackground(Colors.COLOR_CARD_SEPARATOR);
            add(separator, "growx, height 1!, gapleft " + PAD_X + ", gapright " + PAD_X);
        }
        hasRows = true;
    }
}
