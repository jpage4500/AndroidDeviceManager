package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.UiUtils;

import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

/**
 * dialog to enter text on device
 */
public class InputScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(InputScreen.class);

    private static final int MAX_RECENT = 20;

    private static final String PASSWORD_MASK = "••••••••";

    private JPasswordField textField;
    private JCheckBox passwordCheckBox;
    private char echoChar;
    private DefaultListModel<RecentInput> listModel;

    /**
     * text previously sent to a device
     */
    public static class RecentInput {
        public String text;
        public boolean isPassword;

        public RecentInput(String text, boolean isPassword) {
            this.text = text;
            this.isPassword = isPassword;
        }

        @Override
        public String toString() {
            return isPassword ? PASSWORD_MASK : text;
        }
    }

    public InputScreen(App app, Device device) {
        super(app, device, "input-" + device.serial, 300, 300);
        //setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initalizeUi();
        updateDevice(device);
    }

    @Override
    protected String buildTitle() {
        return deviceTitle("Input");
    }

    private void initalizeUi() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setLayout(new MigLayout("fillx", "[][]"));

        setupMenuBar();

        panel.add(new JLabel("Recent Text"), "growx, span 2, wrap");

        String recentInput = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_RECENT_INPUT);
        List<RecentInput> recentInputList = GsonHelper.stringToList(recentInput, RecentInput.class);

        listModel = new DefaultListModel<>();
        listModel.addAll(recentInputList);

        JList<RecentInput> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        list.setVisibleRowCount(6);
        list.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                JList list = (JList) e.getComponent();
                list.clearSelection();
            }
        });

        // [DELETE] = remove selected entry
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    removeRecentText(list.getSelectedIndex());
                }
            }
        });

        // right-click = delete entry
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int index = list.locationToIndex(e.getPoint());
                if (index == -1 || !list.getCellBounds(index, index).contains(e.getPoint())) return;
                JPopupMenu popupMenu = new JPopupMenu();
                UiUtils.addPopupMenuItem(popupMenu, "Delete", actionEvent -> removeRecentText(index));
                popupMenu.show(list, e.getX(), e.getY());
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        panel.add(scroll, "growx, span 2, wrap");

        panel.add(new JSeparator(), "growx, spanx, wrap");

        panel.add(new JLabel("Enter Text"), "growx, span 2, wrap");

        textField = new JPasswordField();
        textField.setHorizontalAlignment(SwingConstants.RIGHT);
        echoChar = textField.getEchoChar();

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
            RecentInput value = list.getSelectedValue();
            textField.setText(value.text);
            passwordCheckBox.setSelected(value.isPassword);
            updatePasswordMode();
        });

        panel.add(textField, "growx, span 2, wrap");

        passwordCheckBox = new JCheckBox("Password");
        passwordCheckBox.addActionListener(e -> updatePasswordMode());
        updatePasswordMode();
        panel.add(passwordCheckBox, "al left");

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> handleEnterPressed());
        panel.add(sendButton, "al right, wrap");

        setContentPane(panel);
        bindEscapeToClose();
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        // start typing as soon as the window is showing
        if (state == WindowState.OPENED || state == WindowState.ACTIVATED) textField.requestFocusInWindow();
    }

    private void setupMenuBar() {
        JMenu windowMenu = buildWindowMenu();

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    /**
     * hide or show the entered text
     */
    private void updatePasswordMode() {
        boolean isPassword = passwordCheckBox.isSelected();
        textField.setEchoChar(isPassword ? echoChar : (char) 0);
        textField.putClientProperty("JPasswordField.cutCopyAllowed", !isPassword);
    }

    private void handleEnterPressed() {
        String text = new String(textField.getPassword());
        boolean isPassword = passwordCheckBox.isSelected();
        textField.setEnabled(false);
        if (text.isEmpty()) {
            // send newline character
            DeviceManager.getInstance().sendInputKeyCode(device, 66, (isSuccess, error) -> SwingUtilities.invokeLater(() -> {
                textField.setEnabled(true);
                textField.requestFocusInWindow();
            }));
            return;
        }

        DeviceManager.getInstance().sendInputText(device, text, (isSuccess, error) -> SwingUtilities.invokeLater(() -> {
            textField.setEnabled(true);
            if (isSuccess) {
                addRecentText(text, isPassword);
                textField.setText(null);
            }
            textField.requestFocusInWindow();
        }));
    }

    /**
     * move text to the top of the recent list and save it
     */
    private void addRecentText(String text, boolean isPassword) {
        for (int i = listModel.getSize() - 1; i >= 0; i--) {
            if (TextUtils.equals(listModel.get(i).text, text)) listModel.remove(i);
        }
        listModel.add(0, new RecentInput(text, isPassword));
        if (listModel.getSize() > MAX_RECENT) listModel.setSize(MAX_RECENT);
        saveRecentText();
    }

    private void removeRecentText(int index) {
        if (index < 0 || index >= listModel.getSize()) return;
        listModel.remove(index);
        saveRecentText();
    }

    private void saveRecentText() {
        List<RecentInput> recentList = Collections.list(listModel.elements());
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_RECENT_INPUT, GsonHelper.toJson(recentList));
    }

}

