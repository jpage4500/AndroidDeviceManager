package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * dialog to enter text on device
 */
public class InputScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(InputScreen.class);

    private final DeviceScreen deviceScreen;
    private Device device;

    private JTextField textField;
    private DefaultListModel<String> listModel;

    public InputScreen(DeviceScreen deviceScreen, Device device) {
        super("input-" + device.serial, 300, 300);
        this.deviceScreen = deviceScreen;
        this.device = device;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initalizeUi();
        updateDeviceState();
    }

    public void updateDeviceState() {
        if (device.isOnline) {
            setTitle("Input [" + device.getDisplayName() + "]");
        } else {
            setTitle("OFFLINE [" + device.getDisplayName() + "]");
        }
    }

    private void initalizeUi() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setLayout(new MigLayout("fillx", "[][]"));

        setupMenuBar();

        panel.add(new JLabel("Recent Text"), "growx, span 2, wrap");

        String recentInput = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_RECENT_INPUT);
        List<String> recentInputList = GsonHelper.stringToList(recentInput, String.class);

        listModel = new DefaultListModel<>();
        listModel.addAll(recentInputList);

        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        list.setVisibleRowCount(6);
        list.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                JList list = (JList) e.getComponent();
                list.clearSelection();
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        panel.add(scroll, "growx, span 2, wrap");

        panel.add(new JSeparator(), "growx, spanx, wrap");

        panel.add(new JLabel("Enter Text"), "growx, span 2, wrap");

        textField = new JTextField();
        textField.setHorizontalAlignment(SwingConstants.RIGHT);

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleEnterPressed();
                }
            }

        });

        list.addListSelectionListener(e -> {
            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex == -1) return;
            String value = list.getSelectedValue();
            textField.setText(value);
        });

        panel.add(textField, "growx, span 2, wrap");

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> handleEnterPressed());
        panel.add(sendButton, "al right, span 2, wrap");

        setContentPane(panel);
    }

    private void setupMenuBar() {
        JMenu windowMenu = new JMenu("Window");

        // [CMD + W] = close window
        createCmdAction(windowMenu, "Close Window", KeyEvent.VK_W, e -> closeWindow());

        // [CMD + 1] = show devices
        createCmdAction(windowMenu, "Show Devices", KeyEvent.VK_1, e -> deviceScreen.toFront());

        // [CMD + 3] = show logs
        createCmdAction(windowMenu, "View Logs", KeyEvent.VK_3, e -> deviceScreen.handleLogsCommand());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        if (state == WindowState.CLOSED) {
            closeWindow();
        }
    }

    private void closeWindow() {
        log.trace("closeWindow: {}", device.getDisplayName());
        saveFrameSize();
        deviceScreen.handleInputClosed(device.serial);
        dispose();
    }

    private void handleEnterPressed() {
        String text = textField.getText();
        textField.setEnabled(false);
        if (text.isEmpty()) {
            // send newline character
            DeviceManager.getInstance().sendInputKeyCode(device, 66, (isSuccess, error) -> {
                textField.setEnabled(true);
                if (isSuccess) {
                    // clear out text
                    textField.setText(null);

                    // add line to history
                    //listModel.addElement(finalText);
                }
            });
            return;
        }

        DeviceManager.getInstance().sendInputText(device, text, (isSuccess, error) -> {
            textField.setEnabled(true);
            if (isSuccess) {
                // clear out text
                textField.setText(null);

                // add line to history
                listModel.addElement(text);

                textField.requestFocus();
            }

        });

    }

}

