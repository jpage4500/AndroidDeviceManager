package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
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
 * NOTE: single window (not 1 per device) - target devices are set via setDeviceList()
 */
public class CommandScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(CommandScreen.class);

    public static final int MAX_RECENT_COMMANDS = 10;
    public static final String COMMANDS_TXT = "commands.txt";

    private HintTextField textField;
    private JList<String> list;
    private DefaultListModel<String> listModel;
    private List<Device> selectedDeviceList;

    public CommandScreen(App app) {
        super(app, null, "command", 500, 350);
        initalizeUi();
    }

    private void initalizeUi() {
        JPanel panel = new JPanel(new MigLayout("fill", "[][]"));

        panel.add(new JLabel("Recent Commands"), "growx, span 2, wrap");

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

        // double-click a recent command to send it
        UiUtils.addLeftClickListener(list, e -> {
            if (e.getClickCount() != 2) return;
            int row = list.locationToIndex(e.getPoint());
            if (row < 0) return;
            // locationToIndex() returns the closest row - make sure the click was actually on it
            Rectangle bounds = list.getCellBounds(row, row);
            if (bounds == null || !bounds.contains(e.getPoint())) return;
            runCommand(listModel.get(row));
        });

        JScrollPane scroll = new JScrollPane(list);
        panel.add(scroll, "grow, push, span 2, wrap");

        panel.add(new JSeparator(), "growx, spanx, wrap");

        textField = new HintTextField("ADB Command", null);
        textField.setHorizontalAlignment(SwingConstants.RIGHT);

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    runCommand();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // HintTextField consumes ESC to clear itself which blocks setupEscapeToClose()
                    closeWindow();
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

        panel.add(textField, "growx, span 2, wrap");

        JButton sendButton = new JButton("Send Command");
        sendButton.addActionListener(e -> runCommand());
        panel.add(sendButton, "newline, al right, span 2, wrap");

        setupMenuBar();
        setupEscapeToClose();

        setContentPane(panel);
    }

    /**
     * set which device(s) the command will be sent to
     * NOTE: this window is re-used so this is called each time it's shown
     */
    public void setDeviceList(List<Device> deviceList) {
        this.selectedDeviceList = deviceList;
        // NOTE: buildWindowMenu()'s "Show File Browser"/"Show Device Logs" items read this field
        // when clicked (not when built) so updating it here re-targets them
        this.device = deviceList.size() == 1 ? deviceList.get(0) : null;
        setTitle(buildTitle());

        // start focus on the command field
        SwingUtilities.invokeLater(() -> textField.requestFocusInWindow());
    }

    /**
     * this screen targets a list, not the single `device` field - so build the title from that list
     * (otherwise a device refresh would retitle the window with just the device name)
     */
    @Override
    protected String buildTitle() {
        if (selectedDeviceList == null || selectedDeviceList.isEmpty()) return "Send ADB Command";
        String target = selectedDeviceList.size() == 1
            ? selectedDeviceList.get(0).getDisplayName()
            : selectedDeviceList.size() + " devices";
        return "Send ADB Command [" + target + "]";
    }

    private void setupMenuBar() {
        JMenu windowMenu = buildWindowMenu();

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        setJMenuBar(menubar);
    }

    private void runCommand() {
        runCommand(textField.getCleanText());
    }

    private void runCommand(String command) {
        if (TextUtils.isEmpty(command)) return;

        command = santizeCommand(command);

        addCustomCommand(command);

        // update displayed list (command just run is now last and selected)
        populateRecent();
        // keep the command in the text field so it can be edited/re-sent
        textField.setText(command);

        log.debug("runCommand: {}, devices:{}", command, selectedDeviceList.size());
        String finalCommand = command;
        DeviceManager.getInstance().runCustomCommand(selectedDeviceList, command, new DeviceManager.BatchCommandListener() {
            @Override
            public void onAllComplete(boolean allSucceeded, String joinedDetail) {
                log.trace("runCommand: {}, detail:\n{}", allSucceeded, joinedDetail);
                showCommandResults(CommandScreen.this, app, finalCommand, allSucceeded, joinedDetail);
            }
        });
    }

    private void populateRecent() {
        listModel.clear();
        Map<String, String> namedCommands = getNamedCommands();
        // TODO: use name
        listModel.addAll(namedCommands.values());

        List<String> customCommandList = getCustomCommands();
        listModel.addAll(customCommandList);

        if (!listModel.isEmpty()) {
            // most recently used command is last - select it and scroll down so it's visible
            int lastIndex = listModel.size() - 1;
            list.setSelectedIndex(lastIndex);
            // NOTE: invokeLater so this also works before the window has been laid out
            SwingUtilities.invokeLater(() -> list.ensureIndexIsVisible(lastIndex));
        }
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
        // add to bottom of list (most recently used command is last)
        customCommands.add(command);
        // only keep the last 10 entries (drop the oldest)
        if (customCommands.size() > MAX_RECENT_COMMANDS) {
            customCommands = customCommands.subList(customCommands.size() - MAX_RECENT_COMMANDS, customCommands.size());
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
        Map<String, String> namedCommandMap = CommandScreen.getNamedCommands();
        namedCommandMap.forEach((name, command) -> {
            JMenuItem item = new JMenuItem(name);
            item.setToolTipText(command);
            item.addActionListener(e -> runCustomCommand(popup, app, device, command));
            commandMenu.add(item);
        });
        if (!namedCommandMap.isEmpty()) commandMenu.addSeparator();

        // add previously used commands (last 10)
        List<String> customCommandList = CommandScreen.getCustomCommands();
        for (String command : customCommandList) {
            String truncatedCommand = TextUtils.truncate(command, 30);
            JMenuItem item = new JMenuItem(truncatedCommand);
            if (!TextUtils.equals(command, truncatedCommand)) {
                item.setToolTipText(command);
            }
            item.addActionListener(e -> {
                // move to top of recent list
                CommandScreen.addCustomCommand(command);
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

        command = CommandScreen.santizeCommand(command);

        runCustomCommand(component, app, device, command);
    }

}
