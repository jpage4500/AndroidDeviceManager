package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.client.RemoteConnection;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.AndroidKeyMapper;
import com.jpage4500.devicemanager.utils.Animations;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Window that displays a remote device screen stream with interactive input
 */
public class RemoteScreenWindow extends BaseScreen implements RemoteConnection.ScreenStreamListener {
    private static final Logger log = LoggerFactory.getLogger(RemoteScreenWindow.class);

    private final Device device;
    private final RemoteConnection remoteConnection;
    private DeviceManager.TaskListener listener;
    // Connection state + reconnect
    private boolean connected = false;     // true after first frame arrives
    private Timer reconnectTimer;          // schedules a reconnect attempt
    private boolean closing = false;       // window is closing; skip reconnect

    private ScreenPanel screenPanel;
    private StatusBar statusBar;
    private JComboBox<Quality> qualityComboBox;
    private JLabel fpsLabel;

    private BufferedImage currentImage;
    private int deviceWidth;
    private int deviceHeight;
    private long lastFrameTime;
    private int frameCount;
    private double currentFps;

    // mouse drag tracking for swipe
    private Point dragStart;
    private boolean isDragging;

    // text input batching
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

    public enum Quality {
        HIGH("High", "high"),
        MEDIUM("Mid", "medium"),
        LOW("Low", "low");

        public final String label;
        public final String value;

        Quality(String label, String value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public RemoteScreenWindow(Device device, DeviceManager.TaskListener listener) {
        super("RemoteScreenWindow", 600, 900);

        this.device = device;
        this.listener = listener;
        this.remoteConnection = device.remoteConnection;

        setTitle("Mirror: " + device.getDisplayName());

        initUI();
        startScreenStream(RefreshSpeed.NORMAL);
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // screen panel
        screenPanel = new ScreenPanel();
        add(screenPanel, BorderLayout.CENTER);

        // status bar
        JPanel statusBarPanel = new JPanel(new BorderLayout());
        UiUtils.setEmptyBorder(statusBarPanel, 0, 0);

        // left - FPS
        fpsLabel = new JLabel("FPS: --");
        UiUtils.setEmptyBorder(fpsLabel, 5, 5);
        statusBarPanel.add(fpsLabel, BorderLayout.WEST);

        // center - Status
        statusBar = new StatusBar();
        statusBar.setCenterLabel("Connecting...");
        statusBarPanel.add(statusBar, BorderLayout.CENTER);

        // quality selector
        qualityComboBox = new JComboBox<>(Quality.values());
        qualityComboBox.setToolTipText("Quality of image: high, medium, low");
        qualityComboBox.setSelectedItem(Quality.HIGH);
        qualityComboBox.setPrototypeDisplayValue(Quality.HIGH);
        qualityComboBox.addActionListener(e -> handleQualityChange());
        statusBarPanel.add(qualityComboBox, BorderLayout.EAST);

        add(statusBarPanel, BorderLayout.SOUTH);

        // start FPS counter
        Timer fpsTimer = new Timer(1000, e -> updateFpsDisplay());
        fpsTimer.start();
    }

    private void startScreenStream(RefreshSpeed speed) {
        log.debug("startScreenStream: speed={}, compress=true", speed);
        remoteConnection.startScreenStream(device.serial, speed.intervalMs, true, this);
    }

    private void handleQualityChange() {
        Quality quality = (Quality) qualityComboBox.getSelectedItem();
        if (quality != null) {
            log.debug("handleQualityChange: {}", quality);
            remoteConnection.setScreenStreamQuality(device.serial, quality.value);
            statusBar.setCenterLabel("Quality: " + quality.label);
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
            closing = true;
            if (reconnectTimer != null) {
                reconnectTimer.stop();
                reconnectTimer = null;
            }
            cleanup();
            saveFrameSize();
            dispose();

            listener.onTaskComplete(true, null);
        }
    }

    private void cleanup() {
        log.trace("cleanup");
        if (remoteConnection != null) {
            remoteConnection.stopScreenStream(device.serial);
        }
        if (reconnectTimer != null) {
            reconnectTimer.stop();
            reconnectTimer = null;
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
            connected = true;
            if (!"Connected".equals(statusBar.getCenterLabelText())) {
                statusBar.setCenterLabel("Connected");
            }
            currentImage = image;
            deviceWidth = width;
            deviceHeight = height;
            screenPanel.repaint();

            // update FPS
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
            // JOptionPane.showMessageDialog(this, error, "Screen Stream Error", JOptionPane.ERROR_MESSAGE);
            scheduleReconnect();
        });
    }

