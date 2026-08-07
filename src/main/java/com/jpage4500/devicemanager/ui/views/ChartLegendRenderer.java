package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;

import net.miginfocom.swing.MigLayout;

import java.awt.Color;
import java.awt.Component;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/**
 * a {@link CheckBoxList} row that doubles as a chart legend: [check] [line swatch] [name]
 * <p>
 * the swatch carries the color and pattern; the name stays in normal ink so a line is never
 * identified by color alone.
 */
public class ChartLegendRenderer implements ListCellRenderer<Object> {
    private final JPanel panel = new JPanel(new MigLayout("insets 1 4 1 4, gap 4", "[][][grow]"));
    private final JCheckBox checkBox = new JCheckBox();
    private final JLabel swatchLabel = new JLabel();
    private final JLabel nameLabel = new JLabel();

    // row index -> swatch; owned by the caller so it can rebuild the list without a new renderer
    private final List<Icon> iconList;

    // the swatch only means something while each row IS a line on the chart
    private boolean showSwatch = true;

    public ChartLegendRenderer(List<Icon> iconList) {
        this.iconList = iconList;
        checkBox.setOpaque(false);
        swatchLabel.setOpaque(false);
        nameLabel.setOpaque(false);
        panel.add(checkBox);
        panel.add(swatchLabel);
        panel.add(nameLabel, "growx");
    }

    /**
     * hide the color swatches when the chart isn't drawing 1 line per row (eg. a pie, where the colors
     * key to values rather than to devices)
     */
    public void setShowSwatch(boolean showSwatch) {
        this.showSwatch = showSwatch;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        JCheckBox item = (JCheckBox) value;
        checkBox.setSelected(item.isSelected());
        checkBox.setEnabled(list.isEnabled());
        swatchLabel.setIcon(showSwatch && index >= 0 && index < iconList.size() ? iconList.get(index) : null);
        nameLabel.setText(item.getText());
        nameLabel.setFont(list.getFont());
        // NOTE: required. MigLayout caches the preferred size on this reused panel, so without it every
        // row measures the same width and JList truncates the longer names when it wraps into columns
        panel.invalidate();
        panel.setToolTipText(item.getToolTipText());
        panel.setBackground(index % 2 == 0 ? Color.WHITE : Colors.COLOR_LIGHT_GRAY);
        return panel;
    }
}
