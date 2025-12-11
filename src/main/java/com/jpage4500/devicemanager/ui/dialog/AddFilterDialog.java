package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.data.LogFilterEntry;
import com.jpage4500.devicemanager.data.LogFilterExpression;
import com.jpage4500.devicemanager.table.LogsTableModel;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.utils.ArrayUtils;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
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
    public static final int MAX_EXPRESSIONS = 5;

    private final LogFilter filter;
    private JButton okButton;

    public HintTextField nameTextField;
    public List<FilterPanel> panelList;
    private JButton addButton;

    public static LogFilter showAddFilterDialog(Component frame, LogFilter filter) {
        AddFilterDialog dialog = new AddFilterDialog(filter);
        String okText = filter == null ? "Save" : "Update";
        dialog.okButton = DialogHelper.createDialogButton(okText);
        JButton cancelButton = DialogHelper.createDialogButton("Cancel");

        int rc = DialogHelper.showCustomDialog(frame, dialog, "Add Filter",
                new Object[]{dialog.okButton, cancelButton});
        boolean isOk = (rc == JOptionPane.YES_OPTION);
        if (isOk) {
            return dialog.getLogFilter();
        } else {
            return null;
        }
    }

    private AddFilterDialog(LogFilter filter) {
        this.filter = filter;
        panelList = new ArrayList<>();

        initalizeUi();
    }

    /**
     * create new LogFilter from dialog
     */
    private LogFilter getLogFilter() {
        LogFilter filter = new LogFilter();
        filter.name = nameTextField.getCleanText();
        filter.filterList = new ArrayList<>();
        for (FilterPanel panel : panelList) {
            String valueText = panel.valueField.getCleanText();
            if (TextUtils.notEmpty(valueText)) {
                LogFilterEntry expr = new LogFilterEntry();
                expr.column = (LogsTableModel.Columns) panel.columnComboBox.getSelectedItem();
                expr.expression = (LogFilterExpression) panel.expressionComboBox.getSelectedItem();
                expr.value = valueText;
                filter.filterList.add(expr);
            }
        }
        return filter;
    }

    protected void initalizeUi() {
        setLayout(new MigLayout("fillx", "[][][][]"));

        JLabel nameLabel = new JLabel("Filter Name:");
        add(nameLabel, "span 3, grow, wrap");

        nameTextField = new HintTextField("Filter Name", text -> {
            enableOkButton();
        });
        add(nameTextField, "span 3, grow, wrap 20px");

        JLabel exprLabel = new JLabel("Filter Expression:");
        add(exprLabel, "span 3, grow, wrap");

        if (filter != null) {
            nameTextField.setText(filter.name);

            for (LogFilterEntry expression : filter.filterList) {
                addFilter(expression);
            }
        } else {
            nameTextField.setText("New Filter");
            nameTextField.selectAll();

            addFilter(null);
        }

        // add/update filter
        addButton = new JButton();
        addButton.setIcon(UiUtils.getImageIcon(Icons.ADD, UiUtils.IMG_SIZE_ICON));
        addButton.addActionListener(e -> handleAddClicked());
        addAddFilterButton();
    }

    private void enableOkButton() {
        String name = nameTextField.getCleanText();
        // at least 1 expression must be set
        int numFilters = 0;
        for (FilterPanel panel : panelList) {
            String valueText = panel.valueField.getCleanText();
            if (TextUtils.notEmpty(valueText)) numFilters++;
        }
        if (okButton != null) okButton.setEnabled(TextUtils.notEmpty(name) && numFilters > 0);
    }

    private void addAddFilterButton() {
        if (addButton.getParent() != null) {
            remove(addButton);
        }
        add(addButton, "skip 3, wrap");
    }

    private void addFilter(LogFilterEntry expression) {
        FilterPanel filterPanel = new FilterPanel();

        add(filterPanel.columnComboBox, "");
        add(filterPanel.expressionComboBox, "");
        add(filterPanel.valueField, "grow, wmin 150");
        add(filterPanel.deleteButton, "wrap");

        if (expression != null) {
            filterPanel.columnComboBox.setSelectedItem(expression.column);
            filterPanel.expressionComboBox.setSelectedItem(expression.expression);
            filterPanel.valueField.setText(expression.value);
        }

        filterPanel.deleteButton.addActionListener(actionEvent -> deletePanel(filterPanel));

        filterPanel.valueField.addTextListener(text -> {
            enableOkButton();
        });

        panelList.add(filterPanel);

        enableOkButton();
        updateDialogSize();
    }

    private void updateDialogSize() {
        // make dialog taller
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.pack();
            Dimension size = window.getSize();
            size.height += 30;
            window.setSize(size);
        }
    }

    private void handleAddClicked() {
        remove(addButton);
        addFilter(null);
        // allow up to MAX_EXPRESSIONS expressions
        if (panelList.size() < MAX_EXPRESSIONS) {
            addAddFilterButton();
        }
    }

    private void deletePanel(FilterPanel filterPanel) {
        // prevent deleting all expressions
        if (panelList.size() <= 1) return;

        remove(filterPanel.columnComboBox);
        remove(filterPanel.expressionComboBox);
        remove(filterPanel.valueField);
        remove(filterPanel.deleteButton);

        panelList.remove(filterPanel);

        if (panelList.size() < MAX_EXPRESSIONS) {
            addAddFilterButton();
        }

        enableOkButton();
        updateDialogSize();
    }

    public static class FilterPanel {
        private JComboBox<LogsTableModel.Columns> columnComboBox;
        private JComboBox<LogFilterExpression> expressionComboBox;
        private HintTextField valueField;
        private JButton deleteButton;

        public FilterPanel() {
            // column
            LogsTableModel.Columns[] columns = LogsTableModel.Columns.values();
            columnComboBox = new JComboBox<>(columns);
            int colIndex = ArrayUtils.indexOf(columns, LogsTableModel.Columns.TAG);
            columnComboBox.setSelectedIndex(colIndex);

            LogFilterExpression[] expressions = LogFilterExpression.values();
            expressionComboBox = new JComboBox<>(expressions);
            int exprIndex = ArrayUtils.indexOf(expressions, LogFilterExpression.STARTS_WITH);
            expressionComboBox.setSelectedIndex(exprIndex);

            deleteButton = new JButton();
            deleteButton.setIcon(UiUtils.getImageIcon(Icons.DELETE, UiUtils.IMG_SIZE_ICON));

            valueField = new HintTextField("Value", null);
        }
    }

}

