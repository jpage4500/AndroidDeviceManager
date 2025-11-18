package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.RemoteConnection;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.AndroidKeyMapper;
import com.jpage4500.devicemanager.utils.Animations;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Window that displays a remote device screen stream with interactive input
 */
public class RemoteScreenWindow extends BaseScreen implements RemoteConnection.ScreenStreamListener {
    private static final Logger log = LoggerFactory.getLogger(RemoteScreenWindow.class);

    private final Device device;
    private final RemoteConnection remoteConnection;
    private ScreenPanel screenPanel;
    private StatusBar statusBar;
    private JComboBox<RefreshSpeed> speedComboBox;
    private JLabel fpsLabel;

    private BufferedImage currentImage;
    private int deviceWidth;
    private int deviceHeight;
    private long lastFrameTime;
    private int frameCount;
    private double currentFps;

    // Mouse drag tracking for swipe
    private Point dragStart;
    private boolean isDragging;

    // Text input batching
    private final StringBuilder textBuffer = new StringBuilder();
    private Timer textBatchTimer;

    public enum RefreshSpeed {
        FAST("Fast", 100),
        NORMAL("Normal", 250),
        SLOW("Slow", 500);

        public final String label;
        public final int intervalMs;

        RefreshSpeed(String label, int intervalMs) {
            this.label = label;
            this.intervalMs = intervalMs;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public RemoteScreenWindow(Device device) {
        super("RemoteScreenWindow", 600, 900);

        this.device = device;
        this.remoteConnection = device.remoteConnection;

        setTitle("Mirror: " + device.getDisplayName());

        initUI();
        startScreenStream(RefreshSpeed.NORMAL);
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // Screen panel
        screenPanel = new ScreenPanel();
        add(screenPanel, BorderLayout.CENTER);

        // Status bar
        JPanel statusBarPanel = new JPanel(new BorderLayout());
        UiUtils.setEmptyBorder(statusBarPanel, 0, 0);

        // Left side - FPS
        fpsLabel = new JLabel("FPS: --");
        UiUtils.setEmptyBorder(fpsLabel, 5, 5);
        statusBarPanel.add(fpsLabel, BorderLayout.WEST);

        // Center - Status
        statusBar = new StatusBar();
        statusBar.setCenterLabel("Connecting...");
        statusBarPanel.add(statusBar, BorderLayout.CENTER);

        // Right side - Speed selector
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightPanel.add(new JLabel("Speed:"));
        speedComboBox = new JComboBox<>(RefreshSpeed.values());
        speedComboBox.setSelectedItem(RefreshSpeed.NORMAL);
        speedComboBox.addActionListener(e -> handleSpeedChange());
        rightPanel.add(speedComboBox);
        statusBarPanel.add(rightPanel, BorderLayout.EAST);

        add(statusBarPanel, BorderLayout.SOUTH);

        // Start FPS counter
        Timer fpsTimer = new Timer(1000, e -> updateFpsDisplay());
        fpsTimer.start();
    }

    private void startScreenStream(RefreshSpeed speed) {
        log.debug("startScreenStream: speed={}, compress=true", speed);
        remoteConnection.startScreenStream(device.serial, speed.intervalMs, true, this);
    }

    private void handleSpeedChange() {
        RefreshSpeed speed = (RefreshSpeed) speedComboBox.getSelectedItem();
        if (speed != null) {
            log.debug("handleSpeedChange: {}", speed);
            remoteConnection.setScreenStreamInterval(device.serial, speed.intervalMs);
            statusBar.setCenterLabel("Speed: " + speed.label);
        }
    }

    private void updateFpsDisplay() {
        if (frameCount > 0) {
            fpsLabel.setText(String.format("FPS: %.1f", currentFps));
            frameCount = 0;
        }
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        if (state == WindowState.CLOSING) {
            cleanup();
            saveFrameSize();
            dispose();
        }
    }

    private void cleanup() {
        log.trace("cleanup");
        if (remoteConnection != null) {
            remoteConnection.stopScreenStream(device.serial);
        }
        if (textBatchTimer != null) {
            textBatchTimer.stop();
            flushTextBuffer();
        }
    }

    // ========================================================================
    // ScreenStreamListener Implementation
    // ========================================================================

    @Override
    public void onFrame(BufferedImage image, int width, int height) {
        SwingUtilities.invokeLater(() -> {
            currentImage = image;
            deviceWidth = width;
            deviceHeight = height;
            screenPanel.repaint();

            // Update FPS
            long now = System.currentTimeMillis();
            if (lastFrameTime > 0) {
                long elapsed = now - lastFrameTime;
                if (elapsed > 0) {
                    currentFps = 1000.0 / elapsed;
                    frameCount++;
                }
            }
            lastFrameTime = now;
        });
    }

    @Override
    public void onStatus(String status, String message) {
        SwingUtilities.invokeLater(() -> {
            log.debug("onStatus: {}: {}", status, message);
            statusBar.setCenterLabel(message);
        });
    }

    @Override
    public void onError(String error) {
        SwingUtilities.invokeLater(() -> {
            log.error("onError: {}", error);
            statusBar.setCenterLabel("Error: " + error);
            JOptionPane.showMessageDialog(this, error, "Screen Stream Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    @Override
    public void onClosed() {
        SwingUtilities.invokeLater(() -> {
            log.info("onClosed");
            statusBar.setCenterLabel("Disconnected");
        });
    }

    // ========================================================================
    // Screen Panel
    // ========================================================================

    private class ScreenPanel extends JPanel {
        // Animation handling
        private final java.util.List<Animations.Animation> animations = new java.util.ArrayList<>();
        private Timer animationTimer; // lazily created

        public ScreenPanel() {
            setBackground(Color.BLACK);
            setFocusable(true);
            requestFocusInWindow();

            // Removed always-on timer; will start when first animation is added

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    dragStart = e.getPoint();
                    isDragging = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (isDragging && dragStart != null) {
                        Point dragEnd = e.getPoint();
                        int distance = (int) dragStart.distance(dragEnd);

                        if (distance > 10) {
                            // Treat as swipe
                            handleSwipe(dragStart, dragEnd);
                        } else {
                            // Treat as tap
                            handleTap(e.getPoint());
                        }
                    }
                    isDragging = false;
                    dragStart = null;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                }
            });

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    handleKeyTyped(e);
                }

                @Override
                public void keyPressed(KeyEvent e) {
                    handleKeyPressed(e);
                }
            });
        }

