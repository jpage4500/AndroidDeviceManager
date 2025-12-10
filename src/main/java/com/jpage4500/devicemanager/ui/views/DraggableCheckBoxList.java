package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.List;

public class DraggableCheckBoxList extends JList<DraggableCheckBoxList.CheckBoxItem> {
    private static final Logger log = LoggerFactory.getLogger(DraggableCheckBoxList.class);
    private static final int DRAG_HANDLE_WIDTH = 40;

    private int dragSourceIndex = -1;
    private boolean isDragHandlePressed = false;
    private Point mousePressPoint = null;

    public DraggableCheckBoxList() {
        setCellRenderer(new CellRenderer());

        // track mouse press/release/drag
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                mousePressPoint = e.getPoint();
                int index = locationToIndex(e.getPoint());
                isDragHandlePressed = index != -1 && isOverDragHandle(e.getPoint(), index);
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                // only toggle checkbox if not dragging and not over drag handle
                if (mousePressPoint != null && !isDragHandlePressed) {
                    // check if mouse didn't move much (not a drag)
                    int deltaX = Math.abs(e.getPoint().x - mousePressPoint.x);
                    int deltaY = Math.abs(e.getPoint().y - mousePressPoint.y);
                    if (deltaX < 5 && deltaY < 5) {
                        int index = locationToIndex(e.getPoint());
                        if (index != -1 && !isOverDragHandle(e.getPoint(), index)) {
                            CheckBoxItem item = (CheckBoxItem) getModel().getElementAt(index);
                            item.checkbox.setSelected(!item.checkbox.isSelected());
                            repaint();
                        }
                    }
                }
                isDragHandlePressed = false;
                mousePressPoint = null;
            }
        });

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setDragEnabled(true);
        setDropMode(DropMode.INSERT);
        setTransferHandler(new ListItemTransferHandler());
    }

    private boolean isOverDragHandle(Point point, int index) {
        Rectangle cellBounds = getCellBounds(index, index);
        if (cellBounds == null) return false;
        // drag handle is on the right side
        int dragHandleX = cellBounds.x + cellBounds.width - DRAG_HANDLE_WIDTH;
        return point.x >= dragHandleX;
    }

    public void addItem(String text, boolean isSelected, ImageIcon icon) {
        JCheckBox checkbox = new JCheckBox(text, isSelected);
        CheckBoxItem item = new CheckBoxItem(checkbox, icon);
        addItem(item);
    }

    private void addItem(CheckBoxItem item) {
        ListModel<CheckBoxItem> currentList = this.getModel();
        CheckBoxItem[] newList = new CheckBoxItem[currentList.getSize() + 1];
        for (int i = 0; i < currentList.getSize(); i++) {
            newList[i] = currentList.getElementAt(i);
        }
        newList[newList.length - 1] = item;
        setListData(newList);
    }

    public void removeAll() {
        setListData(new CheckBoxItem[0]);
    }

    /**
     * @return List of items (text) in display order
     */
    public List<String> getAllItems() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < this.getModel().getSize(); i++) {
            CheckBoxItem item = (CheckBoxItem) getModel().getElementAt(i);
            items.add(item.checkbox.getText());
        }
        return items;
    }

    /**
     * @return List of items that are NOT selected (checked)
     */
    public List<String> getUnSelectedItems() {
        List<String> selectedItems = new ArrayList<>();
        for (int i = 0; i < this.getModel().getSize(); i++) {
            CheckBoxItem item = getModel().getElementAt(i);
            if (!item.checkbox.isSelected()) {
                selectedItems.add(item.checkbox.getText());
            }
        }
        return selectedItems;
    }

    public int getNumberSelectedItems() {
        int numSelected = 0;
        for (int i = 0; i < this.getModel().getSize(); i++) {
            CheckBoxItem item = getModel().getElementAt(i);
            if (item.checkbox.isSelected()) {
                numSelected++;
            }
        }
        return numSelected;
    }

    /**
     * Item wrapper that contains both checkbox and icon
     */
    public static class CheckBoxItem {
        public final JCheckBox checkbox;
        public final ImageIcon icon;

        public CheckBoxItem(JCheckBox checkbox, ImageIcon icon) {
            this.checkbox = checkbox;
            this.icon = icon;
        }
    }

    /**
     * Custom cell renderer that displays icon and checkbox
     */
    protected class CellRenderer implements ListCellRenderer<CheckBoxItem> {
        private static final int ICON_SIZE = 32;

        public Component getListCellRendererComponent(JList<? extends CheckBoxItem> list, CheckBoxItem value, int index, boolean isSelected, boolean cellHasFocus) {
            CheckBoxItem item = value;
            JCheckBox checkbox = item.checkbox;

            JPanel panel = new JPanel(new BorderLayout(5, 0));
            panel.setOpaque(true);

            if (index % 2 == 0) panel.setBackground(Color.WHITE);
            else panel.setBackground(Colors.COLOR_LIGHT_GRAY);

            checkbox.setOpaque(false);
            checkbox.setEnabled(isEnabled());
            checkbox.setFont(getFont());
            checkbox.setFocusPainted(false);
            checkbox.setBorderPainted(false);

            // left side: icon or spacer to maintain alignment
            if (item.icon != null) {
                JLabel iconLabel = new JLabel(item.icon);
                iconLabel.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 0));
                panel.add(iconLabel, BorderLayout.WEST);
            } else {
                // add spacer to align checkboxes when no icon
                JPanel spacer = new JPanel();
                spacer.setOpaque(false);
                spacer.setPreferredSize(new Dimension(ICON_SIZE + 10, ICON_SIZE));
                panel.add(spacer, BorderLayout.WEST);
            }

            // center: checkbox
            panel.add(checkbox, BorderLayout.CENTER);

            if (getDragEnabled()) {
                // right side: drag handle
                JLabel dragHandle = new JLabel("☰");
                dragHandle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
                dragHandle.setForeground(Color.GRAY);
                dragHandle.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 10));
                dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                panel.add(dragHandle, BorderLayout.EAST);
            }

            return panel;
        }
    }

    /**
     * Transfer handler for drag and drop reordering
     */
    private class ListItemTransferHandler extends TransferHandler {
        private final DataFlavor localObjectFlavor;

        public ListItemTransferHandler() {
            localObjectFlavor = new DataFlavor(CheckBoxItem.class, "CheckBoxItem");
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            // only allow drag if mouse was pressed on drag handle
            if (!isDragHandlePressed) {
                return null;
            }

            @SuppressWarnings("unchecked")
            JList<CheckBoxItem> list = (JList<CheckBoxItem>) c;
            dragSourceIndex = list.getSelectedIndex();
            CheckBoxItem value = list.getSelectedValue();
            return new CheckBoxItemTransferable(value);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(localObjectFlavor);
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            int dropTargetIndex = dl.getIndex();

            try {
                CheckBoxItem item = (CheckBoxItem) support.getTransferable().getTransferData(localObjectFlavor);

                // get current list as array
                ListModel<CheckBoxItem> currentModel = getModel();
                CheckBoxItem[] items = new CheckBoxItem[currentModel.getSize()];
                for (int i = 0; i < currentModel.getSize(); i++) {
                    items[i] = currentModel.getElementAt(i);
                }

                // remove from source position
                if (dragSourceIndex != -1 && dragSourceIndex < items.length) {
                    // shift array
                    CheckBoxItem[] newItems = new CheckBoxItem[items.length];
                    int writeIndex = 0;
                    for (int i = 0; i < items.length; i++) {
                        if (i != dragSourceIndex) {
                            newItems[writeIndex++] = items[i];
                        }
                    }
                    items = newItems;
                }

                // adjust drop index if needed
                if (dragSourceIndex != -1 && dragSourceIndex < dropTargetIndex) {
                    dropTargetIndex--;
                }

                // insert at target position
                CheckBoxItem[] finalItems = new CheckBoxItem[items.length];
                int readIndex = 0;
                for (int i = 0; i < finalItems.length; i++) {
                    if (i == dropTargetIndex) {
                        finalItems[i] = item;
                    } else if (readIndex < items.length) {
                        finalItems[i] = items[readIndex++];
                    }
                }

                setListData(finalItems);
                setSelectedIndex(dropTargetIndex);
                return true;
            } catch (Exception e) {
                log.error("Error importing data", e);
            }

            return false;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            dragSourceIndex = -1;
        }

        private class CheckBoxItemTransferable implements Transferable {
            private final CheckBoxItem item;

            public CheckBoxItemTransferable(CheckBoxItem item) {
                this.item = item;
            }

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{localObjectFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return localObjectFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return item;
            }
        }
    }
}
