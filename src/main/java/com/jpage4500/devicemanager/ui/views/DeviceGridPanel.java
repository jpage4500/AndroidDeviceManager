package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.Colors;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
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

        // Create grid panel with even grid layout
        int minTileWidth = DEFAULT_TILE_SIZE; // You can adjust this or use preferences
        gridPanel = new JPanel(new EvenGridLayout(10, 10, minTileWidth));
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
                // Instead of forcing gridPanel to match viewport width, set preferred size using EvenGridLayout utility
                EvenGridLayout.updatePreferredSize(gridPanel);
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
                gridPanel.requestFocusInWindow(); // Ensure gridPanel gets focus for key events
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
                // Instead of forcing gridPanel to match viewport width, set preferred size using EvenGridLayout utility
                EvenGridLayout.updatePreferredSize(gridPanel);
                gridPanel.revalidate();
                gridPanel.repaint();
            }
        });

        // Make gridPanel focusable for key events
        gridPanel.setFocusable(true);
        gridPanel.requestFocusInWindow();
        gridPanel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                boolean isCmdDown = (e.getModifiersEx() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()) != 0;
                boolean isShiftDown = e.isShiftDown();
                int selectedIdx = lastSelectedTile != null ? tilePanels.indexOf(lastSelectedTile) : -1;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_A:
                        if (isCmdDown) {
                            // CMD+A: select all tiles
                            for (DeviceTilePanel tile : tilePanels) {
                                if (!selectedTiles.contains(tile)) {
                                    tile.setSelected(true);
                                    selectedTiles.add(tile);
                                }
                            }
                            if (!tilePanels.isEmpty()) {
                                lastSelectedTile = tilePanels.get(tilePanels.size() - 1);
                            }
                            gridPanel.repaint();
                        }
                        break;
                    case KeyEvent.VK_LEFT:
                        if (isShiftDown && selectedIdx >= 0 && selectedIdx > 0) {
                            // Extend selection left
                            DeviceTilePanel nextTile = tilePanels.get(selectedIdx - 1);
                            nextTile.setSelected(true);
                            if (!selectedTiles.contains(nextTile)) selectedTiles.add(nextTile);
                            lastSelectedTile = nextTile;
                            gridPanel.scrollRectToVisible(nextTile.getBounds());
                        } else {
                            moveSelection(-1, 0);
                        }
                        break;
                    case KeyEvent.VK_RIGHT:
                        if (isShiftDown && selectedIdx >= 0 && selectedIdx < tilePanels.size() - 1) {
                            // Extend selection right
                            DeviceTilePanel nextTile = tilePanels.get(selectedIdx + 1);
                            nextTile.setSelected(true);
                            if (!selectedTiles.contains(nextTile)) selectedTiles.add(nextTile);
                            lastSelectedTile = nextTile;
                            gridPanel.scrollRectToVisible(nextTile.getBounds());
                        } else {
                            moveSelection(1, 0);
                        }
                        break;
                    case KeyEvent.VK_UP:
                        moveSelection(0, -1);
                        break;
                    case KeyEvent.VK_DOWN:
                        moveSelection(0, 1);
                        break;
                    default:
                        break;
                }
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
            // Set preferred size for gridPanel after adding tiles
            EvenGridLayout.updatePreferredSize(gridPanel);
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
            LayoutManager layout = gridPanel.getLayout();
            if (layout instanceof EvenGridLayout) {
                ((EvenGridLayout) layout).setTileWidth(finalSize);
            }
//            for (DeviceTilePanel tilePanel : tilePanels) {
//                tilePanel.setTileSize(finalSize, (int) (finalSize * 0.75));
//            }
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

    // Helper to move selection in grid using EvenGridLayout
    private void moveSelection(int dx, int dy) {
        if (tilePanels.isEmpty()) return;
        int selectedIdx = lastSelectedTile != null ? tilePanels.indexOf(lastSelectedTile) : -1;
        if (selectedIdx == -1) {
            setSelectedDevice(tilePanels.get(0).getDevice());
            gridPanel.scrollRectToVisible(tilePanels.get(0).getBounds());
            return;
        }
        int totalTiles = tilePanels.size();
        int panelWidth = gridPanel.getWidth();
        LayoutManager layout = gridPanel.getLayout();
        int newIdx;
        if (layout instanceof EvenGridLayout) {
            newIdx = ((EvenGridLayout) layout).moveSelection(selectedIdx, dx, dy, totalTiles, panelWidth);
        } else {
            // fallback: linear navigation
            newIdx = Math.max(0, Math.min(selectedIdx + (dx != 0 ? dx : dy * totalTiles), totalTiles - 1));
        }
        if (newIdx != selectedIdx && newIdx >= 0 && newIdx < totalTiles) {
            setSelectedDevice(tilePanels.get(newIdx).getDevice());
            gridPanel.scrollRectToVisible(tilePanels.get(newIdx).getBounds());
        }
    }
}
