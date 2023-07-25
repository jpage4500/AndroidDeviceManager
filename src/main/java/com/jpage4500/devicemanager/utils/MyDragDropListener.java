package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.ui.CustomTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

public class MyDragDropListener implements DropTargetListener {
    private static final Logger log = LoggerFactory.getLogger(MyDragDropListener.class);
    private CustomTable table;
    private DragDropListener listener;

    private Color defaultRowColor;
    private Color dragRowColor = new Color(243, 126, 126);

    public interface DragDropListener {
        void onFileDropped(List<File> fileList);
    }

    public MyDragDropListener(CustomTable table, DragDropListener listener) {
        this.table = table;
        this.listener = listener;
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

    private void showDragExit() {
        if (defaultRowColor != null) {
            table.setSelectionBackground(defaultRowColor);
            table.repaint();
        }
        table.setCursor(Cursor.getDefaultCursor());
    }

    private void showDragEnter() {
        if (defaultRowColor == null) {
            defaultRowColor = table.getSelectionBackground();
        }
        table.setSelectionBackground(dragRowColor);
        table.repaint();
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
    }

}