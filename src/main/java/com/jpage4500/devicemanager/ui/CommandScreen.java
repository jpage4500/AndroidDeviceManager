package com.jpage4500.devicemanager.ui.dialog;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.ui.App;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.utils.*;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.jpage4500.devicemanager.utils.PreferenceUtils.Pref;

/**
 * send custom ADB command
 */
public class CommandDialog extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(CommandDialog.class);

    public static final int MAX_RECENT_COMMANDS = 10;
    public static final String COMMANDS_TXT = "commands.txt";

    private HintTextField textField;
    private JList<String> list;
    private DefaultListModel<String> listModel;
    private List<Device> selectedDeviceList;
    private App app;

    public static void showCommandDialog(Component frame, App app, List<Device> selectedDeviceList) {
        CommandDialog screen = new CommandDialog(app, selectedDeviceList);
        DialogHelper.showCustomDialog(frame, screen, "Send ADB Command", new String[0]);
    }

    public CommandDialog(App app, List<Device> selectedDeviceList) {
        this.app = app;
        this.selectedDeviceList = selectedDeviceList;

        setLayout(new MigLayout("fillx", "[][]"));

        add(new JLabel("Recent Commands"), "growx, span 2, wrap");

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        list.setVisibleRowCount(5);
        UiUtils.addRightClickListener(list, e -> {
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
        });

        JScrollPane scroll = new JScrollPane(list);
        //scroll.setMaximumSize(new Dimension(200, 200));
        add(scroll, "growx, span 2, wrap");

        add(new JSeparator(), "growx, spanx, wrap");

        textField = new HintTextField("ADB Command", null);
        textField.setHorizontalAlignment(SwingConstants.RIGHT);

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    runCommand();
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

        JButton sendButton = new JButton("Send Command");
        sendButton.addActionListener(e -> runCommand());
        add(sendButton, "newline, al right, span 2, wrap");
    }

    private void runCommand() {
        String command = textField.getCleanText();
        if (TextUtils.isEmpty(command)) return;

        command = santizeCommand(command);

        addCustomCommand(command);

        // update displayed list
        populateRecent();

        log.debug("runCommand: {}, devices:{}", command, selectedDeviceList.size());
        String finalCommand = command;
        DeviceManager.getInstance().runCustomCommand(selectedDeviceList, command, new DeviceManager.BatchCommandListener() {
            @Override
            public void onAllComplete(boolean allSucceeded, String joinedDetail) {
                log.trace("runCommand: {}, detail:\n{}", allSucceeded, joinedDetail);
                showCommandResults(CommandDialog.this, app, finalCommand, allSucceeded, joinedDetail);
            }
        });

        // close this (modal) dialog - otherwise it would block input to the results window
        UiUtils.closeWindow(this);
    }

    private void populateRecent() {
        listModel.clear();
        Map<String, String> namedCommands = getNamedCommands();
        // TODO: use name
        listModel.addAll(namedCommands.values());

        List<String> customCommandList = getCustomCommands();
        listModel.addAll(customCommandList);

        if (!listModel.isEmpty()) list.setSelectedIndex(0);
    }

    public static List<String> getCustomCommands() {
        String customCommands = PreferenceUtils.getPreference(Pref.PREF_CUSTOM_COMMAND_LIST);
        List<String> commandList = GsonHelper.stringToList(customCommands, String.class);
        if (commandList.isEmpty()) {
            // add some common commands
            commandList.add(DeviceManager.COMMAND_DUMPSYS_BATTERY);
        }
        return commandList;
    }

    /**
     * get any predefined commands which are saved in ~/.device-manager/commands.txt
     * FORMAT: NAME = COMMAND
     * -----------
     * clear APP data = pm clear com.example.app
     * open APP = ...
     * -----------
     */
    public static Map<String, String> getNamedCommands() {
        File home = Utils.getDeviceManagerFolder();
        File namedCommandFile = new File(home, COMMANDS_TXT);
        String commandsText = FileUtils.readFile(namedCommandFile);
        TreeMap<String, String> resultMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (TextUtils.isEmpty(commandsText)) return resultMap;
        String[] commandArr = commandsText.split("\n");
        for (String command : commandArr) {
            String[] lineArr = command.split("=");
            if (lineArr.length < 2) continue;
            resultMap.put(lineArr[0].trim(), lineArr[1].trim());
        }
        return resultMap;
    }

    public static void addCustomCommand(String command) {
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
    }

    public static String santizeCommand(String command) {
        // remove "adb " from commands
        if (command.startsWith("adb ")) {
            command = command.substring("adb ".length());
        }
        // remove "shell " from commands
        if (command.startsWith("shell ")) {
            command = command.substring("shell ".length());
        }
        return command;
    }

    public void deleteItem(String command) {
        log.trace("deleteItem: {}", command);
        List<String> customCommands = getCustomCommands();
        customCommands.remove(command);
        PreferenceUtils.setPreference(Pref.PREF_CUSTOM_COMMAND_LIST, GsonHelper.toJson(customCommands));
        populateRecent();
    }

    public static void setupCommandPopupMenu(JPopupMenu popup, App app, Device device) {
        JMenu commandMenu = new JMenu("Send Command");
        Map<String, String> namedCommandMap = CommandDialog.getNamedCommands();
        namedCommandMap.forEach((name, command) -> {
            JMenuItem item = new JMenuItem(name);
            item.setToolTipText(command);
            item.addActionListener(e -> runCustomCommand(popup, app, device, command));
            commandMenu.add(item);
        });
        if (!namedCommandMap.isEmpty()) commandMenu.addSeparator();

        // add previously used commands (last 10)
        List<String> customCommandList = CommandDialog.getCustomCommands();
        for (String command : customCommandList) {
            String truncatedCommand = TextUtils.truncate(command, 30);
            JMenuItem item = new JMenuItem(truncatedCommand);
            if (!TextUtils.equals(command, truncatedCommand)) {
                item.setToolTipText(command);
            }
            item.addActionListener(e -> {
                // move to top of recent list
                CommandDialog.addCustomCommand(command);
                runCustomCommand(popup, app, device, command);
            });
            commandMenu.add(item);
        }
        if (!customCommandList.isEmpty()) commandMenu.addSeparator();

        JMenuItem item = new JMenuItem("Enter Command...", UiUtils.getImageIcon(Icons.FILE_ADB, UiUtils.IMG_SIZE_SMALL));
        item.addActionListener(e -> handleSendCommand(popup, app, device));
        commandMenu.add(item);

        popup.add(commandMenu);
        popup.addSeparator();
    }

    private static void runCustomCommand(Component component, App app, Device device, String command) {
        DeviceManager.getInstance().runCustomCommand(device, command, result ->
            showCommandResults(component, app, command, result.isSuccess, TextUtils.join(result.resultList, "\n")));
    }

    /**
     * display adb command results in the message viewer (more formatting options than a plain dialog)
     * NOTE: results arrive on a DeviceManager background thread
     */
    private static void showCommandResults(Component component, App app, String command, boolean isSuccess, String text) {
        String title = (isSuccess ? "Success" : "Failed") + ": " + command;
        if (app != null) {
            app.showMessage(title, text);
        } else {
            // no App reference (eg: scrcpy mirror window) - fall back to a simple text dialog
            SwingUtilities.invokeLater(() -> DialogHelper.showTextDialog(component, title, text));
        }
    }

    private static void handleSendCommand(Component component, App app, Device device) {
        // prompt for adb command
        String command = DialogHelper.showInputDialog(component, "ADB Command", "Enter command to run", null);
        if (TextUtils.isEmpty(command)) return;

        command = CommandDialog.santizeCommand(command);

        runCustomCommand(component, app, device, command);
    }

}

