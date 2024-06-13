package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * view log messages
 */
public class MessageViewScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(MessageViewScreen.class);
    public static final String TEXT_FORMAT_JSON = "Format JSON";
    public static final String TEXT_RESTORE = "Restore";
    public static final String TEXT_FORMAT_XML = "Format XML";
    public static final String TEXT_WRAP_ON = "Wrap ON";
    public static final String TEXT_AUTO_FORMAT_ON = "Auto Format ON";
    public static final String TEXT_AUTO_FORMAT_OFF = "Auto Format OFF";

    private LogEntry[] logEntryArr;

    private JTextArea textArea;
    private JButton jsonButton;
    private JButton xmlButton;
    private JButton wrapButton;
    private JButton autoFormatButton;

    public MessageViewScreen() {
        super("message");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        initalizeUi();
    }

    protected void initalizeUi() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // -- toolbar --
        JToolBar toolbar = new JToolBar();
        setupToolbar(toolbar);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        // -- message --
        textArea = new JTextArea();
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        setupMenuBar();

        setTitle("Message Viewer");

        setContentPane(mainPanel);
        setVisible(true);
    }

    private void setupToolbar(JToolBar toolbar) {
        //toolbar.add(Box.createHorizontalGlue());

        jsonButton = createToolbarButton(toolbar, "json.png", TEXT_FORMAT_JSON, "Format JSON", actionEvent -> toggleJson());
        xmlButton = createToolbarButton(toolbar, "xml.png", TEXT_FORMAT_XML, "Format XML", actionEvent -> formatXml());
        wrapButton = createToolbarButton(toolbar, "wrap.png", TEXT_WRAP_ON, "Wrap ON", actionEvent -> toggleWrap());
        autoFormatButton = createToolbarButton(toolbar, null, TEXT_AUTO_FORMAT_ON, "Auto Format ON", actionEvent -> toggleAutoFormat());
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> closeWindow());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    private void closeWindow() {
        log.trace("closeWindow:");
        dispose();
    }

    public void setLogEntry(LogEntry... logEntryArr) {
        this.logEntryArr = logEntryArr;

        boolean autoFormat = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_AUTO_FORMAT_MESSAGE, true);
        if (autoFormat) {
            formatJson();
        } else {
            restoreText();
        }
    }

    private String getLogText() {
        StringBuilder msg = new StringBuilder();
        for (LogEntry logEntry : logEntryArr) {
            if (!msg.isEmpty()) msg.append("\n");
            msg.append(logEntry.message);
        }
        return msg.toString();
    }

    private void toggleAutoFormat() {
        boolean autoFormat = !PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_AUTO_FORMAT_MESSAGE, true);
        autoFormatButton.setText(autoFormat ? TEXT_AUTO_FORMAT_ON : TEXT_AUTO_FORMAT_OFF);
    }

    private void formatXml() {

    }

    private void toggleWrap() {
        boolean lineWrap = !textArea.getLineWrap();
        textArea.setLineWrap(lineWrap);
        textArea.setWrapStyleWord(lineWrap);
    }

    private void toggleJson() {
        String currentText = jsonButton.getText();
        switch (currentText) {
            case TEXT_FORMAT_JSON:
                // format JSON
                formatJson();
                break;
            case TEXT_RESTORE:
                // restore original text
                restoreText();
                break;
        }
    }

    private void formatJson() {
        String prettyText = TextUtils.formatJson(getLogText());
        textArea.setText(prettyText);
        jsonButton.setText(TEXT_RESTORE);
    }

    private void restoreText() {
        // restore original text
        textArea.setText(getLogText());
        jsonButton.setText(TEXT_FORMAT_JSON);
    }

}
