import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

import javax.swing.*;

class MyDragDropListener implements DropTargetListener {
    private static final Logger log = LoggerFactory.getLogger(MyDragDropListener.class);
    private JTable table;

    public MyDragDropListener(JTable table) {
        this.table = table;
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
                    // Loop them through
                    for (File file : files) {
                        // Print out the file path
                        log.debug("drop: {}", file.getPath());
                    }
                }
            } catch (Exception e) {
                // Print out the error stack
                log.error("drop: {}", e.getMessage());
            }
        }
        // Inform that the drop is complete
        event.dropComplete(true);

        table.setBackground(Color.WHITE);
    }

    @Override
    public void dragEnter(DropTargetDragEvent event) {
        table.setBackground(Color.LIGHT_GRAY);
        log.debug("dragEnter:");
    }

    @Override
    public void dragExit(DropTargetEvent event) {
        table.setBackground(Color.WHITE);
        log.debug("dragExit: ");
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
    }

}