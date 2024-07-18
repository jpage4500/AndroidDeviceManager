package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.utils.ArrayUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AddFilterDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(AddFilterDialog.class);

    private LogFilter logFilter;
    private HintTextField nameTextField;
    private List<FilterPanel> panelList;

    public static LogFilter showAddFilterDialog(Component frame, LogFilter logFilter) {
        String okButton = logFilter == null ? "Save" : "Update";
        AddFilterDialog screen = new AddFilterDialog(logFilter);
        int rc = JOptionPane.showOptionDialog(frame, screen, "Add Filter", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new String[]{okButton, "Cancel"}, null);
        if (rc != JOptionPane.YES_OPTION) return null;

        // SAVE/UPDATE filter

        return screen.logFilter;
    }

    public AddFilterDialog(LogFilter logFilter) {
        if (logFilter == null) logFilter = new LogFilter();
        this.logFilter = logFilter;
        panelList = new ArrayList<>();

        initalizeUi();
    }

    protected void initalizeUi() {
        setLayout(new MigLayout("fillx", "[][][][]"));

        nameTextField = new HintTextField("Filter Name", null);
        add(nameTextField, "span 3, grow, wrap 2");

        JScrollPane scrollPane = new JScrollPane(nameTextField);
        addFilter();

        // add/update filter
        JButton addButton = new JButton();
        addButton.setIcon(UiUtils.getImageIcon("icon_add.png", 20));
        addButton.addActionListener(e -> handleAddClicked());
        //add(addButton, "skip 3, wrap");
    }

    private void addFilter() {
        FilterPanel filterPanel = new FilterPanel();

        add(filterPanel.columnComboBox, "");
        add(filterPanel.expressionComboBox, "");
        add(filterPanel.valueField, "grow, wmin 150");
        add(filterPanel.deleteButton, "wrap");
        revalidate();
        repaint();

        filterPanel.deleteButton.addActionListener(actionEvent -> {
            deletePanel(filterPanel);
        });

        panelList.add(filterPanel);
    }

    private void handleAddClicked() {
        addFilter();
    }

    private void deletePanel(FilterPanel filterPanel) {
        panelList.remove(filterPanel);
        remove(filterPanel.columnComboBox);
        remove(filterPanel.expressionComboBox);
        remove(filterPanel.valueField);
        remove(filterPanel.deleteButton);

        revalidate();
        repaint();
    }

    public static class FilterPanel {
        private JComboBox<LogsTableModel.Columns> columnComboBox;
        private JComboBox<LogFilter.Expression> expressionComboBox;
        private HintTextField valueField;
        private JButton deleteButton;

        public FilterPanel() {
            // column
            LogsTableModel.Columns[] columns = LogsTableModel.Columns.values();
            columnComboBox = new JComboBox<>(columns);
            int colIndex = ArrayUtils.indexOf(columns, LogsTableModel.Columns.TAG);
            columnComboBox.setSelectedIndex(colIndex);

            LogFilter.Expression[] expressions = LogFilter.Expression.values();
            expressionComboBox = new JComboBox<>(expressions);
            int exprIndex = ArrayUtils.indexOf(expressions, LogFilter.Expression.STARTS_WITH);
            expressionComboBox.setSelectedIndex(exprIndex);

            deleteButton = new JButton();
            deleteButton.setIcon(UiUtils.getImageIcon("icon_delete.png", 20));

            valueField = new HintTextField("Value", null);
        }
    }

}