    @Override
    public void onClosed() {
        SwingUtilities.invokeLater(() -> {
            log.info("onClosed");
            if (closing) {
                statusBar.setCenterLabel("Closed");
                return;
            }
            connected = false;
            statusBar.setCenterLabel("Disconnected. Reconnecting...");
            scheduleReconnect();
        });
    }

    // ========================================================================
    // screen Panel
    // ========================================================================

    private class ScreenPanel extends JPanel {
        // animation handling
        private final java.util.List<Animations.Animation> animations = new java.util.ArrayList<>();
        private Timer animationTimer; // lazily created

        // long press handling
        private static final int LONG_PRESS_THRESHOLD_MS = 500; // hold duration before triggering
        private static final int LONG_PRESS_DURATION_MS = 650; // duration sent to device to simulate long press
        private static final int LONG_PRESS_MOVE_THRESHOLD_PX = 10; // cancel if moved more than this before trigger
        private Timer longPressTimer;
        private Point pressStartPoint;
        private boolean longPressTriggered;

        // swipe gesture tracking
        private Timer swipeGestureTimer;
        private Point swipeStartPoint;
        private int swipeAccumulatedRotation;
        private boolean swipeIsHorizontal;
        private static final int SWIPE_GESTURE_TIMEOUT_MS = 150; // time to wait for gesture completion
        private static final int FIXED_SWIPE_DISTANCE = 300; // fixed swipe distance in device pixels

        private JPopupMenu activePopup;

        public ScreenPanel() {
            setBackground(Color.BLACK);
            setFocusable(true);
            requestFocusInWindow();

            // handle click and long-click
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showContextMenu(e);
                        return;
                    } else if (activePopup != null) {
                        // close popup and ignore this click
                        closePopup();
                        e.consume();
                        return;
                    }

                    requestFocusInWindow();
                    dragStart = e.getPoint();
                    pressStartPoint = e.getPoint();
                    longPressTriggered = false;
                    isDragging = true;
                    startLongPressTimer();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    // stop long press timer
                    stopLongPressTimer();

                    if (longPressTriggered) {
                        // long press already handled; ignore further tap/swipe logic
                        isDragging = false;
                        dragStart = null;
                        pressStartPoint = null;
                        return;
                    }

