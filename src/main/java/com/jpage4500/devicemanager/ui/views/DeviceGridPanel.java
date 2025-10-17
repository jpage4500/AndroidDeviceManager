package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Grid panel for displaying devices in tile format
 */
public class DeviceGridPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(DeviceGridPanel.class);
    
    private static final int DEFAULT_TILE_SIZE = 200;
    private static final int MIN_TILE_SIZE = 120;
    private static final int MAX_TILE_SIZE = 400;
    
    private JPanel gridPanel;
    private JScrollPane scrollPane;
    private List<DeviceTilePanel> tilePanels;
    private List<Device> devices;
    private List<DeviceTilePanel> selectedTiles;
    private DeviceTilePanel lastSelectedTile;
    
    public DeviceGridPanel() {
        initializeComponents();
        selectedTiles = new ArrayList<>();
        
        // Add property change listener for preview requests
        addPropertyChangeListener("requestPreview", evt -> {
            Device device = (Device) evt.getNewValue();
            if (device != null) {
                firePropertyChange("requestPreview", null, device);
            }
        });
    }
    
    private void initializeComponents() {
        setLayout(new BorderLayout());
        
        // Create grid panel with flow layout
        gridPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        gridPanel.setBackground(Color.WHITE);
        
        // Create scroll pane
        scrollPane = new JScrollPane(gridPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        
        add(scrollPane, BorderLayout.CENTER);
        
        // Initialize tile panels list
        tilePanels = new ArrayList<>();
    }
    
    public void setDevices(List<Device> devices) {
        this.devices = devices;
        refreshGrid();
    }
    
    public void refreshGrid() {
        SwingUtilities.invokeLater(() -> {
            // Clear existing tiles
            gridPanel.removeAll();
            tilePanels.clear();
            selectedTiles.clear();
            lastSelectedTile = null;
            
            if (devices == null || devices.isEmpty()) {
                gridPanel.revalidate();
                gridPanel.repaint();
                return;
            }
            
            // Get tile size from preferences
            int tileSize = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_GRID_TILE_SIZE, DEFAULT_TILE_SIZE);
            tileSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, tileSize));
            
            // Create tiles for each device
            for (Device device : devices) {
                DeviceTilePanel tilePanel = new DeviceTilePanel(device);
                tilePanel.setTileSize(tileSize, (int)(tileSize * 0.75)); // 4:3 aspect ratio
                
                // Add mouse listeners
                setupTileListeners(tilePanel);
                
                tilePanels.add(tilePanel);
                gridPanel.add(tilePanel);
            }
            
            gridPanel.revalidate();
            gridPanel.repaint();
        });
    }
    
    private void setupTileListeners(DeviceTilePanel tilePanel) {
        tilePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleTileClick(tilePanel, e);
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(tilePanel, e);
                }
            }
        });
    }
    
    private void handleTileClick(DeviceTilePanel tilePanel, MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            if (e.getClickCount() == 2) {
                // Double-click: trigger browse command
                handleDoubleClick(tilePanel);
            } else {
                // Single click: handle selection
                handleSelection(tilePanel, e);
            }
        }
    }
    
    private void handleSelection(DeviceTilePanel tilePanel, MouseEvent e) {
        boolean isCtrlDown = e.isControlDown() || e.isMetaDown(); // CMD on Mac
        
        if (!isCtrlDown) {
            // Clear all selections
            for (DeviceTilePanel tile : selectedTiles) {
                tile.setSelected(false);
            }
            selectedTiles.clear();
        }
        
        if (selectedTiles.contains(tilePanel)) {
            // Deselect if already selected
            tilePanel.setSelected(false);
            selectedTiles.remove(tilePanel);
        } else {
            // Select tile
            tilePanel.setSelected(true);
            selectedTiles.add(tilePanel);
        }
        
        lastSelectedTile = tilePanel;
    }
    
    private void handleDoubleClick(DeviceTilePanel tilePanel) {
        // Trigger browse command for the device
        Device device = tilePanel.getDevice();
        if (device != null && device.isOnline) {
            // This will be handled by the parent DeviceScreen
            firePropertyChange("browseDevice", null, device);
        }
    }
    
    private void handleRightClick(DeviceTilePanel tilePanel, MouseEvent e) {
        // Select tile if not already selected
        if (!selectedTiles.contains(tilePanel)) {
            // Clear other selections
            for (DeviceTilePanel tile : selectedTiles) {
                tile.setSelected(false);
            }
            selectedTiles.clear();
            
            // Select this tile
            tilePanel.setSelected(true);
            selectedTiles.add(tilePanel);
            lastSelectedTile = tilePanel;
        }
        
        // Fire event for context menu
        firePropertyChange("showContextMenu", null, new ContextMenuEvent(tilePanel, e.getX(), e.getY()));
    }
    
    public void updateDevice(Device device) {
        SwingUtilities.invokeLater(() -> {
            for (DeviceTilePanel tilePanel : tilePanels) {
                if (tilePanel.getDevice() == device) {
                    tilePanel.updateDeviceInfo();
                    break;
                }
            }
        });
    }
    
    public void setTileSize(int size) {
        final int finalSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, size));
        
        SwingUtilities.invokeLater(() -> {
            for (DeviceTilePanel tilePanel : tilePanels) {
                tilePanel.setTileSize(finalSize, (int)(finalSize * 0.75));
            }
            gridPanel.revalidate();
            gridPanel.repaint();
        });
    }
    
    public List<Device> getSelectedDevices() {
        List<Device> selectedDevices = new ArrayList<>();
        for (DeviceTilePanel tilePanel : selectedTiles) {
            selectedDevices.add(tilePanel.getDevice());
        }
        return selectedDevices;
    }
    
    public void clearSelection() {
        for (DeviceTilePanel tilePanel : selectedTiles) {
            tilePanel.setSelected(false);
        }
        selectedTiles.clear();
        lastSelectedTile = null;
    }
    
    public void setSelectedDevice(Device device) {
        clearSelection();
        
        for (DeviceTilePanel tilePanel : tilePanels) {
            if (tilePanel.getDevice() == device) {
                tilePanel.setSelected(true);
                selectedTiles.add(tilePanel);
                lastSelectedTile = tilePanel;
                break;
            }
        }
    }
    
    /**
     * Event class for context menu requests
     */
    public static class ContextMenuEvent {
        public final DeviceTilePanel tilePanel;
        public final int x;
        public final int y;
        
        public ContextMenuEvent(DeviceTilePanel tilePanel, int x, int y) {
            this.tilePanel = tilePanel;
            this.x = x;
            this.y = y;
        }
    }
}
