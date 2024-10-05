package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.manager.DeviceManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * track results from multiple actions from multiple threads and display them in a dialog when complete
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

    public void handleResult(String device, boolean isSuccess, String message) {
        synchronized (resultList) {
            resultList.add(new Result(device, isSuccess, message));
        }
        int count = counter.incrementAndGet();

        if (count == numResults) {
            // DONE!
            SwingUtilities.invokeLater(() -> {
                // check if any results were errors
                boolean isError = false;
                StringBuilder sb = new StringBuilder();
                if (desc != null) sb.append(desc + "\n\n");
                sb.append("-- RESULTS --\n");
                for (int i = 0; i < resultList.size(); i++) {
                    if (i > 0) sb.append("\n");
                    Result result = resultList.get(i);
                    // only show results with a message
                    if (result.message == null) continue;

                    if (device != null) {
                        sb.append(result.device);
                        sb.append(": ");
                    }
                    sb.append(result.isSucess ? "OK" : "FAIL");
                    sb.append(": ");
                    sb.append(result.message);
                    if (!result.isSucess) {
                        isError = true;
                        break;
                    }
                }
                if (listener != null) {
                    listener.onTaskComplete(!isError, sb.toString());
                } else if (!sb.isEmpty()) {
                    // display results in dialog
                    JTextArea textArea = new JTextArea(sb.toString());
                    textArea.setEditable(false);
                    JScrollPane scrollPane = new JScrollPane(textArea);
                    JOptionPane.showMessageDialog(component, scrollPane, "Results", JOptionPane.PLAIN_MESSAGE);
                }
            });
        }
    }

}
