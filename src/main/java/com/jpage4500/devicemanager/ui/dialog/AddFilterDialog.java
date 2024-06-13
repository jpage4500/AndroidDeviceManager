package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.LogFilter;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

public class AddFilterDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(AddFilterDialog.class);

    private LogFilter logFilter;

    public static LogFilter showAddFilterDialog(Component frame, LogFilter logFilter) {
        AddFilterDialog screen = new AddFilterDialog(logFilter);
        int rc = JOptionPane.showOptionDialog(frame, screen, "Add Filter", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{}, null);
        if (rc != JOptionPane.YES_OPTION) return null;

        return screen.logFilter;
    }

    public AddFilterDialog(LogFilter logFilter) {
        logFilter = logFilter;
        if (logFilter == null) logFilter = new LogFilter();

        initalizeUi();
    }

    protected void initalizeUi() {
        setLayout(new MigLayout("fillx", "[][]"));

        add(new JLabel("Filter Name"));
        JTextField nameField = new JTextField();
        add(nameField);

        JButton addButton = new JButton("Add Filter");
        addButton.addActionListener(e -> handleAddClicked());
        add(addButton, "al right, span 2, wrap");
    }

    private void handleAddClicked() {

    }

}