                    if (isDragging && dragStart != null) {
                        Point dragEnd = e.getPoint();
                        int distance = (int) dragStart.distance(dragEnd);

                        if (distance > 10) {
                            // treat as swipe
                            handleSwipe(dragStart, dragEnd);
                        } else {
                            // treat as tap
                            handleTap(e.getPoint());
                        }
                    }
                    isDragging = false;
                    dragStart = null;
                    pressStartPoint = null;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    requestFocusInWindow();
                }
            });

            // handle drag
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (pressStartPoint != null && !longPressTriggered) {
                        int dist = (int) pressStartPoint.distance(e.getPoint());
                        if (dist > LONG_PRESS_MOVE_THRESHOLD_PX) {
                            // movement exceeded threshold before long press fired; cancel
                            stopLongPressTimer();
                        }
                    }
                }
            });

            // handle touchpad swipes
            addMouseWheelListener(this::handleMouseWheel);

            // handle key events
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

        private boolean isInputAllowed() {
            return connected;
        }

        private boolean isPopupVisible() {
            if (activePopup != null) {
                return activePopup.isVisible();
            }
            return false;
        }

        private void startAnimationLoop() {
            if (animationTimer != null) return;
            animationTimer = new Timer(30, e -> {
                if (animations.isEmpty()) {
                    // nothing to animate; stop and cleanup
                    animationTimer.stop();
                    animationTimer = null;
                    return;
                }
                // remove finished animations first
                boolean removed = animations.removeIf(Animations.Animation::isFinished);
                if (removed && animations.isEmpty()) {
                    // all finished; stop loop
                    animationTimer.stop();
                    animationTimer = null;
                    repaint(); // final repaint to clear
                    return;
                }
                // active animations remain; repaint
                repaint();
            });
            animationTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (currentImage == null) {
                // show loading message
                g.setColor(Color.WHITE);
                String msg = "Connecting to device...";
                FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(msg)) / 2;
                int y = getHeight() / 2;
                g.drawString(msg, x, y);
                return;
            }

            // calculate scaled dimensions maintaining aspect ratio
            int panelWidth = getWidth();
            int panelHeight = getHeight();
            double panelRatio = (double) panelWidth / panelHeight;
            double imageRatio = (double) currentImage.getWidth() / currentImage.getHeight();

            int drawWidth, drawHeight, drawX, drawY;
            if (panelRatio > imageRatio) {
                // panel is wider - fit to height
                drawHeight = panelHeight;
                drawWidth = (int) (drawHeight * imageRatio);
                drawX = (panelWidth - drawWidth) / 2;
                drawY = 0;
            } else {
                // panel is taller - fit to width
                drawWidth = panelWidth;
                drawHeight = (int) (drawWidth / imageRatio);
                drawX = 0;
                drawY = (panelHeight - drawHeight) / 2;
            }

            // draw image
            g.drawImage(currentImage, drawX, drawY, drawWidth, drawHeight, null);

            // draw animations overlay
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
            if (!isInputAllowed()) return;
            Point devicePoint = screenToDeviceCoordinates(screenPoint);
            if (devicePoint != null) {
                log.debug("handleTap: screen={}, device={}", screenPoint, devicePoint);
                remoteConnection.sendScreenInputTap(device.serial, devicePoint.x, devicePoint.y);
                addTapAnimation(screenPoint);
            }
        }

        private void handleSwipe(Point screenStart, Point screenEnd) {
            if (!isInputAllowed()) return;
            Point deviceStart = screenToDeviceCoordinates(screenStart);
            Point deviceEnd = screenToDeviceCoordinates(screenEnd);
            if (deviceStart != null && deviceEnd != null) {
                log.debug("handleSwipe: screen={}→{}, device={}→{}", screenStart, screenEnd, deviceStart, deviceEnd);
                remoteConnection.sendScreenInputSwipe(device.serial,
                    deviceStart.x, deviceStart.y, deviceEnd.x, deviceEnd.y, 300);
                addSwipeAnimation(screenStart, screenEnd);
            }
        }

        // long press helpers
        private void startLongPressTimer() {
            stopLongPressTimer();
            longPressTimer = new Timer(LONG_PRESS_THRESHOLD_MS, e -> {
                if (pressStartPoint == null || longPressTriggered) return;
                // check movement again (safety) - ensure still within threshold
                if (dragStart != null) {
                    int dist = (int) pressStartPoint.distance(dragStart);
                    if (dist > LONG_PRESS_MOVE_THRESHOLD_PX) {
                        stopLongPressTimer();
                        return;
                    }
                }
                triggerLongPress(pressStartPoint);
                longPressTriggered = true;
                stopLongPressTimer();
            });
            longPressTimer.setRepeats(false);
            longPressTimer.start();
        }

        private void stopLongPressTimer() {
            if (longPressTimer != null) {
                longPressTimer.stop();
                longPressTimer = null;
            }
        }

        private void triggerLongPress(Point screenPoint) {
            if (!isInputAllowed()) return;
            Point devicePoint = screenToDeviceCoordinates(screenPoint);
            if (devicePoint != null) {
                log.debug("handleLongPress: screen={}, device={}", screenPoint, devicePoint);
                // simulate long press using swipe with same coords and longer duration
                remoteConnection.sendScreenInputSwipe(device.serial,
                    devicePoint.x, devicePoint.y,
                    devicePoint.x, devicePoint.y,
                    LONG_PRESS_DURATION_MS);
                addLongPressAnimation(screenPoint);
            }
        }

        private void addLongPressAnimation(Point p) {
            animations.add(new Animations.LongPressAnimation(p.x, p.y));
            startAnimationLoop();
            repaint();
        }

        private void handleKeyTyped(KeyEvent e) {
            if (!isInputAllowed()) return;
            char ch = e.getKeyChar();
            if (Character.isISOControl(ch)) {
                return; // Skip control characters
            }

            // add to text buffer for batching
            textBuffer.append(ch);

            // start/reset batch timer
            if (textBatchTimer == null) {
                textBatchTimer = new Timer(50, ae -> flushTextBuffer());
                textBatchTimer.setRepeats(false);
            }
            textBatchTimer.restart();

            // animation for typed character
            addKeyAnimation(String.valueOf(ch));
        }

        private void handleKeyPressed(KeyEvent e) {
            int keyCode = e.getKeyCode();

            // if popup is open and Escape is pressed, close it
            if (isPopupVisible() && keyCode == KeyEvent.VK_ESCAPE) {
                closePopup();
                e.consume();
                return;
            }

            // check for CMD+C (Mac) or CTRL+C (other platforms) to copy image
            boolean isMetaDown = e.isMetaDown() || e.isControlDown();
            if (isMetaDown && keyCode == KeyEvent.VK_C) {
                copyImageToClipboard();
                e.consume();
                return;
            }

            // check for CMD+S (Mac) or CTRL+S (other platforms) to save image
            if (isMetaDown && keyCode == KeyEvent.VK_S) {
                saveImageToFile();
                e.consume();
                return;
            }

            if (!isInputAllowed()) return;

            // map to Android keycode
            Integer androidKeyCode = AndroidKeyMapper.mapKeyCode(keyCode);

            if (androidKeyCode != null) {
                //log.debug("handleKeyPressed: Java keyCode={}, Android keyCode={}", keyCode, androidKeyCode);
                // flush any pending text first
                flushTextBuffer();
                // send keyevent
                remoteConnection.sendScreenInputKeyEvent(device.serial, androidKeyCode);
                addKeyAnimation(KeyEvent.getKeyText(keyCode));
                e.consume();
            }
        }

        private void handleMouseWheel(MouseWheelEvent e) {
            if (!isInputAllowed()) return;

            int rotation = e.getWheelRotation();
            Point mousePos = e.getPoint();
            Point devicePoint = screenToDeviceCoordinates(mousePos);
            if (devicePoint == null) return;

            boolean isHorizontal = e.isShiftDown();

            // if this is the first event of a new gesture
            if (swipeGestureTimer == null || !swipeGestureTimer.isRunning()) {
                // start new gesture
                swipeStartPoint = devicePoint;
                swipeAccumulatedRotation = rotation;
                swipeIsHorizontal = isHorizontal;

                // create timer to detect end of gesture
                swipeGestureTimer = new Timer(SWIPE_GESTURE_TIMEOUT_MS, evt -> {
                    sendAccumulatedSwipe();
                });
                swipeGestureTimer.setRepeats(false);
                swipeGestureTimer.start();
            } else {
                // continue accumulating the gesture
                swipeAccumulatedRotation += rotation;
                // restart timer to wait for more events
                swipeGestureTimer.restart();
            }
        }

        private void sendAccumulatedSwipe() {
            if (swipeStartPoint == null || swipeAccumulatedRotation == 0) {
                return;
            }

            // calculate swipe direction based on accumulated rotation
            int direction = swipeAccumulatedRotation < 0 ? -1 : 1;
            int deltaX = swipeIsHorizontal ? direction * FIXED_SWIPE_DISTANCE : 0;
            int deltaY = swipeIsHorizontal ? 0 : direction * FIXED_SWIPE_DISTANCE;

            // calculate device swipe coordinates (start and end positions for the touch gesture)
            Point startPoint = new Point(swipeStartPoint.x - deltaX, swipeStartPoint.y - deltaY);
            Point endPoint = new Point(swipeStartPoint.x + deltaX, swipeStartPoint.y + deltaY);

            // clamp coordinates to device screen bounds
            startPoint = clampToDeviceBounds(startPoint);
            endPoint = clampToDeviceBounds(endPoint);

            log.debug("sendAccumulatedSwipe: rotation={}, horizontal={}, device={}→{}",
                swipeAccumulatedRotation, swipeIsHorizontal, startPoint, endPoint);

            // send swipe command to device
            remoteConnection.sendScreenInputSwipe(device.serial,
                startPoint.x, startPoint.y,
                endPoint.x, endPoint.y,
                200);

            // show animation starting at mouse position
            Point screenMousePos = deviceToScreenCoordinates(swipeStartPoint);
            Point animEndPoint = new Point(swipeStartPoint.x + deltaX, swipeStartPoint.y + deltaY);
            Point screenEnd = deviceToScreenCoordinates(animEndPoint);
            if (screenMousePos != null && screenEnd != null) {
                addMouseWheelSwipeAnimation(screenMousePos, screenEnd);
            }

            // reset gesture tracking
            swipeStartPoint = null;
            swipeAccumulatedRotation = 0;
        }

        private void copyImageToClipboard() {
            if (currentImage == null) {
                log.warn("copyImageToClipboard: no image available");
                statusBar.setCenterLabel("No image to copy");
                return;
            }

            try {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                ImageTransferable transferable = new ImageTransferable(currentImage);
                clipboard.setContents(transferable, null);
                log.debug("copyImageToClipboard: image copied to clipboard");
                statusBar.setCenterLabel("Image copied to clipboard");
            } catch (Exception ex) {
                log.error("copyImageToClipboard: error", ex);
                statusBar.setCenterLabel("Error copying image");
            }
        }

        private void saveImageToFile() {
            if (currentImage == null) {
                log.warn("saveImageToFile: no image available");
                statusBar.setCenterLabel("No image to save");
                return;
            }

            // create file chooser
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Screenshot");

            // set default filename with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String defaultName = device.getDisplayName().replaceAll("[^a-zA-Z0-9.-]", "_") + "_" + sdf.format(new Date()) + ".png";
            fileChooser.setSelectedFile(new File(defaultName));

            // set file filter
            FileNameExtensionFilter filter = new FileNameExtensionFilter("PNG Images (*.png)", "png");
            fileChooser.setFileFilter(filter);

            // show save dialog
            int result = fileChooser.showSaveDialog(RemoteScreenWindow.this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                // ensure .png extension
                if (!file.getName().toLowerCase().endsWith(".png")) {
                    file = new File(file.getAbsolutePath() + ".png");
                }

                try {
                    ImageIO.write(currentImage, "png", file);
                    log.debug("saveImageToFile: image saved to {}", file.getAbsolutePath());
                    statusBar.setCenterLabel("Image saved: " + file.getName());
                } catch (IOException ex) {
                    log.error("saveImageToFile: error saving image", ex);
                    statusBar.setCenterLabel("Error saving image");
                    DialogHelper.showDialog(this, "Save Error", "Failed to save image: " + ex.getMessage(), true);
                }
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

            // check if click is within image bounds
            if (screenPoint.x < drawX || screenPoint.x >= drawX + drawWidth ||
                screenPoint.y < drawY || screenPoint.y >= drawY + drawHeight) {
                return null;
            }

            // convert to device coordinates
            int relativeX = screenPoint.x - drawX;
            int relativeY = screenPoint.y - drawY;
            int deviceX = (int) ((double) relativeX / drawWidth * deviceWidth);
            int deviceY = (int) ((double) relativeY / drawHeight * deviceHeight);

            return new Point(deviceX, deviceY);
        }

        private Point deviceToScreenCoordinates(Point devicePoint) {
            if (currentImage == null) return null;

            int panelWidth = screenPanel.getWidth();
            int panelHeight = screenPanel.getHeight();
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

            // Convert device coordinates to screen
            int screenX = (int) ((double) devicePoint.x / deviceWidth * drawWidth) + drawX;
            int screenY = (int) ((double) devicePoint.y / deviceHeight * drawHeight) + drawY;

            return new Point(screenX, screenY);
        }

        /**
         * Clamp point coordinates to device screen bounds
         */
        private Point clampToDeviceBounds(Point point) {
            int x = Math.max(0, Math.min(deviceWidth - 1, point.x));
            int y = Math.max(0, Math.min(deviceHeight - 1, point.y));
            return new Point(x, y);
        }

        // animation helpers
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

        private void addMouseWheelSwipeAnimation(Point start, Point end) {
            animations.add(new Animations.MouseWheelSwipeAnimation(start.x, start.y, end.x, end.y));
            startAnimationLoop();
            repaint();
        }

        private void addKeyAnimation(String text) {
            animations.add(new Animations.KeyAnimation(text, this));
            startAnimationLoop();
            repaint();
        }

        private void addIconAnimation(Image icon) {
            animations.add(new Animations.IconAnimation(icon, this));
            startAnimationLoop();
            repaint();
        }

        private void showContextMenu(MouseEvent e) {
            closePopup();
            JPopupMenu popup = new JPopupMenu();

            // Home
            addPopupItem(popup, "Home", Icons.HOME, AndroidKeyMapper.KEYCODE_HOME);
            // Back
            addPopupItem(popup, "Back", Icons.BACK, AndroidKeyMapper.KEYCODE_BACK);
            // Recent Apps / Task Switcher
            addPopupItem(popup, "Recent Apps", Icons.RECENT, AndroidKeyMapper.KEYCODE_APP_SWITCH);
            popup.addSeparator();
            // Menu
            addPopupItem(popup, "Menu", Icons.MENU, AndroidKeyMapper.KEYCODE_MENU);
            popup.addSeparator();
            // TODO: uncomment later if useful
//            // Page Up
//            addPopupItem(popup, "Page Up", Icons.ARROW_UP, AndroidKeyMapper.KEYCODE_PAGE_UP);
//            // Page Down
//            addPopupItem(popup, "Page Down", Icons.ARROW_DOWN, AndroidKeyMapper.KEYCODE_PAGE_DOWN);
//            popup.addSeparator();
//            // Volume Up
//            addPopupItemWithKeyAnimation(popup, "Volume Up", "Vol+", AndroidKeyMapper.KEYCODE_VOLUME_UP);
//            // Volume Down
//            addPopupItemWithKeyAnimation(popup, "Volume Down", "Vol-", AndroidKeyMapper.KEYCODE_VOLUME_DOWN);
//            popup.addSeparator();

            // Power
            addPopupItem(popup, "Power", Icons.POWER, AndroidKeyMapper.KEYCODE_POWER);

            popup.show(e.getComponent(), e.getX(), e.getY());
            activePopup = popup;
        }

        private void closePopup() {
            if (activePopup != null && activePopup.isVisible()) {
                activePopup.setVisible(false);
            }
            activePopup = null;
        }

        private void addPopupItem(JPopupMenu popup, String label, Icons icn, int keycode) {
            JMenuItem item = UiUtils.addPopupMenuItem(popup, label, icn, evt -> {
                if (isInputAllowed()) {
                    remoteConnection.sendScreenInputKeyEvent(device.serial, keycode);
                    addIconAnimation(UiUtils.getImage(icn, 64));
                }
            });
            popup.add(item);
        }
    }

    private void flushTextBuffer() {
        if (!textBuffer.isEmpty()) {
            String text = textBuffer.toString();
            //log.debug("flushTextBuffer: sending {} chars", text.length());
            remoteConnection.sendScreenInputText(device.serial, text);
            textBuffer.setLength(0);
        }
    }

    private void scheduleReconnect() {
        if (closing) return;
        if (reconnectTimer != null && reconnectTimer.isRunning()) return;
        reconnectTimer = new Timer(2000, e -> {
            if (closing) {
                reconnectTimer.stop();
                return;
            }
            log.info("Attempting to reconnect screen stream for {}", device.serial);
            connected = false; // will flip true on next frame
            startScreenStream(RefreshSpeed.NORMAL);
            statusBar.setCenterLabel("Reconnecting...");
            reconnectTimer.stop();
        });
        reconnectTimer.setRepeats(false);
        reconnectTimer.start();
    }

    /**
     * Helper class to make BufferedImage transferable to clipboard
     */
    private static class ImageTransferable implements Transferable {
        private final BufferedImage image;

        public ImageTransferable(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }

}