        private void startAnimationLoop() {
            if (animationTimer != null) return;
            animationTimer = new Timer(30, e -> {
                if (animations.isEmpty()) {
                    // Nothing to animate; stop and cleanup
                    animationTimer.stop();
                    animationTimer = null;
                    return;
                }
                // Remove finished animations first
                boolean removed = animations.removeIf(Animations.Animation::isFinished);
                if (removed && animations.isEmpty()) {
                    // All finished; stop loop
                    animationTimer.stop();
                    animationTimer = null;
                    repaint(); // final repaint to clear
                    return;
                }
                // Active animations remain; repaint
                repaint();
            });
            animationTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (currentImage == null) {
                // Show loading message
                g.setColor(Color.WHITE);
                String msg = "Connecting to device...";
                FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = getHeight() / 2;
                g.drawString(msg, x, y);
                return;
            }

            // Calculate scaled dimensions maintaining aspect ratio
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            double panelRatio = (double) panelWidth / panelHeight;
            double imageRatio = (double) currentImage.getWidth() / currentImage.getHeight();

            int drawWidth, drawHeight, drawX, drawY;
            if (panelRatio > imageRatio) {
                // Panel is wider - fit to height
                drawHeight = panelHeight;
                drawWidth = (int) (drawHeight * imageRatio);
                drawX = (panelWidth - drawWidth) / 2;
                drawY = 0;
            } else {
                // Panel is taller - fit to width
                drawWidth = panelWidth;
                drawHeight = (int) (drawWidth / imageRatio);
                drawX = 0;
                drawY = (panelHeight - drawHeight) / 2;
            }

            // Draw image
            g.drawImage(currentImage, drawX, drawY, drawWidth, drawHeight, null);

            // Draw animations overlay
            if (!animations.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                for (Animations.Animation a : animations) {
                    try {
                        a.paint(g2);
                    } catch (Exception ex) {
                        log.warn("Animation paint error", ex);
                    }
                }
                g2.dispose();
            }
        }

