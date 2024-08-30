package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Random;

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
    public static final String TEXT_WRAP_OFF = "Wrap OFF";

    private final DeviceScreen deviceScreen;

    private LogEntry[] logEntryArr;

    private JTextArea textArea;
    private JScrollPane scrollPane;
    private JButton jsonButton;
    private JButton xmlButton;
    private JButton wrapButton;
    private JButton autoFormatButton;
    private JButton editButton;

    public MessageViewScreen(DeviceScreen deviceScreen) {
        super("message", 500, 500);
        this.deviceScreen = deviceScreen;
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        initalizeUi();
        refreshUi();
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

        scrollPane = new JScrollPane(textArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        setupMenuBar();

        setTitle("Message Viewer");

        setContentPane(mainPanel);
        setVisible(true);
    }

    private void setupToolbar(JToolBar toolbar) {
        //toolbar.add(Box.createHorizontalGlue());

        jsonButton = createSmallToolbarButton(toolbar, "json.png", TEXT_FORMAT_JSON, "Format JSON text (pretty-print)", actionEvent -> toggleJson());
        //xmlButton = createSmallToolbarButton(toolbar, "xml.png", TEXT_FORMAT_XML, "Format XML", actionEvent -> formatXml());
        wrapButton = createSmallToolbarButton(toolbar, "wrap.png", TEXT_WRAP_ON, "Wrap text (line wrap)", actionEvent -> toggleWrap());
        autoFormatButton = createSmallToolbarButton(toolbar, "status_busy.png", TEXT_AUTO_FORMAT_ON, "Auto Format JSON text", actionEvent -> toggleAutoFormat());
        editButton = createSmallToolbarButton(toolbar, "icon_edit.png", "Edit", "Edit message in default editor", actionEvent -> editMessage());
    }

    private void editMessage() {
        // save to temp file
        String tempFolder = System.getProperty("java.io.tmpdir");
        Random rand = new Random();
        int randInt = rand.nextInt(1000);
        File tempFile = new File(tempFolder, "msg-" + randInt + ".txt");
        try (PrintStream out = new PrintStream(new FileOutputStream(tempFile))) {
            out.print(textArea.getText());
            out.flush();
        } catch (Exception e) {
            log.error("editMessage: Exception: {}", e.getMessage());
            return;
        }

        Utils.editFile(tempFile);
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> closeWindow());

        // [CMD + 1] = show devices
        createCmdAction(windowMenu, DeviceScreen.SHOW_DEVICE_LIST, KeyEvent.VK_1, e -> {
            deviceScreen.setVisible(true);
            deviceScreen.toFront();
        });

        // [CMD + 2] = show explorer
        createCmdAction(windowMenu, DeviceScreen.SHOW_BROWSE, KeyEvent.VK_2, e -> deviceScreen.handleBrowseCommand(null));

        JMenu messageMenu = new JMenu("Message");

        // [CMD + E] = edit message
        createCmdAction(messageMenu, "Edit Message", KeyEvent.VK_E, e -> editMessage());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    private void closeWindow() {
        setVisible(false);
        //dispose();
    }

    public void setLogEntry(LogEntry... logEntryArr) {
        this.logEntryArr = logEntryArr;

        refreshUi();

        boolean autoFormat = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_AUTO_FORMAT_MESSAGE, true);
        if (autoFormat && TextUtils.containsJson(getLogText())) {
            formatJson();
        } else {
            restoreText();
        }

        // scroll back to top/left
        SwingUtilities.invokeLater(() -> {
            scrollPane.getVerticalScrollBar().setValue(0);
            scrollPane.getHorizontalScrollBar().setValue(0);
        });
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
        PreferenceUtils.togglePreference(PreferenceUtils.PrefBoolean.PREF_AUTO_FORMAT_MESSAGE, true);
        refreshUi();
    }

    private void formatXml() {

    }

    private void toggleWrap() {
        PreferenceUtils.togglePreference(PreferenceUtils.PrefBoolean.PREF_WRAP_MESSAGE, false);
        refreshUi();
    }

    private void refreshUi() {
        boolean autoFormat = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_AUTO_FORMAT_MESSAGE, true);
        autoFormatButton.setText(autoFormat ? TEXT_AUTO_FORMAT_ON : TEXT_AUTO_FORMAT_OFF);

        boolean wrapMessage = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_WRAP_MESSAGE, false);
        wrapButton.setText(wrapMessage ? TEXT_WRAP_ON : TEXT_WRAP_OFF);
        textArea.setLineWrap(wrapMessage);
        textArea.setWrapStyleWord(wrapMessage);
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
