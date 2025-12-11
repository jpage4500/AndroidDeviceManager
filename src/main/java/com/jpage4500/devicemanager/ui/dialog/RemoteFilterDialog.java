package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.data.LogFilterEntry;
import com.jpage4500.devicemanager.table.utils.LogFilterRenderer;
import com.jpage4500.devicemanager.ui.ViewLogsScreen;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.TextUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for selecting a log filter when starting remote log streaming.
 * Remote devices require a filter to reduce bandwidth and improve performance.
 */
public class RemoteFilterDialog extends JPanel {
    private final Component parent;
    private JList<LogFilter> filterList;
    private HintTextField customField;
    private List<LogFilter> selectableFilters;

    /**
     * Show dialog to prompt user to select or create a filter for remote logging.
     *
     * @param parent Parent component (typically ViewLogsScreen)
     * @param device The remote device being logged
     * @return Combined filter expression string, or null if user canceled
     */
    public static String showFilterDialog(Component parent, Device device) {
        RemoteFilterDialog dialog = new RemoteFilterDialog(parent, device);
        JButton okBtn = DialogHelper.createDialogButton("OK");
        JButton newBtn = DialogHelper.createDialogButton("New...");
        JButton cancelBtn = DialogHelper.createDialogButton("Cancel");

        int rc = DialogHelper.showCustomDialog(parent, dialog,
            "Select Remote Log Filter - " + device.getDisplayName(),
            new Object[]{okBtn, newBtn, cancelBtn});

        if (rc == 0) { // OK button
            String built = dialog.buildFilterExpression();
            if (TextUtils.isEmpty(built)) {
                DialogHelper.showDialog(parent, "Invalid Filter", "Please select or enter a valid filter.", true);
                // recursively show dialog again
                return showFilterDialog(parent, device);
            }
            return built;
        } else if (rc == 1) { // New button
            dialog.handleNewFilter();
            // recursively show dialog again to continue selection
            return showFilterDialog(parent, device);
        }

        return null; // Cancel or closed
    }

    private RemoteFilterDialog(Component parent, Device device) {
        this.parent = parent;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // info label
        JLabel info = new JLabel("Remote log streaming requires a filter. Select existing or enter custom.");
        add(info, BorderLayout.NORTH);

        // build list of selectable filters (exclude 'All Messages' and separators)
        selectableFilters = new ArrayList<>();
        rebuildFilterList();

        // filter list
        filterList = new JList<>(selectableFilters.toArray(new LogFilter[0]));
        filterList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        filterList.setCellRenderer(new LogFilterRenderer());
        JScrollPane listScroll = new JScrollPane(filterList);
        listScroll.setPreferredSize(new Dimension(260, 180));

        // custom filter text field
        customField = new HintTextField("Custom filter (ex: tag:MyTag)", null);

        // center panel with list and custom field
        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(listScroll, BorderLayout.CENTER);
        center.add(customField, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
    }

    private void rebuildFilterList() {
        selectableFilters.clear();
        for (LogFilter f : ViewLogsScreen.getSystemFilters()) {
            if (f.filterList != null && !f.filterList.isEmpty()) {
                selectableFilters.add(f);
            }
        }
        for (LogFilter f : ViewLogsScreen.getUserFilters()) {
            if (f.filterList != null && !f.filterList.isEmpty()) {
                selectableFilters.add(f);
            }
        }
        selectableFilters.sort((a, b) -> TextUtils.compareToIgnoreCase(a.name, b.name));
    }

    private void handleNewFilter() {
        LogFilter created = AddFilterDialog.showAddFilterDialog(parent, null);
        if (created != null && created.filterList != null && !created.filterList.isEmpty()) {
            ViewLogsScreen.addFilter(null, created);
            rebuildFilterList();
            filterList.setListData(selectableFilters.toArray(new LogFilter[0]));

            // select the newly created filter
            for (int i = 0; i < selectableFilters.size(); i++) {
                if (TextUtils.equals(selectableFilters.get(i).name, created.name)) {
                    filterList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    /**
     * Build combined filter expression from selected filters and custom text.
     *
     * @return Combined filter expression string using AND (&&) logic
     */
    private String buildFilterExpression() {
        List<LogFilter> selected = filterList.getSelectedValuesList();
        String customText = customField.getCleanText();

        StringBuilder sb = new StringBuilder();

        // add selected filter expressions
        if (selected != null) {
            for (LogFilter f : selected) {
                if (f == null || f.filterList == null || f.filterList.isEmpty()) {
                    continue;
                }
                for (LogFilterEntry entry : f.filterList) {
                    if (!sb.isEmpty()) {
                        sb.append(" && ");
                    }
                    sb.append(entry.toString());
                }
            }
        }

        // add custom text filter
        if (TextUtils.notEmpty(customText)) {
            LogFilter cf;
            if (TextUtils.indexOf(customText, ':') >= 0) {
                cf = LogFilter.parse(customText);
            } else {
                cf = LogFilter.parse("*:*" + customText + "*");
            }

            if (cf != null && cf.filterList != null) {
                for (LogFilterEntry entry : cf.filterList) {
                    if (!sb.isEmpty()) {
                        sb.append(" && ");
                    }
                    sb.append(entry.toString());
                }
            }
        }

        return sb.toString();
    }
}

