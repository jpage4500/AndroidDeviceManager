package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.DeviceFile;

import net.coobird.thumbnailator.Thumbnails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;

public class DeviceFileRenderer extends JLabel implements TableCellRenderer {
    private static final Logger log = LoggerFactory.getLogger(DeviceFileRenderer.class);

    private final Icon folderIcon;
    private final Icon fileIcon;

    public DeviceFileRenderer() {
        setOpaque(true);

        Border margin = new EmptyBorder(0,10,0,0); //top 0, left 10 , bottom 0, right 0
        setBorder(margin);

        folderIcon = getIcon("icon_folder.png");
        fileIcon = getIcon("icon_file.png");
    }

    public Component getTableCellRendererComponent(JTable table, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
        DeviceFile deviceFile = (DeviceFile) object;
        //setText(deviceFile.name);

        if (deviceFile.isDir || deviceFile.isLink) setIcon(folderIcon);
        else setIcon(fileIcon);

        if (isSelected) {
            setBackground(table.getSelectionBackground());
        } else {
            setBackground(Color.WHITE);
        }
        return this;
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

}