package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.table.utils.AlternatingBackgroundColorRenderer;
import com.jpage4500.devicemanager.ui.DeviceScreen;
import com.jpage4500.devicemanager.ui.dialog.AddFilterDialog;
import com.jpage4500.devicemanager.ui.views.HintTextField;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

public class DialogHelper {
    private static final Logger log = LoggerFactory.getLogger(DialogHelper.class);
    public static final String KEY_VALUE_DELIM = " : ";

    /**
     * show a simple dialog
     */
    public static void showDialog(Component component, String title, String text) {
        if (title == null) title = "Alert";
        JOptionPane.showConfirmDialog(component, text, title, JOptionPane.DEFAULT_OPTION);
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
     * show a prompt dialog with custom buttons
     *
     * @return true if YES is selected
     */
    public static boolean showOptionDialog(Component component, String title, String text, String[] buttons) {
        int rc = JOptionPane.showOptionDialog(component,
                text, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, buttons, null);
        return (rc == JOptionPane.YES_OPTION);
    }

    public static void showTextDialog(Component component, String title, String text) {
        // display results in dialog
        JTextArea textArea = new JTextArea(text);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(Utils.getScreenWidth() / 2, Utils.getScreenHeight() - 200));
        JOptionPane.showMessageDialog(component, scrollPane, title, JOptionPane.PLAIN_MESSAGE);
    }

    public static boolean showCustomDialog(Component frame, Component component, String title, String[] buttonArr) {
        int rc = JOptionPane.showOptionDialog(frame, component, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, buttonArr, null);
        return (rc == JOptionPane.YES_OPTION);
    }

    public interface DoubleClickListener {
        void handleDoubleClick(String key, String value);
    }

    /**
     * show a UI List of key-value pairs
     * NOTE: contains a filter to quickly narrow the list
     */
    public static void showListDialog(Component component, String title, Map<String, String> keyValueMap, DoubleClickListener listener) {
        JPanel panel = new JPanel(new MigLayout());
        DefaultListModel<String> listModel = new DefaultListModel<>();

        filterList(listModel, keyValueMap, null);
        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new AlternatingBackgroundColorRenderer());
        list.setVisibleRowCount(15);
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (SwingUtilities.isRightMouseButton(evt)) {
                    list.requestFocus();
                    int index = list.locationToIndex(evt.getPoint());
                    list.setSelectedIndex(index);
                    String value = list.getSelectedValue();
                    JPopupMenu popupMenu = new JPopupMenu();
                    UiUtils.addPopupMenuItem(popupMenu, "Copy to Clipboard", actionEvent -> {
                        log.trace("mouseClicked: copy: {}", value);
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        StringSelection stringSelection = new StringSelection(value);
                        clipboard.setContents(stringSelection, null);
                    });
                    popupMenu.show(evt.getComponent(), evt.getX(), evt.getY());
                } else if (evt.getClickCount() == 2) {
                    String selectedValue = list.getSelectedValue();
                    if (listener != null) {
                        String key, value;
                        int i = selectedValue.indexOf(KEY_VALUE_DELIM);
                        if (i > 0) {
                            key = selectedValue.substring(0, i);
                            value = selectedValue.substring(i + 1);
                        } else {
                            key = selectedValue;
                            value = null;
                        }
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
            }
        });

        HintTextField filter = new HintTextField("Filter", text -> filterList(listModel, keyValueMap, text));
        panel.add(filter, "width 25%, wrap");
        filter.addAncestorListener(new RequestFocusListener());

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
}
