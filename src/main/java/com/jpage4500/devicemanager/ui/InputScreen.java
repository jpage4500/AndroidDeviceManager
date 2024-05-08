package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.CustomFrame;
import com.jpage4500.devicemanager.utils.GsonHelper;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * dialog to enter text on device
 */
public class InputScreen {
    private static final Logger log = LoggerFactory.getLogger(InputScreen.class);

    private JFrame deviceFrame;
    public CustomFrame frame;
    public JPanel panel;

    private JTextField textField;
    private DefaultListModel<String> listModel;

    private Device selectedDevice;

    public InputScreen(JFrame deviceFrame, Device selectedDevice) {
        this.deviceFrame = deviceFrame;
        this.selectedDevice = selectedDevice;
        initalizeUi();

        frame.setTitle(selectedDevice.getDisplayName());
    }

    public void show() {
        frame.setVisible(true);
        textField.requestFocus();
    }

    private void initalizeUi() {
        frame = new CustomFrame("input");
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {
            }
        });
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {

            }
        });

        // -- CMD+W = close window --
        Action closeAction = new AbstractAction("Close Window") {
            @Override
            public void actionPerformed(ActionEvent e) {
                log.debug("actionPerformed: CLOSE");
                frame.setVisible(false);
                frame.dispose();
            }
        };

        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        KeyStroke closeKey = KeyStroke.getKeyStroke(KeyEvent.VK_W, mask);
        closeAction.putValue(Action.ACCELERATOR_KEY, closeKey);

        // -- CMD+~ = show devices --
        Action switchAction = new AbstractAction("Show Devices") {
            @Override
            public void actionPerformed(ActionEvent e) {
                deviceFrame.toFront();
            }
        };
        KeyStroke switchKey = KeyStroke.getKeyStroke(KeyEvent.VK_1, mask);
        switchAction.putValue(Action.ACCELERATOR_KEY, switchKey);

        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu("Window");
        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.setAction(closeAction);
        menu.add(closeItem);
        JMenuItem switchItem = new JMenuItem("Show Devices");
        switchItem.setAction(switchAction);
        menu.add(switchItem);
        menubar.add(menu);
        frame.setJMenuBar(menubar);

        panel.setLayout(new MigLayout("fillx", "[][]"));

        panel.add(new JLabel("Recent Text"), "growx, span 2, wrap");

        Preferences preferences = Preferences.userRoot();
        String recentInput = preferences.get("PREF_RECENT_INPUT", null);
        List<String> recentInputList = GsonHelper.stringToList(recentInput, String.class);

        listModel = new DefaultListModel<>();
        //listModel.addAll(recentInputList);

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

        panel.add(new JLabel("Input"), "growx, span 2, wrap");

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
            // TODO
        });

        panel.add(textField, "growx, span 2, wrap");

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> {
            handleEnterPressed();
        });
        panel.add(sendButton, "al right, span 2, wrap");

        frame.setContentPane(panel);
    }

    private void handleEnterPressed() {
        String text = textField.getText();
        textField.setEnabled(false);
        if (text.isEmpty()) {
            // send newline character
            DeviceManager.getInstance().sendInputKeyCode(selectedDevice, 66, isSuccess -> {
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

        DeviceManager.getInstance().sendInputText(selectedDevice, text, isSuccess -> {
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

