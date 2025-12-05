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

    private final DefaultListModel<Operation> listModel = new DefaultListModel<>();
    private final JList<Operation> operationsList;
    private final JDialog dialog;
    private final JLabel emptyLabel;
    private final JButton clearResultsButton;

    public static class Operation {
        public int id;
        public long startTime;         // when operation started
        public long endTime;           // when operation completed

        public ImageIcon icon;         // icon for this operation
        public String label;           // "Installing <filename.apk> to <device name>"
        public int progress;           // 0-100
        public String result;          // "Success" or "Failed"
        public boolean isComplete;     // progress == 100
    }

    /**
     * Create an ActivityDialog panel and optionally wrap it in a modeless dialog.
     */
    public ActivityDialog(Component component) {
        setLayout(new BorderLayout());

        // Create "No activities" label for empty state
        emptyLabel = new JLabel("No activities");
        emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyLabel.setHorizontalAlignment(JLabel.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(24f));

        // Create JList with custom renderer
        operationsList = new JList<>(listModel);
        operationsList.setOpaque(false);
        operationsList.setCellRenderer(new OperationCellRenderer());
        operationsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        operationsList.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Add mouse listener to handle close button clicks
        operationsList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = operationsList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    Operation op = listModel.getElementAt(index);
                    if (op.isComplete) {
                        // Check if click is on the close button (right side of the cell)
                        Rectangle cellBounds = operationsList.getCellBounds(index, index);
                        int closeButtonX = cellBounds.x + cellBounds.width - 40; // close button area on right
                        if (e.getX() >= closeButtonX) {
                            removeOperation(op.id);
                        }
                    }
                }
            }
        });

        // Add empty label initially
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(emptyLabel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

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

        dialog.setSize(new Dimension(540, 200));
        dialog.setMinimumSize(new Dimension(540, 200));
        dialog.setLocationRelativeTo(component);
    }

    /**
     * Add a new operation to the list.
     *
     * @param label description to show for this operation
     * @param icn  optional icon to display
     * @return generated operation id
     */
    public int addOperation(String label, Icons icn) {
        Operation operation = new Operation();
        operation.id = UUID.randomUUID().hashCode();
        operation.label = label;
        operation.progress = 0;
        operation.result = null;
        operation.isComplete = false;
        operation.startTime = System.currentTimeMillis();

        if (icn != null) {
            operation.icon = UiUtils.getImageIcon(icn, UiUtils.IMG_SIZE_TOOLBAR);
        }

        SwingUtilities.invokeLater(() -> {
            listModel.addElement(operation);
            operationsList.setSelectedIndex(listModel.getSize() - 1);

            // Show list and hide empty label when first operation is added
            if (listModel.getSize() == 1) {
                // Remove empty label panel and replace with scroll pane
                removeAll();
                JScrollPane scrollPane = new JScrollPane(operationsList);
                scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
                scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                add(scrollPane, BorderLayout.CENTER);

                JPanel footer = new JPanel(new BorderLayout());
                JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                rightPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

                clearResultsButton.setVisible(false);
                rightPanel.add(clearResultsButton);

                JButton closeButton = new JButton("Close");
                closeButton.setMargin(new Insets(6, 12, 6, 12));
                closeButton.addActionListener(e -> hideDialog());
                rightPanel.add(closeButton);
                footer.add(rightPanel, BorderLayout.EAST);
                add(footer, BorderLayout.SOUTH);

                revalidate();
                repaint();
            }

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
        SwingUtilities.invokeLater(() -> {
            // NOTE: must be run in invokeLater() because operation might not be added yet
            Operation operation = getOperation(operationId);
            if (operation == null) return;

            // ensure progress is between 0 and 100
            final int progressFinal = Math.max(0, Math.min(100, progress));

            operation.progress = progressFinal;
            operation.result = result == null ? "" : result;

            if (progressFinal == 100) {
                // -- operation complete --
                operation.endTime = System.currentTimeMillis();
                long elapsed = operation.endTime - operation.startTime;
                String time = Utils.formatTime(elapsed);
                operation.result += " (" + time + ")";
                operation.isComplete = true;

                // Show Clear Results button when first operation completes
                clearResultsButton.setVisible(true);
            }

            // Trigger repaint by finding and updating the list model
            int index = listModel.indexOf(operation);
            if (index >= 0) {
                listModel.set(index, operation);
            }
        });
    }

    private Operation getOperation(int operationId) {
        for (int i = 0; i < listModel.size(); i++) {
            Operation op = listModel.getElementAt(i);
            if (op.id == operationId) {
                return op;
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
            listModel.removeElement(operation);

            // Show empty label if list is now empty
            if (listModel.isEmpty()) {
                removeAll();
                JPanel centerPanel = new JPanel(new BorderLayout());
                centerPanel.add(Box.createVerticalGlue(), BorderLayout.NORTH);
                JPanel labelPanel = new JPanel(new BorderLayout());
                labelPanel.add(Box.createHorizontalGlue(), BorderLayout.WEST);
                labelPanel.add(emptyLabel, BorderLayout.CENTER);
                labelPanel.add(Box.createHorizontalGlue(), BorderLayout.EAST);
                centerPanel.add(labelPanel, BorderLayout.CENTER);
                centerPanel.add(Box.createVerticalGlue(), BorderLayout.SOUTH);
                add(centerPanel, BorderLayout.CENTER);
                clearResultsButton.setVisible(false);
                revalidate();
                repaint();
            }

            adjustDialogSizeToFit();
        });
    }

    /**
     * Clear all completed operations (progress = 100%)
     */
    private void clearCompletedOperations() {
        // create a separate List to avoid ConcurrentModificationException
        List<Operation> removeList = new ArrayList<>();
        for (int i = 0; i < listModel.size(); i++) {
            Operation operation = listModel.getElementAt(i);
            if (operation.endTime > 0) removeList.add(operation);
        }
        removeList.forEach(operation -> removeOperation(operation.id));
    }

    /**
     * Show the dialog (makes it visible)
     */
    public void showDialog() {
        SwingUtilities.invokeLater(() -> {
            dialog.setVisible(true);
            dialog.toFront();
        });
    }

    /**
     * Hide the dialog (keeps it in memory so it can be shown again)
     */
    public void hideDialog() {
        SwingUtilities.invokeLater(() -> dialog.setVisible(false));
    }

    /**
     * Check if the dialog is currently visible
     */
    public boolean isDialogVisible() {
        return dialog.isVisible();
    }

    /**
     * Try to size the dialog to fit all operations without scrollbars.
     * Dynamically adjusts height based on number of items with min/max constraints.
     */
    private void adjustDialogSizeToFit() {
        int itemCount = listModel.getSize();

        // Constants for height calculation
        final int MIN_HEIGHT = 200;
        final int MAX_HEIGHT = 600;
        final int ITEM_HEIGHT = 100; // approximate height per item (including padding)
        final int FOOTER_HEIGHT = 60; // height for footer buttons
        final int PADDING = 60; // additional padding

        // Calculate desired height based on number of items
        int contentHeight = (itemCount * ITEM_HEIGHT) + FOOTER_HEIGHT + PADDING;

        // Clamp to min/max range
        int targetHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, contentHeight));

        // Keep width consistent
        int targetWidth = 540;

        dialog.setSize(new Dimension(targetWidth, targetHeight));
        dialog.validate();
    }

    /**
     * Custom ListCellRenderer for Operation items
     */
    private static class OperationCellRenderer extends JPanel implements ListCellRenderer<Operation> {
        private final JLabel iconLabel;
        private final JLabel titleLabel;
        private final JProgressBar progressBar;
        private final JLabel resultLabel;
        private final JButton closeButton;

        public OperationCellRenderer() {
            setLayout(new BorderLayout());
            Border innerPadding = BorderFactory.createEmptyBorder(8, 8, 8, 8);
            Border rounded = new LineBorder(new Color(180, 180, 180), 1, true);
            setBorder(BorderFactory.createCompoundBorder(rounded, innerPadding));

            // Icon on the left
            iconLabel = new JLabel();
            iconLabel.setPreferredSize(new Dimension(UiUtils.IMG_SIZE_TOOLBAR + 8, 64));
            JPanel iconPanel = new JPanel(new BorderLayout());
            iconPanel.setOpaque(false);
            iconPanel.add(iconLabel, BorderLayout.WEST);
            iconPanel.add(Box.createHorizontalStrut(8), BorderLayout.EAST);
            add(iconPanel, BorderLayout.WEST);

            // Content panel in center
            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setOpaque(false);

            // Title row with label and close button
            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);
            titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            titleLabel = new JLabel();
            titleLabel.setPreferredSize(new Dimension(0, 20));
            titleLabel.setMinimumSize(new Dimension(100, 20));
            titleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            titleRow.add(titleLabel, BorderLayout.CENTER);

            closeButton = new JButton("✕");
            closeButton.setMargin(new Insets(2, 6, 2, 6));
            closeButton.setVisible(false);
            closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            titleRow.add(closeButton, BorderLayout.EAST);

            contentPanel.add(titleRow);
            contentPanel.add(Box.createVerticalStrut(10));

            // Progress bar - direct add, NO wrapper, fixed 10px height
            progressBar = new JProgressBar(0, 100);
            progressBar.setPreferredSize(new Dimension(400, 10));
            progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
            progressBar.setMinimumSize(new Dimension(100, 10));
            contentPanel.add(progressBar);
            contentPanel.add(Box.createVerticalStrut(10));

            // Result label - wrap in panel to ensure visibility and left alignment
            JPanel resultPanel = new JPanel(new BorderLayout());
            resultPanel.setOpaque(false);
            resultPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

            resultLabel = new JLabel("");
            resultLabel.setPreferredSize(new Dimension(100, 20));
            resultLabel.setMinimumSize(new Dimension(100, 20));
            resultLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            resultPanel.add(resultLabel, BorderLayout.CENTER);

            contentPanel.add(resultPanel);

            add(contentPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Operation> list, Operation operation, int index, boolean isSelected, boolean cellHasFocus) {
            if (operation == null) return this;

            iconLabel.setIcon(operation.icon);
            titleLabel.setText(operation.label);

            progressBar.setValue(operation.progress);

            String resultText = operation.result == null ? "" : operation.result;
            resultLabel.setText(resultText);

            // Show close button when complete
            closeButton.setVisible(operation.isComplete);

            // Only set tooltip if label or result text is truncated
            boolean isLabelTruncated = isTextTruncated(titleLabel);
            boolean isResultTruncated = isTextTruncated(resultLabel);
            if (isLabelTruncated || isResultTruncated) {
                StringBuilder tooltip = new StringBuilder("<html>");
                if (isLabelTruncated) {
                    tooltip.append(operation.label);
                }
                if (isLabelTruncated && isResultTruncated) {
                    tooltip.append("<br><br>");
                }
                if (isResultTruncated) {
                    tooltip.append(resultText);
                }
                tooltip.append("</html>");
                setToolTipText(tooltip.toString());
            } else {
                setToolTipText(null);
            }

            return this;
        }

        private boolean isTextTruncated(JLabel label) {
            String text = label.getText();
            if (text == null || text.isEmpty()) return false;
            FontMetrics fm = label.getFontMetrics(label.getFont());
            int textWidth = fm.stringWidth(text);
            int labelWidth = label.getWidth();
            // If width is not set yet (first render), use preferred width
            if (labelWidth == 0) {
                labelWidth = label.getPreferredSize().width;
            }
            return textWidth > labelWidth;
        }
    }
}
