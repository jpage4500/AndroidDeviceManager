package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.manager.DeviceManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * track results from multiple actions from multiple threads and display them in a dialog when complete
 * - enhanced to optionally show an in-progress dialog with a progress bar and failure details
 */
public class ResultWatcher {
    private Component component;
    private final int numResults;
    private AtomicInteger counter = new AtomicInteger();
    private DeviceManager.TaskListener listener;
    private final List<Result> resultList = new ArrayList<>();

    static class Result {
        String device;
        boolean isSuccess;
        String message;

        public Result(String device, boolean isSuccess, String message) {
            this.device = device;
            this.isSuccess = isSuccess;
            this.message = message;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(isSuccess ? "✅" : "❌").append(" ");
            if (TextUtils.notEmpty(device)) {
                sb.append(device);
            }
            if (TextUtils.notEmpty(message)) {
                sb.append(": ");
                sb.append(message);
            }
            return sb.toString();
        }
    }

    // progress fields
    private JDialog progressDialog;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private JTextArea descArea;
    private JScrollPane descScroll;
    private JButton closeButton;
    private boolean showProgress;

    /**
     * when complete show a dialog with results
     */
    public ResultWatcher(Component component, int numResults) {
        this.component = component;
        this.numResults = numResults;
    }

    /**
     * when complete calls listener
     */
    public ResultWatcher(Component component, int numResults, DeviceManager.TaskListener listener) {
        this.component = component;
        this.numResults = numResults;
        this.listener = listener;
    }

    /**
     * Enable a modeless progress dialog that updates as results come in. If any failures occur they're listed.
     *
     * @param title       dialog title
     * @param description optional description shown above the progress bar
     */
    public void showProgressDialog(String title, String description) {
        if (numResults <= 0 || showProgress) return; // nothing to show or already showing
        showProgress = true;
        SwingUtilities.invokeLater(() -> {
            Window parent = component != null ? SwingUtilities.getWindowAncestor(component) : null;
            progressDialog = new JDialog(parent, title, Dialog.ModalityType.MODELESS);
            progressDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            if (description != null) {
                JLabel descLabel = new JLabel(description);
                descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                panel.add(descLabel);
                panel.add(Box.createVerticalStrut(8));
            }

            progressBar = new JProgressBar(0, numResults);
            progressBar.setValue(0);
            progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(progressBar);
            panel.add(Box.createVerticalStrut(6));

            progressLabel = new JLabel(formatProgressText(0));
            progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(progressLabel);
            panel.add(Box.createVerticalStrut(10));

            descArea = new JTextArea(6, 50);
            descArea.setEditable(false);
            descArea.setLineWrap(true);
            descArea.setWrapStyleWord(true);
            descScroll = new JScrollPane(descArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            descScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            descScroll.setVisible(false);
            panel.add(descScroll);

            closeButton = new JButton("Close");
            closeButton.setEnabled(false);
            closeButton.addActionListener(e -> progressDialog.dispose());
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(closeButton);
            buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(Box.createVerticalStrut(6));
            panel.add(buttonPanel);

            progressDialog.setContentPane(panel);
            progressDialog.pack();
            // position near center but not cover entire app
            if (parent != null) {
                int x = parent.getX() + parent.getWidth() / 2 - progressDialog.getWidth() / 2;
                int y = parent.getY() + parent.getHeight() / 2 - progressDialog.getHeight() / 2;
                progressDialog.setLocation(Math.max(0, x), Math.max(0, y));
            } else {
                progressDialog.setLocationRelativeTo(null);
            }
            progressDialog.setResizable(false);
            progressDialog.setVisible(true);
        });
    }

    private String formatProgressText(int completed) {
        return String.format("Progress: %d / %d", completed, numResults);
    }

    /**
     * Update progress label with detailed installation progress for a specific device
     * @param deviceName name of device being updated
     * @param currentStep current step in the installation process
     * @param totalSteps total steps in the installation process
     * @param message descriptive message about current step
     */
    public void updateProgress(String deviceName, int currentStep, int totalSteps, String message) {
        if (showProgress) {
            SwingUtilities.invokeLater(() -> {
                if (progressLabel != null) {
                    int completed = counter.get();
                    String progressText = String.format("Progress: %d / %d - %s (%d/%d): %s",
                        completed, numResults, deviceName, currentStep, totalSteps, message);
                    progressLabel.setText(progressText);
                }
            });
        }
    }

    public boolean handleResult(String device, boolean isSuccess, String message) {
        Result result = new Result(device, isSuccess, message);
        synchronized (resultList) {
            resultList.add(result);
        }
        int count = counter.incrementAndGet();

        if (showProgress) {
            SwingUtilities.invokeLater(() -> {
                if (progressBar != null) progressBar.setValue(count);
                if (progressLabel != null) progressLabel.setText(formatProgressText(count));
                // show failure details
                if (descArea != null && descScroll != null) {
                    if (!descScroll.isVisible()) {
                        descScroll.setVisible(true);
                        if (progressDialog != null) progressDialog.pack();
                    } else {
                        descArea.append("\n");
                    }
                    descArea.append(result.toString());
                }
            });
        }

        if (count == numResults) {
            // DONE!
            SwingUtilities.invokeLater(() -> {
                boolean isError = resultList.stream().anyMatch(r -> !r.isSuccess);
                if (showProgress) {
                    if (progressDialog != null) {
                        progressDialog.setTitle("DONE: " + progressDialog.getTitle());
                        closeButton.setEnabled(true);
                    }
                } else {
                    // not showing progress - only show dialog if error and no listener
                    StringBuilder sb = new StringBuilder();
                    for (Result r : resultList) {
                        sb.append(r.toString()).append("\n");
                    }
                    if (listener != null) {
                        // pass either success or error details (only failures listed)
                        listener.onTaskComplete(!isError, sb.toString());
                    } else if (isError && !showProgress) {
                        // show error if no progress dialog
                        if (!sb.isEmpty()) DialogHelper.showTextDialog(component, "Results", sb.toString());
                    }
                }
            });
            return true;
        }
        return false;
    }

}
