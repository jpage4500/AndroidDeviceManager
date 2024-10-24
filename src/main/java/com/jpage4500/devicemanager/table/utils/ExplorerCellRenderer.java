package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.data.DeviceFile;
import com.jpage4500.devicemanager.table.ExploreTableModel;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ExplorerCellRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(ExplorerCellRenderer.class);

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd h:mm aa");

    private final Icon folderUpIcon;
    private final Icon folderIcon;
    private final Icon folderReadonlyIcon;
    private final Icon fileIcon;
    private final Icon fileReadonlyIcon;
    private final Icon folderLinkIcon;
    private final Icon fileLinkIcon;

    public ExplorerCellRenderer() {
        setOpaque(true);

        Border margin = new EmptyBorder(0, 10, 0, 0);
        setBorder(margin);

        folderUpIcon = UiUtils.getImageIcon("icon_folder_up.png", UiUtils.IMG_SIZE_ICON);
        folderIcon = UiUtils.getImageIcon("icon_folder.png", UiUtils.IMG_SIZE_ICON);
        folderReadonlyIcon = UiUtils.getImageIcon("icon_folder_readonly.png", UiUtils.IMG_SIZE_ICON);
        fileIcon = UiUtils.getImageIcon("icon_file.png", UiUtils.IMG_SIZE_ICON);
        fileReadonlyIcon = UiUtils.getImageIcon("icon_file_readonly.png", UiUtils.IMG_SIZE_ICON);
        folderLinkIcon = UiUtils.getImageIcon("icon_folder_link.png", UiUtils.IMG_SIZE_ICON);
        fileLinkIcon = UiUtils.getImageIcon("icon_file_link.png", UiUtils.IMG_SIZE_ICON);
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        DeviceFile deviceFile = (DeviceFile) object;
        // convert table column to model column
        column = table.convertColumnIndexToModel(column);

        ExploreTableModel.Columns col = ExploreTableModel.Columns.values()[column];
        Icon icon = null;
        String text = null;
        int align = SwingConstants.LEFT;
        switch (col) {
            case NAME:
                text = deviceFile.name;
                if (deviceFile.isSymbolicLink) icon = deviceFile.isDirectory ? folderLinkIcon : fileLinkIcon;
                else if (deviceFile.isDirectory) {
                    if (deviceFile.isUpFolder()) icon = folderUpIcon;
                    else if (deviceFile.isReadOnly) icon = folderReadonlyIcon;
                    else icon = folderIcon;
                } else if (deviceFile.isReadOnly) icon = fileReadonlyIcon;
                else icon = fileIcon;
                break;
            case SIZE:
                // right-align size column
                align = SwingConstants.RIGHT;
                if (!deviceFile.isDirectory) {
                    text = FileUtils.bytesToDisplayString(deviceFile.size);
                } else {
                    text = "-";
                }
                break;
            case DATE:
                if (deviceFile.dateMs > 0) {
                    Date date = new Date(deviceFile.dateMs);
                    text = dateFormat.format(date);
                }
                break;
        }

        boolean isTableFocused = table.hasFocus();
        Color textColor = isSelected && isTableFocused ? Color.WHITE : Color.BLACK;
        Color backgroundColor = isSelected ? table.getSelectionBackground() : table.getBackground();

        setForeground(textColor);
        setBackground(backgroundColor);

        setIcon(icon);
        setText(text);
        setHorizontalAlignment(align);

        return this;
    }
}