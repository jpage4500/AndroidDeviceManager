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
    private final List<Result> resultList = new ArrayList<>();

    static class Result {
        boolean isSucess;
        String message;

        public Result(boolean isSucess, String message) {
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

    public void handleResult(boolean isSuccess, String message) {
        synchronized (resultList) {
            resultList.add(new Result(isSuccess, message));
        }
        int count = counter.incrementAndGet();

        if (count == numResults) {
            // DONE!
            SwingUtilities.invokeLater(() -> {
                // check if any results were errors
                boolean isError = false;
                StringBuilder sb = new StringBuilder();
                for (Result result : resultList) {
                    // only show results with a message
                    if (result.message == null) continue;
                    else if (!sb.isEmpty()) sb.append('\n');
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
