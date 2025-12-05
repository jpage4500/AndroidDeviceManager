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
    private final Component component;
    private final int numResults;
    private final AtomicInteger counter = new AtomicInteger();
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

    public boolean handleResult(String device, boolean isSuccess, String message) {
        Result result = new Result(device, isSuccess, message);
        synchronized (resultList) {
            resultList.add(result);
        }
        int count = counter.incrementAndGet();
        if (count == numResults) {
            // DONE!
            SwingUtilities.invokeLater(() -> {
                boolean isError = resultList.stream().anyMatch(r -> !r.isSuccess);
                // not showing progress - only show dialog if error and no listener
                StringBuilder sb = new StringBuilder();
                for (Result r : resultList) {
                    sb.append(r.toString()).append("\n");
                }
                if (listener != null) {
                    // pass either success or error details (only failures listed)
                    listener.onTaskComplete(!isError, sb.toString());
                } else if (isError) {
                    // show error if no progress dialog
                    if (!sb.isEmpty()) DialogHelper.showTextDialog(component, "Results", sb.toString());
                }
            });
            return true;
        }
        return false;
    }

}
