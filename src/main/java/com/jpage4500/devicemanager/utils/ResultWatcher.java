package com.jpage4500.devicemanager.utils;

import javax.swing.*;
import java.awt.*;

/**
 * track results from multiple actions from multiple threads and display them in a dialog when complete
 */
public class ResultWatcher {
    public StringBuilder sb = new StringBuilder();
    private final int numResults;
    private int counter;

    public ResultWatcher(int numResults) {
        this.numResults = numResults;
    }

    public void handleResult(Component component, String line) {
        SwingUtilities.invokeLater(() -> {
            if (line != null) sb.append(line);
            counter++;

            // show results when last command is complete
            if (counter == numResults && !sb.isEmpty()) {
                JTextArea textArea = new JTextArea(sb.toString());
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);
                JOptionPane.showMessageDialog(component, scrollPane, "Results", JOptionPane.PLAIN_MESSAGE);
            } else {
                if (line != null) sb.append("\n");
            }
        });
    }

}
