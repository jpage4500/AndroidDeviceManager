package com.jpage4500.devicemanager.table.utils;

import com.jpage4500.devicemanager.table.ExploreTableModel;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.vidstige.jadb.RemoteFile;

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
    private final Icon fileIcon;
    private final Icon linkIcon;

    public ExplorerCellRenderer() {
        setOpaque(true);

        Border margin = new EmptyBorder(0, 10, 0, 0);
        setBorder(margin);

        folderUpIcon = getIcon("icon_folder_up.png");
        folderIcon = getIcon("icon_folder.png");
        fileIcon = getIcon("icon_file.png");
        linkIcon = getIcon("icon_link.png");
    }

    private ImageIcon getIcon(String imageName) {
        Image icon = null;
        try {
            // library offers MUCH better image scaling than ImageIO
            icon = Thumbnails.of(getClass().getResource("/images/" + imageName)).size(20, 20).asBufferedImage();
            //Image image = ImageIO.read(getClass().getResource("/images/" + imageName));
        } catch (Exception e) {
            log.error("getIcon: Exception:{}", e.getMessage());
        }
        if (icon != null) return new ImageIcon(icon);
        else return null;
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        RemoteFile remoteFile = (RemoteFile) object;

        ExploreTableModel.Columns col = ExploreTableModel.Columns.values()[column];
        Icon icon = null;
        String text = null;
        int align = SwingConstants.LEFT;
        switch (col) {
            case NAME:
                text = remoteFile.getName();
                if (remoteFile.isDirectory()) {
                    if (TextUtils.equals(remoteFile.getName(), "..")) icon = folderUpIcon;
                    else icon = folderIcon;
                } else if (remoteFile.isSymbolicLink()) icon = linkIcon;
                else icon = fileIcon;
                break;
            case SIZE:
                // right-align size column
                align = SwingConstants.RIGHT;
                if (!remoteFile.isDirectory() && !remoteFile.isSymbolicLink()) {
                    text = FileUtils.bytesToDisplayString(remoteFile.getSize());
                } else {
                    text = "-";
                }
                break;
            case DATE:
                if (remoteFile.getLastModified() > 0) {
                    Date date = new Date(remoteFile.getLastModified() * 1000L);
                    text = dateFormat.format(date);
                }
                break;
        }

        setIcon(icon);
        setText(text);
        setHorizontalAlignment(align);

        if (isSelected) {
            setBackground(table.getSelectionBackground());
        } else {
            setBackground(Color.WHITE);
        }
        return this;
    }
}