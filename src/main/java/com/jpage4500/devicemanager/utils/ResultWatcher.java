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
    private String desc;
    private final List<Result> resultList = new ArrayList<>();

    static class Result {
        String device;
        boolean isSucess;
        String message;

        public Result(String device, boolean isSucess, String message) {
            this.device = device;
            this.isSucess = isSucess;
            this.message = message;
        }
    }

    // progress fields
    private JDialog progressDialog;
    private JProgressBar progressBar;
    private JLabel progressLabel;
    private JTextArea failureArea;
    private JScrollPane failureScroll;
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

    public void setDesc(String desc) {
        this.desc = desc;
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

            failureArea = new JTextArea(6, 50);
            failureArea.setEditable(false);
            failureArea.setLineWrap(true);
            failureArea.setWrapStyleWord(true);
            failureScroll = new JScrollPane(failureArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            failureScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
            failureScroll.setVisible(false); // only visible when failures happen
            panel.add(failureScroll);

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
            progressDialog.setAlwaysOnTop(true);
            progressDialog.setVisible(true);
        });
    }

    private String formatProgressText(int completed) {
        return String.format("Progress: %d / %d", completed, numResults);
    }

    public boolean handleResult(String device, boolean isSuccess, String message) {
        synchronized (resultList) {
            resultList.add(new Result(device, isSuccess, message));
        }
        int count = counter.incrementAndGet();

        if (showProgress) {
            SwingUtilities.invokeLater(() -> {
                if (progressBar != null) progressBar.setValue(count);
                if (progressLabel != null) progressLabel.setText(formatProgressText(count));
                if (!isSuccess) {
                    // show failure details
                    if (failureArea != null && failureScroll != null) {
                        if (!failureScroll.isVisible()) {
                            failureScroll.setVisible(true);
                            // repack dialog to show new area
                            if (progressDialog != null) progressDialog.pack();
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append(device != null ? device + ": " : "");
                        if (message != null) sb.append(message);
                        else sb.append("Error");
                        sb.append("\n");
                        failureArea.append(sb.toString());
                    }
                }
            });
        }

        if (count == numResults) {
            // DONE!
            SwingUtilities.invokeLater(() -> {
                boolean isError = false;
                StringBuilder sb = new StringBuilder();
                if (desc != null) sb.append(desc).append("\n\n");
                for (int i = 0; i < resultList.size(); i++) {
                    Result result = resultList.get(i);
                    if (!result.isSucess) {
                        isError = true;
                        // collect only failures for summary per requirement
                        sb.append(result.device != null ? result.device + ": " : "");
                        if (result.message != null) sb.append(result.message);
                        else sb.append("Error");
                        sb.append("\n");
                    }
                }

                if (showProgress) {
                    if (progressDialog != null) {
                        if (!isError) {
                            // auto-close if no errors
                            progressDialog.dispose();
                        } else {
                            progressDialog.setAlwaysOnTop(false);
                            progressDialog.setTitle("Completed with Errors");
                            progressLabel.setText("Completed: " + formatProgressText(count));
                            closeButton.setEnabled(true);
                            // if failures already shown leave dialog open
                            if (failureArea != null && failureScroll != null && !failureScroll.isVisible()) {
                                failureScroll.setVisible(true);
                                failureArea.append(sb.toString());
                                progressDialog.pack();
                            }
                        }
                    }
                }

                if (listener != null) {
                    // pass either success or error details (only failures listed)
                    listener.onTaskComplete(!isError, isError ? sb.toString() : null);
                } else if (isError && !showProgress) {
                    // show error if no progress dialog
                    if (!sb.isEmpty()) DialogHelper.showTextDialog(component, "Results", sb.toString());
                }
            });
            return true;
        }
        return false;
    }

}
