package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static com.jpage4500.devicemanager.utils.PreferenceUtils.Pref;

public class CommandDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(CommandDialog.class);

    public static final int MAX_RECENT_COMMANDS = 10;

    private Component frame;
    private JTextField textField;
    private JList<String> list;
    private DefaultListModel<String> listModel;
    private List<Device> selectedDeviceList;

    private HoverLabel resultsLabel;
    private String resultsMsg;

    public static void showCommandDialog(Component frame, List<Device> selectedDeviceList) {
        CommandDialog screen = new CommandDialog(selectedDeviceList);
        int rc = JOptionPane.showOptionDialog(frame, screen, "Send ADB Command", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{}, null);
        if (rc != JOptionPane.YES_OPTION) return;
    }

    public CommandDialog(List<Device> selectedDeviceList) {
        this.selectedDeviceList = selectedDeviceList;

        setLayout(new MigLayout("fillx", "[][]"));

        add(new JLabel("Recent Commands"), "growx, span 2, wrap");

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        list.setVisibleRowCount(5);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point point = e.getPoint();
                int row = list.locationToIndex(point);
                if (row < 0) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    // select row
                    list.setSelectedIndex(row);
                    JPopupMenu popupMenu = new JPopupMenu();

                    UiUtils.addPopupMenuItem(popupMenu, "Delete", actionEvent -> deleteItem(list.getSelectedValue()));

                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        //scroll.setMaximumSize(new Dimension(200, 200));
        add(scroll, "growx, span 2, wrap");

        add(new JSeparator(), "growx, spanx, wrap");

        textField = new JTextField();
        textField.setHorizontalAlignment(SwingConstants.RIGHT);

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handleEnterPressed();
                    e.consume();
                }
            }
        });

        list.addListSelectionListener(e -> {
            int selectedIndex = list.getSelectedIndex();
            if (selectedIndex == -1) return;
            String value = list.getSelectedValue();
            textField.setText(value);
        });
        populateRecent();

        add(textField, "growx, span 2, wrap");

        resultsLabel = new HoverLabel();
        resultsLabel.setVisible(false);
        resultsLabel.addActionListener(e -> handleResultsClicked());
        add(resultsLabel, "span 2, wrap");

        JButton sendButton = new JButton("Send Command");
        sendButton.addActionListener(e -> handleEnterPressed());
        add(sendButton, "newline, al right, span 2, wrap");
    }

    private void handleResultsClicked() {
        if (resultsMsg == null) return;
        // display results in dialog
        JTextArea textArea = new JTextArea(resultsMsg);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        JOptionPane.showMessageDialog(this, scrollPane, "Results", JOptionPane.PLAIN_MESSAGE);
    }

    private void deleteItem(String command) {
        log.trace("deleteItem: {}", command);
        List<String> customCommands = getCustomCommands();
        customCommands.remove(command);
        PreferenceUtils.setPreference(Pref.PREF_CUSTOM_COMMAND_LIST, GsonHelper.toJson(customCommands));
        populateRecent();
    }

    private void handleEnterPressed() {
        String command = textField.getText();
        if (TextUtils.isEmpty(command)) return;

        // remove "adb " from commands
        if (command.startsWith("adb ")) {
            command = command.substring("adb ".length());
        }
        // remove "shell " from commands
        if (command.startsWith("shell ")) {
            command = command.substring("shell ".length());
        }

        // update recent list
        List<String> customCommands = getCustomCommands();
        customCommands.remove(command);
        // add to top of list
        customCommands.add(0, command);
        // only save last 10 entries
        if (customCommands.size() > MAX_RECENT_COMMANDS) {
            customCommands = customCommands.subList(0, MAX_RECENT_COMMANDS);
        }

        PreferenceUtils.setPreference(Pref.PREF_CUSTOM_COMMAND_LIST, GsonHelper.toJson(customCommands));

        // update displayed list
        populateRecent();

        log.debug("handleRunCustomCommand: {}, devices:{}", command, selectedDeviceList.size());
        ResultWatcher resultWatcher = new ResultWatcher(getRootPane(), selectedDeviceList.size(), (isSuccess, error) -> {
            log.trace("handleEnterPressed: {}, {}", isSuccess, error);
            String img = isSuccess ? "icon_success.png" : "icon_error.png";
            BufferedImage image = UiUtils.getImage(img, UiUtils.IMG_SIZE_ICON);
            Color color = isSuccess ? Colors.COLOR_SUCCESS : Colors.COLOR_ERROR;
            image = UiUtils.replaceColor(image, color);
            resultsLabel.setVisible(true);
            resultsLabel.setIcon(new ImageIcon(image));
            String msg = (isSuccess ? "Success" : "Error") + " - click for results";
            resultsLabel.setText(msg);
            resultsMsg = error;
        });
        for (Device device : selectedDeviceList) {
            DeviceManager.getInstance().runCustomCommand(device, command, (isSuccess, error) -> resultWatcher.handleResult(device.serial, isSuccess, isSuccess ? null : error));
        }
    }

    private void populateRecent() {
        List<String> customCommandList = getCustomCommands();
        listModel.clear();
        listModel.addAll(customCommandList);

        if (!listModel.isEmpty()) list.setSelectedIndex(0);
    }

    private List<String> getCustomCommands() {
        String customCommands = PreferenceUtils.getPreference(Pref.PREF_CUSTOM_COMMAND_LIST);
        List<String> commandList = GsonHelper.stringToList(customCommands, String.class);
        if (commandList.isEmpty()) {
            // add some common commands
            commandList.add(DeviceManager.COMMAND_DUMPSYS_BATTERY);
        }
        return commandList;
    }
}

