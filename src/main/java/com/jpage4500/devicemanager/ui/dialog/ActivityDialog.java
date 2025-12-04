package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.utils.UiUtils;
import com.jpage4500.devicemanager.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * shows a list of recent operations (install, copy)
 */
public class ActivityDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(ActivityDialog.class);

    private final List<Operation> operationList = new ArrayList<>();
    private final JPanel operationsPanel = new JPanel();
    private final JDialog dialog;
    private final JLabel emptyLabel;
    private final JButton clearResultsButton;

    public static class Operation {
        public int id;
        public long startTime;         // when operation started
        public long endTime;           // when operation completed

        public JLabel icon;            // icon for this operation
        public JLabel label;           // "Installing <filename.apk> to <device name>"
        public JProgressBar progressBar;
        public JLabel resultLabel;     // "Success" or "Failed"
        public JButton closeButton;    // close button (visible when complete)
        public JPanel container;
    }

    /**
     * Create an OperationDialog panel and optionally wrap it in a modeless dialog.
     * If component and title are provided, a dialog will be created and shown.
     */
    public ActivityDialog(Component component) {
        setLayout(new BorderLayout());
        operationsPanel.setLayout(new BoxLayout(operationsPanel, BoxLayout.Y_AXIS));
        operationsPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Create "No activities" label
        emptyLabel = new JLabel("No activities");
        emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(24f));
        operationsPanel.add(emptyLabel);

        JScrollPane scrollPane = new JScrollPane(operationsPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        Window parent = SwingUtilities.getWindowAncestor(component);
        dialog = new JDialog(parent, "Activity", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        dialog.setContentPane(this);

        // add bottom-right buttons with padding
        JPanel footer = new JPanel(new BorderLayout());
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        clearResultsButton = new JButton("Clear Results");
        clearResultsButton.setMargin(new Insets(6, 12, 6, 12));
        clearResultsButton.addActionListener(e -> clearCompletedOperations());
        clearResultsButton.setVisible(false); // hidden until there are completed operations
        rightPanel.add(clearResultsButton);

        JButton closeButton = new JButton("Close");
        // keep default LAF border; only add extra internal padding
        closeButton.setMargin(new Insets(6, 12, 6, 12));
        closeButton.addActionListener(e -> hideDialog());
        rightPanel.add(closeButton);
        footer.add(rightPanel, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        dialog.setSize(new Dimension(540, 300));
        dialog.setMinimumSize(new Dimension(540, 200));
        dialog.setLocationRelativeTo(component);
    }

    /**
     * Add a new operation row with description, progress bar, and result label.
     *
     * @param label description to show for this operation
     * @return generated operation id
     */
    public int addOperation(String label, Icons icon) {
        Operation operation = new Operation();
        operation.id = UUID.randomUUID().hashCode();

        if (icon != null) {
            ImageIcon imageIcon = UiUtils.getImageIcon(icon.getName(), UiUtils.IMG_SIZE_TOOLBAR);
            if (imageIcon != null) {
                operation.icon = new JLabel(imageIcon);
            }
        }

        operation.label = new JLabel(label);
        operation.label.setAlignmentX(Component.LEFT_ALIGNMENT);
        operation.label.setPreferredSize(new Dimension(0, 20));
        operation.label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        operation.label.setMinimumSize(new Dimension(100, 20));
        operation.label.setToolTipText(label);

        operation.progressBar = new JProgressBar(0, 100);
        operation.progressBar.setIndeterminate(true);
        operation.progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        operation.progressBar.setPreferredSize(new Dimension(0, 10));
        operation.progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
        operation.progressBar.setMinimumSize(new Dimension(100, 10));

        operation.resultLabel = new JLabel("");
        operation.resultLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        operation.resultLabel.setPreferredSize(new Dimension(0, 20));
        operation.resultLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        operation.resultLabel.setMinimumSize(new Dimension(100, 20));

        operation.startTime = System.currentTimeMillis();
        operationList.add(operation);

        JPanel row = new JPanel();
        row.setLayout(new BorderLayout());

        Border innerPadding = BorderFactory.createEmptyBorder(8, 8, 8, 8);
        Border rounded = new LineBorder(new Color(180, 180, 180), 1, true);
        row.setBorder(BorderFactory.createCompoundBorder(rounded, innerPadding));

        row.setPreferredSize(new Dimension(500, 80));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        row.setMinimumSize(new Dimension(500, 80));

        // Add icon on the left if present - with padding on the right
        if (operation.icon != null) {
            JPanel iconPanel = new JPanel(new BorderLayout());
            iconPanel.setOpaque(false);
            iconPanel.add(operation.icon, BorderLayout.WEST);
            iconPanel.add(Box.createHorizontalStrut(8), BorderLayout.EAST);
            row.add(iconPanel, BorderLayout.WEST);
        }

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        // Top row: label on left, close button on right
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        topRow.setPreferredSize(new Dimension(0, 20));
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        topRow.setMinimumSize(new Dimension(0, 20));

        // Wrapper panel for label to ensure it grows and respects parent width
        JPanel labelWrapper = new JPanel(new BorderLayout());
        labelWrapper.setOpaque(false);
        labelWrapper.add(operation.label, BorderLayout.CENTER);
        topRow.add(labelWrapper, BorderLayout.CENTER);

        operation.closeButton = new JButton("✕");
        operation.closeButton.setMargin(new Insets(2, 6, 2, 6));
        operation.closeButton.setVisible(false); // hidden until complete
        operation.closeButton.addActionListener(e -> removeOperation(operation.id));
        topRow.add(operation.closeButton, BorderLayout.EAST);

        contentPanel.add(topRow);
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(operation.progressBar);
        contentPanel.add(Box.createVerticalStrut(6));
        contentPanel.add(operation.resultLabel);

        row.add(contentPanel, BorderLayout.CENTER);
        operation.container = row;

        SwingUtilities.invokeLater(() -> {
            // Hide "No activities" label when adding first operation
            emptyLabel.setVisible(false);
            operationsPanel.add(row);
            operationsPanel.add(Box.createVerticalStrut(6));
            operationsPanel.revalidate();
            operationsPanel.repaint();
            adjustDialogSizeToFit();
        });

        return operation.id;
    }

    /**
     * Update an existing operation's progress and result.
     *
     * @param operationId id returned by addOperation
     * @param progress    0-100 progress value
     * @param result      optional result message ("Success", "Failed", error text, etc.)
     */
    public void updateOperation(int operationId, int progress, String result) {
        Operation operation = getOperation(operationId);
        if (operation == null) return;

        // ensure progress is between 0 and 100
        final int progressFinal = Math.max(0, Math.min(100, progress));

        // update UI
        SwingUtilities.invokeLater(() -> {
            operation.progressBar.setIndeterminate(false);
            operation.progressBar.setValue(progressFinal);

            String resultFinal = result == null ? "" : result;
            if (progressFinal == 100) {
                // -- operation complete --
                // append elapsed time
                operation.endTime = System.currentTimeMillis();
                long elapsed = operation.endTime - operation.startTime;
                String time = Utils.formatTime(elapsed);
                resultFinal += " (" + time + ")";

                // show close button when operation is complete
                operation.closeButton.setVisible(true);

                clearResultsButton.setVisible(true);
            }
            operation.resultLabel.setText(resultFinal);
            // Update tooltip to show full text if it might be truncated
            if (!resultFinal.isEmpty()) {
                operation.resultLabel.setToolTipText(resultFinal);
            }

            operation.container.revalidate();
            operation.container.repaint();
        });
    }

    private Operation getOperation(int operationId) {
        for (Operation operation : operationList) {
            if (operation.id == operationId) {
                return operation;
            }
        }
        return null;
    }

    /**
     * Remove a specific operation from the display
     */
    private void removeOperation(int operationId) {
        Operation operation = getOperation(operationId);
        if (operation == null) return;

        SwingUtilities.invokeLater(() -> {
            operationsPanel.remove(operation.container);
            // also remove the vertical spacer after this operation
            Component[] components = operationsPanel.getComponents();
            for (int i = 0; i < components.length; i++) {
                if (components[i] == operation.container && i + 1 < components.length) {
                    operationsPanel.remove(i + 1); // remove spacer
                    break;
                }
            }
            operationList.remove(operation);

            // Show "No activities" label if list is now empty
            if (operationList.isEmpty()) {
                operationsPanel.removeAll();
                operationsPanel.add(Box.createVerticalGlue());
                emptyLabel.setVisible(true);
                operationsPanel.add(emptyLabel);
                operationsPanel.add(Box.createVerticalGlue());
                clearResultsButton.setVisible(false);
            }

            operationsPanel.revalidate();
            operationsPanel.repaint();
            adjustDialogSizeToFit();
        });
    }

    /**
     * Clear all completed operations (progress = 100%)
     */
    private void clearCompletedOperations() {
        // create a separate List to avoid ConcurrentModificationException
        List<Operation> removeList = new ArrayList<>();
        operationList.forEach(operation -> {
            if (operation.endTime > 0) removeList.add(operation);
        });
        removeList.forEach(operation -> removeOperation(operation.id));
    }

    /**
     * Show the dialog (makes it visible)
     */
    public void showDialog() {
        if (dialog != null) {
            SwingUtilities.invokeLater(() -> {
                dialog.setVisible(true);
                dialog.toFront();
            });
        }
    }

    /**
     * Hide the dialog (keeps it in memory so it can be shown again)
     */
    public void hideDialog() {
        if (dialog != null) {
            SwingUtilities.invokeLater(() -> dialog.setVisible(false));
        }
    }

    /**
     * Check if the dialog is currently visible
     */
    public boolean isDialogVisible() {
        return dialog != null && dialog.isVisible();
    }

    /**
     * Try to size the dialog to fit all operations without scrollbars.
     * Caps height to ~50% of the screen to avoid oversized windows.
     */
    private void adjustDialogSizeToFit() {
        if (dialog == null) return;
        dialog.pack();
        // cap to screen size
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxH = (int) (screen.height * 0.5);
        Dimension pref = dialog.getSize();
        int newH = Math.min(pref.height, maxH);
        dialog.setSize(new Dimension(Math.max(pref.width, 520), newH));
        dialog.validate();
    }

}