        private void handleTap(Point screenPoint) {
            Point devicePoint = screenToDeviceCoordinates(screenPoint);
            if (devicePoint != null) {
                log.debug("handleTap: screen={}, device={}", screenPoint, devicePoint);
                remoteConnection.sendScreenInputTap(device.serial, devicePoint.x, devicePoint.y);
                addTapAnimation(screenPoint);
            }
        }

        private void handleSwipe(Point screenStart, Point screenEnd) {
            Point deviceStart = screenToDeviceCoordinates(screenStart);
            Point deviceEnd = screenToDeviceCoordinates(screenEnd);
            if (deviceStart != null && deviceEnd != null) {
                log.debug("handleSwipe: screen={}→{}, device={}→{}", screenStart, screenEnd, deviceStart, deviceEnd);
                remoteConnection.sendScreenInputSwipe(device.serial,
                    deviceStart.x, deviceStart.y, deviceEnd.x, deviceEnd.y, 300);
                addSwipeAnimation(screenStart, screenEnd);
            }
        }

        private void handleKeyTyped(KeyEvent e) {
            char ch = e.getKeyChar();
            if (Character.isISOControl(ch)) {
                return; // Skip control characters
            }

            // Add to text buffer for batching
            textBuffer.append(ch);

            // Start/reset batch timer
            if (textBatchTimer == null) {
                textBatchTimer = new Timer(50, ae -> flushTextBuffer());
                textBatchTimer.setRepeats(false);
            }
            textBatchTimer.restart();

            // Animation for typed character
            addKeyAnimation(String.valueOf(ch));
        }

        private void handleKeyPressed(KeyEvent e) {
            int keyCode = e.getKeyCode();

            // Map to Android keycode
            Integer androidKeyCode = AndroidKeyMapper.mapKeyCode(keyCode);

            if (androidKeyCode != null) {
                log.debug("handleKeyPressed: Java keyCode={}, Android keyCode={}", keyCode, androidKeyCode);
                // Flush any pending text first
                flushTextBuffer();
                // Send keyevent
                remoteConnection.sendScreenInputKeyEvent(device.serial, androidKeyCode);
                addKeyAnimation(KeyEvent.getKeyText(keyCode));
                e.consume();
            }
        }

        /**
         * Convert screen coordinates to device coordinates
         */
        private Point screenToDeviceCoordinates(Point screenPoint) {
            if (currentImage == null) return null;

            int panelWidth = getWidth();
            int panelHeight = getHeight();
            double panelRatio = (double) panelWidth / panelHeight;
            double imageRatio = (double) currentImage.getWidth() / currentImage.getHeight();

            int drawWidth, drawHeight, drawX, drawY;
            if (panelRatio > imageRatio) {
                drawHeight = panelHeight;
                drawWidth = (int) (drawHeight * imageRatio);
                drawX = (panelWidth - drawWidth) / 2;
                drawY = 0;
            } else {
                drawWidth = panelWidth;
                drawHeight = (int) (drawWidth / imageRatio);
                drawX = 0;
                drawY = (panelHeight - drawHeight) / 2;
            }

            // Check if click is within image bounds
            if (screenPoint.x < drawX || screenPoint.x >= drawX + drawWidth ||
                screenPoint.y < drawY || screenPoint.y >= drawY + drawHeight) {
                return null;
            }

            // Convert to device coordinates
            int relativeX = screenPoint.x - drawX;
            int relativeY = screenPoint.y - drawY;
            int deviceX = (int) ((double) relativeX / drawWidth * deviceWidth);
            int deviceY = (int) ((double) relativeY / drawHeight * deviceHeight);

            return new Point(deviceX, deviceY);
        }

        // Animation helpers
        private void addTapAnimation(Point p) {
            animations.add(new Animations.TapAnimation(p.x, p.y));
            startAnimationLoop();
            repaint();
        }

        private void addSwipeAnimation(Point start, Point end) {
            animations.add(new Animations.SwipeAnimation(start.x, start.y, end.x, end.y));
            startAnimationLoop();
            repaint();
        }

        private void addKeyAnimation(String text) {
            animations.add(new Animations.KeyAnimation(text, this));
            startAnimationLoop();
            repaint();
        }
    }

    private void flushTextBuffer() {
        if (!textBuffer.isEmpty()) {
            String text = textBuffer.toString();
            log.debug("flushTextBuffer: sending {} chars", text.length());
            remoteConnection.sendScreenInputText(device.serial, text);
            textBuffer.setLength(0);
        }
    }
}
