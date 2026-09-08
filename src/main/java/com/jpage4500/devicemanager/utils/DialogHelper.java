package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.ui.views.DraggableCheckBoxList;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import com.jpage4500.devicemanager.ui.views.HoverLabel;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DialogHelper {
    private static final Logger log = LoggerFactory.getLogger(DialogHelper.class);
    public static final String KEY_VALUE_DELIM = " : ";
    // input/action map key for the ESC binding on resizable dialogs
    private static final String ACTION_CLOSE = "closeDialog";

    /**
     * show an INFO dialog
     */
    public static void showDialog(Component component, String text) {
        showDialog(component, null, text, false);
    }

    /**
     * show an INFO dialog
     */
    public static void showDialog(Component component, String title, String text) {
        showDialog(component, title, text, false);
    }

    /**
     * show an INFO or ERROR dialog
     */
    public static void showDialog(Component component, String title, String text, boolean isError) {
        if (title == null) title = isError ? "Error" : "Alert";
        JOptionPane.showConfirmDialog(component, text, title, JOptionPane.DEFAULT_OPTION, isError ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * show a YES/NO prompt dialog
     *
     * @return true if YES is selected
     */
    public static boolean showConfirmDialog(Component component, String title, String text) {
        int rc = JOptionPane.showConfirmDialog(component, text, title, JOptionPane.YES_NO_OPTION);
        return (rc == JOptionPane.YES_OPTION);
    }

    /**
     * show a dialog to prompt for input
     */
    public static String showInputDialog(Component component, String title, String text, String defaultValue) {
        return (String) JOptionPane.showInputDialog(component, text, title,
            JOptionPane.QUESTION_MESSAGE, null, null, defaultValue);
    }

    public static int showOptionDialog(Component component, String title, String text, List<String> choiceList) {
        return showOptionDialog(component, title, text, choiceList.toArray(new String[0]), 0);
    }

    public static int showOptionDialog(Component component, String title, String text, List<String> choiceList, int selectedIndex) {
        return showOptionDialog(component, title, text, choiceList.toArray(new String[0]), selectedIndex);
    }

    public static int showOptionDialog(Component component, String title, String text, String[] choiceArr) {
        return showOptionDialog(component, title, text, choiceArr, 0);
    }

    /**
     * show a prompt dialog with radio buttons for choices
     *
     * @param selectedIndex choice to select by default
     * @return index of selected button or -1 if cancelled
     */
    public static int showOptionDialog(Component component, String title, String text, String[] choiceArr, int selectedIndex) {
        JPanel panel = new JPanel(new MigLayout("", "[grow]", "[]10[]"));

        // add text label if provided
        if (text != null && !text.isEmpty()) {
            JLabel label = new JLabel(text);
            panel.add(label, "wrap");
        }

        // create radio buttons
        ButtonGroup buttonGroup = new ButtonGroup();
        JRadioButton[] radioButtons = new JRadioButton[choiceArr.length];

        for (int i = 0; i < choiceArr.length; i++) {
            radioButtons[i] = new JRadioButton(choiceArr[i]);
            buttonGroup.add(radioButtons[i]);
            panel.add(radioButtons[i], "wrap");
        }

        // select the default choice (first one if out of range)
        if (radioButtons.length > 0) {
            radioButtons[selectedIndex >= 0 && selectedIndex < radioButtons.length ? selectedIndex : 0].setSelected(true);
        }

        // show dialog with OK/Cancel buttons
        int result = JOptionPane.showConfirmDialog(component, panel, title,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        // return -1 if cancelled, otherwise return index of selected radio button
        if (result != JOptionPane.OK_OPTION) {
            return -1;
        }

        for (int i = 0; i < radioButtons.length; i++) {
            if (radioButtons[i].isSelected()) {
                return i;
            }
        }

        return -1;
    }

    public static void showTextDialog(Component component, String title, String text) {
        // display results in dialog
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        // calculate preferred size based on text content
        FontMetrics fm = textArea.getFontMetrics(textArea.getFont());
        String[] lines = text.split("\n");
        int maxLineWidth = 0;
        for (String line : lines) {
            int lineWidth = fm.stringWidth(line);
            if (lineWidth > maxLineWidth) {
                maxLineWidth = lineWidth;
            }
        }

        // calculate dimensions with constraints
        int screenWidth = Utils.getScreenWidth();
        int screenHeight = Utils.getScreenHeight();
        int maxWidth = Math.min(screenWidth * 3 / 4, 1200);
        int maxHeight = screenHeight - 200;

        int preferredWidth = Math.min(maxLineWidth + 50, maxWidth);
        int preferredHeight = Math.min(lines.length * fm.getHeight() + 50, maxHeight);

        // ensure minimum size
        preferredWidth = Math.max(preferredWidth, 400);
        preferredHeight = Math.max(preferredHeight, 200);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        JOptionPane.showMessageDialog(component, scrollPane, title, JOptionPane.PLAIN_MESSAGE);
    }

    public static int showCustomDialog(Component frame, Component component, String title, Object[] buttonArr) {
        return JOptionPane.showOptionDialog(frame, component, title, JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, buttonArr, null);
    }

    /**
     * show a resizable modal dialog whose content grows with the window
     * <p>
     * NOTE: this doesn't use JOptionPane like the dialogs above. JOptionPane lays its message component
     * out at that component's preferred size, so making its window resizable just adds empty margin
     * around fixed size content - no good for something like a chart the user wants bigger.
     *
     * @param size initial size, or null to size to the content
     * @return index of the button clicked, or {@link JOptionPane#CLOSED_OPTION}
     */
    public static int showResizableDialog(Component parent, Component component, String title,
                                          Dimension size, String[] buttonArr) {
        Window owner = parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        // holds the clicked index so the button listeners can report back out of the modal block
        int[] result = {JOptionPane.CLOSED_OPTION};

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(component, BorderLayout.CENTER);

        if (buttonArr != null && buttonArr.length > 0) {
            // NOTE: FlowLayout.RIGHT still lays buttons out left to right within the right aligned
            // block, so the LAST entry in buttonArr is the one that ends up nearest the corner
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 16));
            for (int i = 0; i < buttonArr.length; i++) {
                int index = i;
                JButton button = new JButton(buttonArr[i]);
                button.addActionListener(actionEvent -> {
                    result[0] = index;
                    dialog.dispose();
                });
                buttonPanel.add(button);
            }
            contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        }

        dialog.setContentPane(contentPanel);
        dialog.setResizable(true);
        // closing via the title bar has to dispose too, or the window is only hidden and leaks
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        // JOptionPane wires ESC up for free; a plain JDialog doesn't, so bind it here. WHEN_IN_FOCUSED_
        // WINDOW means it works wherever focus sits inside the dialog, not just on the buttons
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), ACTION_CLOSE);
        dialog.getRootPane().getActionMap().put(ACTION_CLOSE, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                dialog.dispose();
            }
        });
        if (size != null) dialog.setSize(size);
        else dialog.pack();
        dialog.setLocationRelativeTo(parent);
        // modal: blocks here until a button disposes the dialog or the user closes the window
        dialog.setVisible(true);
        return result[0];
    }

    public interface ListListener {
        void handleDoubleClick(String key, String value);

        void handleRightClick(String key, String value, JPopupMenu menu);
    }

    public static void showListDialog(Component component, String title, List<String> valueList, ListListener listener) {
        // TODO:
    }

    /**
     * show a UI List of key-value pairs
     * NOTE: contains a filter to quickly narrow the list
     */
    public static void showListDialog(Component component, String title, Map<String, String> keyValueMap, ListListener listener) {
        JPanel panel = new JPanel(new MigLayout());
        DefaultListModel<String> listModel = new DefaultListModel<>();

        filterList(listModel, keyValueMap, null);
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        list.setVisibleRowCount(15);
        UiUtils.addClickListener(list, evt -> {
            if (SwingUtilities.isRightMouseButton(evt)) {
                list.requestFocus();
                if (list.isSelectionEmpty()) {
                    int index = list.locationToIndex(evt.getPoint());
                    list.setSelectedIndex(index);
                }
                JPopupMenu popupMenu = new JPopupMenu();

                List<String> valueList = list.getSelectedValuesList();
                // TODO: let listener support multiple selected items
                if (valueList.size() == 1 && listener != null) {
                    String selectedValue = valueList.get(0);
                    String[] valueArr = TextUtils.split(selectedValue, KEY_VALUE_DELIM);
                    String key = valueArr[0];
                    String value = valueArr.length > 1 ? valueArr[1] : null;
                    listener.handleRightClick(key, value, popupMenu);
                }

                UiUtils.addPopupMenuItem(popupMenu, "Copy to Clipboard", actionEvent -> {
                    String allText = TextUtils.join(valueList, "\n");
                    Utils.setClipboardText(allText);
                });
                popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
            } else if (evt.getClickCount() == 2) {
                String selectedValue = list.getSelectedValue();
                if (listener != null) {
                    String[] valueArr = TextUtils.split(list.getSelectedValue(), KEY_VALUE_DELIM);
                    String key = valueArr[0];
                    String value = valueArr.length > 1 ? valueArr[1] : null;
                    listener.handleDoubleClick(key, value);
                } else {
                    JTextArea textArea = new JTextArea(selectedValue);
                    textArea.setLineWrap(true);
                    textArea.setEditable(false);
                    JScrollPane scrollPane = new JScrollPane(textArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                    int maxW = Utils.getScreenWidth() / 2;
                    scrollPane.setPreferredSize(new Dimension(maxW, 300));
                    JOptionPane.showMessageDialog(component, scrollPane, title, JOptionPane.PLAIN_MESSAGE);
                }
            }
        });

        HintTextField filter = new HintTextField("Filter", text -> filterList(listModel, keyValueMap, text));
        panel.add(filter, "width 25%, wrap");
        //filter.addAncestorListener(new RequestFocusListener());

        JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        panel.add(scroll, "width " + (Utils.getScreenWidth() / 2) + "px");

        JOptionPane.showOptionDialog(component, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
    }

    private static void filterList(DefaultListModel<String> listModel, Map<String, String> keyValueMap, String filter) {
        listModel.clear();
        for (Map.Entry<String, String> entry : keyValueMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (TextUtils.isEmpty(filter) || TextUtils.containsAny(key, true, filter) ||
                TextUtils.containsAny(value, true, filter)) {
                if (value != null) {
                    listModel.addElement(key + KEY_VALUE_DELIM + value);
                } else {
                    listModel.addElement(key);
                }
            }
        }
    }

    /**
     * create JButton for use in JOptionPane dialog;
     * example - can be dynamically enabled/disabled
     */
    public static JButton createDialogButton(String text) {
        JButton button = new JButton(text);
        button.addActionListener(e -> {
            JOptionPane pane = DialogHelper.getOptionPane((JComponent) e.getSource());
            if (pane != null) pane.setValue(button);
        });
        return button;
    }

    public static JOptionPane getOptionPane(JComponent parent) {
        if (parent == null) return null;
        if (!(parent instanceof JOptionPane)) {
            return getOptionPane((JComponent) parent.getParent());
        } else {
            return (JOptionPane) parent;
        }
    }

    /**
     * Callback interface for device selection dialog
     */
    public interface DeviceSelectionListener {
        void onDevicesSelected(List<Device> selectedDevices);
    }

    /**
     * Show a dialog to select one or more devices from a list
     *
     * @param listener Callback when devices are selected (or null if cancelled)
     */
    public static void showDeviceSelectionDialog(Component component, String title, String msg, DeviceSelectionListener listener) {
        if (title == null) title = "Select Devices";
        if (msg == null) msg = "Select one or more devices";

        List<Device> deviceList = DeviceManager.getInstance().getDevices();
        // sort by display name
        deviceList.sort((d1, d2) -> d1.getDisplayName().compareToIgnoreCase(d2.getDisplayName()));

        DraggableCheckBoxList checkBoxList = new DraggableCheckBoxList();
        checkBoxList.setDragEnabled(false);
        for (Device device : deviceList) {
            ImageIcon icon = UiUtils.getImageIcon(device.getDeviceIcon(), 32, 32, device.getDeviceColor());
            String label = device.getDisplayName();
            checkBoxList.addItem(label, false, icon);
        }

        JPanel panel = new JPanel(new MigLayout("fillx"));

        Label msgLabel = new Label(msg);
        panel.add(msgLabel, "span, wrap 20px");

        JScrollPane scroll = new JScrollPane(checkBoxList);
        scroll.setPreferredSize(new Dimension(350, Math.min(300, deviceList.size() * 40)));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, "grow, span, wrap");

        // Select All link
        HoverLabel selectAllLabel = new HoverLabel("Select All");
        selectAllLabel.addActionListener(e -> {
            // toggle selection state
            int numSelected = checkBoxList.getNumberSelectedItems();
            boolean select = numSelected != checkBoxList.getModel().getSize();
            for (int i = 0; i < checkBoxList.getModel().getSize(); i++) {
                DraggableCheckBoxList.CheckBoxItem item = checkBoxList.getModel().getElementAt(i);
                item.checkbox.setSelected(select);
            }
            checkBoxList.repaint();
        });
        panel.add(selectAllLabel, "span, align right, gaptop 5px, wrap");

        if (DialogHelper.showCustomDialog(component, panel, title, new String[]{"Ok", "Cancel"}) != JOptionPane.YES_OPTION) return;

        List<Device> selectedDevices = new ArrayList<>();
        for (int i = 0; i < checkBoxList.getModel().getSize(); i++) {
            DraggableCheckBoxList.CheckBoxItem item = checkBoxList.getModel().getElementAt(i);
            if (item.checkbox.isSelected()) selectedDevices.add(deviceList.get(i));
        }
        if (!selectedDevices.isEmpty()) {
            listener.onDevicesSelected(selectedDevices);
        } else {
            DialogHelper.showDialog(component, "Device Manager", "Please select at least 1 device.");
            showDeviceSelectionDialog(component, title, msg, listener);
        }
    }

}
