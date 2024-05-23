package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

public class FileDragAndDropListener implements DropTargetListener {
    private static final Logger log = LoggerFactory.getLogger(FileDragAndDropListener.class);
    private JComponent component;
    private DragDropListener listener;

    private Color defaultRowColor;
    private Color dragRowColor = new Color(243, 126, 126);
    private Border selectedBorder;
    private Border defaultBorder;

    public interface DragDropListener {
        void onFileDropped(List<File> fileList);
    }

    public FileDragAndDropListener(JComponent component, DragDropListener listener) {
        this.component = component;
        this.listener = listener;

        defaultBorder = component.getBorder();
    }

    @Override
    public void drop(DropTargetDropEvent event) {
        // Accept copy drops
        event.acceptDrop(DnDConstants.ACTION_COPY);

        // Get the transfer which can provide the dropped item data
        Transferable transferable = event.getTransferable();

        // Get the data formats of the dropped item
        DataFlavor[] flavors = transferable.getTransferDataFlavors();

        // Loop through the flavors
        for (DataFlavor flavor : flavors) {
            try {
                // If the drop items are files
                if (flavor.isFlavorJavaFileListType()) {
                    // Get all of the dropped files
                    List<File> files = (List<File>) transferable.getTransferData(flavor);
                    listener.onFileDropped(files);
                }
            } catch (Exception e) {
                // Print out the error stack
                log.error("drop: {}", e.getMessage());
            }
        }
        // Inform that the drop is complete
        event.dropComplete(true);
        showDragExit();
    }

    @Override
    public void dragEnter(DropTargetDragEvent event) {
    }

    @Override
    public void dragExit(DropTargetEvent event) {
        showDragExit();
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
        if (component instanceof JTable table) {
            Point p = event.getLocation();
            int numSelected = table.getSelectedRowCount();
            int row = table.rowAtPoint(p);
            // if dragging over a selected row, change color of ALL selected rows
            if (table.isRowSelected(row)) {
                showDragEnter();
            } else if (numSelected < 2) {
                // select rows as user drags over them
                table.changeSelection(row, 0, false, false);
            } else {
                showDragExit();
            }
        } else {
            if (selectedBorder == null) {
                selectedBorder = BorderFactory.createMatteBorder(2, 2, 2, 2, Color.BLUE);
            }
            component.setBorder(selectedBorder);
            showDragEnter();
        }
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
    }

    private void showDragExit() {
        if (component instanceof JTable table) {
            if (defaultRowColor != null) {
                table.setSelectionBackground(defaultRowColor);
                table.repaint();
            }
        } else {
            component.setBorder(defaultBorder);
        }
        component.setCursor(Cursor.getDefaultCursor());
    }

    private void showDragEnter() {
        if (component instanceof JTable table) {
            if (defaultRowColor == null) {
                defaultRowColor = table.getSelectionBackground();
            }
            table.setSelectionBackground(dragRowColor);
            table.repaint();
        }
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

}