package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

public class CheckBoxList extends JList {
    private static final Logger log = LoggerFactory.getLogger(CheckBoxList.class);

    public CheckBoxList() {
        setCellRenderer(new CellRenderer());

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int index = locationToIndex(e.getPoint());

                if (index != -1) {
                    JCheckBox checkbox = (JCheckBox) getModel().getElementAt(index);
                    checkbox.setSelected(!checkbox.isSelected());
                    repaint();
                }
            }
        });

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public void addItem(String item) {
        addItem(item, false);
    }

    public void addItem(String item, boolean isSelected) {
        JCheckBox checkbox = new JCheckBox(item, isSelected);
        addCheckbox(checkbox);
    }

    public void addCheckbox(JCheckBox checkBox) {
        ListModel currentList = this.getModel();
        JCheckBox[] newList = new JCheckBox[currentList.getSize() + 1];
        for (int i = 0; i < currentList.getSize(); i++) {
            newList[i] = (JCheckBox) currentList.getElementAt(i);
        }
        newList[newList.length - 1] = checkBox;
        setListData(newList);
    }

    /**
     * @return List of items that are selected (checked)
     */
    public java.util.List<String> getSelectedItems() {
        return getItems(true);
    }

    /**
     * @return List of items that are NOT selected (checked)
     */
    public java.util.List<String> getUnSelectedItems() {
        return getItems(false);
    }

    private java.util.List<String> getItems(boolean isSelected) {
        java.util.List<String> selectedItems = new ArrayList<>();
        for (int i = 0; i < this.getModel().getSize(); i++) {
            JCheckBox checkbox = (JCheckBox) getModel().getElementAt(i);
            if (checkbox.isSelected() == isSelected) selectedItems.add(checkbox.getText());
        }
        return selectedItems;
    }

    protected class CellRenderer implements ListCellRenderer {
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JCheckBox checkbox = (JCheckBox) value;

            if (index % 2 == 0) checkbox.setBackground(Color.WHITE);
            else checkbox.setBackground(Colors.COLOR_LIGHT_GRAY);
            checkbox.setEnabled(isEnabled());
            checkbox.setFont(getFont());
            checkbox.setFocusPainted(false);
            checkbox.setBorderPainted(true);
            return checkbox;
        }
    }
}