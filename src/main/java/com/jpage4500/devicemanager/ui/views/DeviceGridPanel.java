package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.Colors;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
    private EmptyView emptyView;

    public DeviceGridPanel() {
        initializeComponents();
        selectedTiles = new ArrayList<>();

        emptyView = new EmptyView();
        emptyView.setShowBackground(PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true));

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
        gridPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 10));
        gridPanel.setOpaque(false);
        gridPanel.setBackground(Colors.COLOR_BACKGROUND);

        // Force relayout on resize
        gridPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                gridPanel.revalidate();
                gridPanel.repaint();
            }
        });

        // Force gridPanel to use viewport width for preferred size
        // This ensures WrapLayout wraps items when window is resized smaller
        //scrollPane = new JScrollPane(gridPanel);
        scrollPane = new JScrollPane(gridPanel) {
            @Override
            public void paint(Graphics graphics) {
                super.paint(graphics);
                emptyView.setEmptyText(devices.isEmpty() ? "No Devices" : null);
                emptyView.paint(graphics, getWidth(), getHeight(), 0);
            }
        };

        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int viewportWidth = scrollPane.getViewport().getWidth();
                int preferredHeight = gridPanel.getPreferredSize().height;
                gridPanel.setPreferredSize(new Dimension(viewportWidth, preferredHeight));
                gridPanel.revalidate();
                gridPanel.repaint();
            }
        });
        // Also listen to gridPanel itself (covers cases where its size changes directly)
        gridPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    gridPanel.revalidate();
                    gridPanel.repaint();
                });
            }
        });

        // Add mouse listener to gridPanel to handle deselection when clicking outside tiles
        gridPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Component clicked = gridPanel.getComponentAt(e.getPoint());
                boolean isTile = false;
                for (DeviceTilePanel tile : tilePanels) {
                    if (tile == clicked || SwingUtilities.isDescendingFrom(clicked, tile)) {
                        isTile = true;
                        break;
                    }
                }
                if (!isTile) {
                    clearSelection();
                }
            }
        });

        // Create scroll pane
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        // Initialize tile panels list
        tilePanels = new ArrayList<>();

        // --- Added: relayout on viewport resize ---
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Revalidate/repaint to force WrapLayout to recompute tile positions
                SwingUtilities.invokeLater(() -> {
                    gridPanel.revalidate();
                    gridPanel.repaint();
                });
            }
        });
    }

    public void setDevices(List<Device> devices) {
        this.devices = devices;
        emptyView.setEmptyText(devices.isEmpty() ? "No Devices" : null);
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
                tilePanel.setTileSize(tileSize, (int) (tileSize * 0.75)); // 4:3 aspect ratio

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
        UiUtils.addClickListener(tilePanel, e -> {
            boolean isRightClick = SwingUtilities.isRightMouseButton(e);
            log.trace("onClick: right:{}", isRightClick);
            if (isRightClick) {
                handleRightClick(tilePanel, e);
            } else {
                handleTileClick(tilePanel, e);
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
        log.trace("handleSelection: {}, {}", isCtrlDown, tilePanel.getDevice().getDisplayName());

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
                    // Force refresh preview if this device has a preview image
                    if (device.previewImage != null) {
                        tilePanel.refreshPreview();
                    }
                    break;
                }
            }
        });
    }

    public void setTileSize(int size) {
        final int finalSize = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, size));

        SwingUtilities.invokeLater(() -> {
            for (DeviceTilePanel tilePanel : tilePanels) {
                tilePanel.setTileSize(finalSize, (int) (finalSize * 0.75));
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
