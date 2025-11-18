package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.*;
import fi.iki.elonen.NanoWSD;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket handler for streaming device screen as PNG images
 */
public class ScreenStreamWebSocket extends NanoWSD.WebSocket {
    private static final Logger log = LoggerFactory.getLogger(ScreenStreamWebSocket.class);

    // Control actions
    public static final String ACTION_CLOSE = "close";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESUME = "resume";
    public static final String ACTION_SET_INTERVAL = "setInterval";
    public static final String ACTION_SET_COMPRESSION = "setCompression";
    public static final String ACTION_INPUT = "input";

    // Input types
    public static final String INPUT_TAP = "tap";
    public static final String INPUT_SWIPE = "swipe";
    public static final String INPUT_TEXT = "text";
    public static final String INPUT_KEYEVENT = "keyevent";

    // Default refresh interval in milliseconds
    private static final int DEFAULT_INTERVAL_MS = 250;

    private final Device device;
    private final DeviceManager deviceManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> captureTask;
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger intervalMs = new AtomicInteger(DEFAULT_INTERVAL_MS);
    private final AtomicLong frameId = new AtomicLong(0);
    private final AtomicBoolean useCompression = new AtomicBoolean(false);
    private static final float JPEG_QUALITY = 0.70f; // JPEG compression quality (0.0-1.0)

    public ScreenStreamWebSocket(NanoWSD.IHTTPSession handshakeRequest, Device device, boolean useCompression) {
        super(handshakeRequest);
        this.device = device;
        this.deviceManager = DeviceManager.getInstance();
        this.useCompression.set(useCompression);
        log.debug("ScreenStreamWebSocket: device: {}, compression: {}", device.serial, useCompression);
    }

    @Override
    protected void onOpen() {
        log.info("onOpen: device: {}", device.serial);

        // Wake device screen before starting
        wakeDevice();

        // Send initial connection message
        sendStatusMessage("connected", "Screen stream started");

        // Start capture loop
        startCaptureLoop();
    }

    @Override
    protected void onClose(NanoWSD.WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
        log.info("onClose: device: {}, code: {}, reason: {}", device.serial, code, reason);
        cleanup();
    }

    @Override
    protected void onMessage(NanoWSD.WebSocketFrame message) {
        try {
            String messageText = message.getTextPayload();
            log.debug("onMessage: device: {}, text: {}", device.serial, messageText);

            @SuppressWarnings("unchecked")
            Map<String, Object> controlMessage = GsonHelper.fromJson(messageText, Map.class);
            String action = (String) controlMessage.get("action");

            if (action == null) {
                sendErrorMessage("Missing 'action' field in control message");
                return;
            }

            handleControlMessage(action, controlMessage);
        } catch (Exception e) {
            log.error("onMessage: error processing message", e);
            sendErrorMessage("Error processing message: " + e.getMessage());
        }
    }

    @Override
    protected void onPong(NanoWSD.WebSocketFrame pong) {
        // Connection health check
    }

    @Override
    protected void onException(IOException exception) {
        log.error("onException: device: {}", device.serial, exception);
        cleanup();
    }

    /**
     * Handle control messages from client
     */
    private void handleControlMessage(String action, Map<String, Object> message) {
        switch (action) {
            case ACTION_CLOSE:
                log.debug("handleControlMessage: close requested");
                try {
                    close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, "Client requested close", false);
                } catch (IOException e) {
                    log.error("handleControlMessage: error closing", e);
                }
                break;

            case ACTION_PAUSE:
                log.debug("handleControlMessage: pause");
                isPaused.set(true);
                sendStatusMessage("paused", "Stream paused");
                break;

            case ACTION_RESUME:
                log.debug("handleControlMessage: resume");
                isPaused.set(false);
                sendStatusMessage("resumed", "Stream resumed");
                break;

            case ACTION_SET_INTERVAL:
                Object intervalObj = message.get("intervalMs");
                if (intervalObj instanceof Number) {
                    int newInterval = ((Number) intervalObj).intValue();
                    setInterval(newInterval);
                } else {
                    sendErrorMessage("Invalid intervalMs value");
                }
                break;

            case ACTION_SET_COMPRESSION:
                Object compressionObj = message.get("useCompression");
                if (compressionObj instanceof Boolean) {
                    boolean newCompression = (Boolean) compressionObj;
                    setCompression(newCompression);
                } else {
                    sendErrorMessage("Invalid useCompression value");
                }
                break;

            case ACTION_INPUT:
                handleInputMessage(message);
                break;

            default:
                log.warn("handleControlMessage: unknown action: {}", action);
                sendErrorMessage("Unknown action: " + action);
                break;
        }
    }

    /**
     * Handle input messages (tap, swipe, text, keyevent)
     */
    private void handleInputMessage(Map<String, Object> message) {
        String type = (String) message.get("type");
        if (type == null) {
            sendErrorMessage("Missing 'type' in input message");
            return;
        }

        try {
            switch (type) {
                case INPUT_TAP:
                    handleTap(message);
                    break;
                case INPUT_SWIPE:
                    handleSwipe(message);
                    break;
                case INPUT_TEXT:
                    handleText(message);
                    break;
                case INPUT_KEYEVENT:
                    handleKeyEvent(message);
                    break;
                default:
                    sendErrorMessage("Unknown input type: " + type);
                    break;
            }
        } catch (Exception e) {
            log.error("handleInputMessage: error", e);
            sendErrorMessage("Input error: " + e.getMessage());
        }
    }

    /**
     * Handle tap input
     */
    private void handleTap(Map<String, Object> message) {
        Object xObj = message.get("x");
        Object yObj = message.get("y");

        if (!(xObj instanceof Number) || !(yObj instanceof Number)) {
            sendErrorMessage("Invalid tap coordinates");
            return;
        }

        int x = ((Number) xObj).intValue();
        int y = ((Number) yObj).intValue();

        log.debug("handleTap: x={}, y={}", x, y);
        String command = String.format("input tap %d %d", x, y);
        deviceManager.runShell(device, command);
    }

    /**
     * Handle swipe input
     */
    private void handleSwipe(Map<String, Object> message) {
        Object x1Obj = message.get("x1");
        Object y1Obj = message.get("y1");
        Object x2Obj = message.get("x2");
        Object y2Obj = message.get("y2");

        if (!(x1Obj instanceof Number) || !(y1Obj instanceof Number) ||
            !(x2Obj instanceof Number) || !(y2Obj instanceof Number)) {
            sendErrorMessage("Invalid swipe coordinates");
            return;
        }

        int x1 = ((Number) x1Obj).intValue();
        int y1 = ((Number) y1Obj).intValue();
        int x2 = ((Number) x2Obj).intValue();
        int y2 = ((Number) y2Obj).intValue();

        // Optional duration parameter (default 300ms)
        int duration = 300;
        Object durationObj = message.get("duration");
        if (durationObj instanceof Number) {
            duration = ((Number) durationObj).intValue();
        }

        log.debug("handleSwipe: x1={}, y1={}, x2={}, y2={}, duration={}", x1, y1, x2, y2, duration);
        String command = String.format("input swipe %d %d %d %d %d", x1, y1, x2, y2, duration);
        deviceManager.runShell(device, command);
    }

    /**
     * Handle text input
     */
    private void handleText(Map<String, Object> message) {
        String text = (String) message.get("text");
        if (text == null || text.isEmpty()) {
            sendErrorMessage("Missing or empty text");
            return;
        }

        log.debug("handleText: text length={}", text.length());
        // Escape text for shell command
        String escapedText = text.replace(" ", "%s");
        String command = String.format("input text \"%s\"", escapedText);
        deviceManager.runShell(device, command);
    }

    /**
     * Handle keyevent input
     */
    private void handleKeyEvent(Map<String, Object> message) {
        Object keycodeObj = message.get("keycode");
        if (!(keycodeObj instanceof Number)) {
            sendErrorMessage("Invalid keycode");
            return;
        }

        int keycode = ((Number) keycodeObj).intValue();
        log.debug("handleKeyEvent: keycode={}", keycode);
        String command = String.format("input keyevent %d", keycode);
        deviceManager.runShell(device, command);
    }

    /**
     * Wake device screen
     */
    private void wakeDevice() {
        log.debug("wakeDevice: {}", device.serial);
        // Check if screen is awake
        DeviceManager.ShellResult result = deviceManager.runShell(device, "dumpsys power");
        if (result.isSuccess && result.resultList != null) {
            log.trace("wakeDevice: {}", result);
            for (String line : result.resultList) {
                if (line.contains("mWakefulness=")) {
                    // mWakefulness=Dozing
                    // mWakefulness=Asleep
                    log.debug("wakeDevice: {}", line);
                    if (TextUtils.containsAny(line, true, "Asleep", "Dozing")) {
                        // Wake up the device
                        deviceManager.runShell(device, "input keyevent " + AndroidKeyMapper.KEYCODE_WAKEUP);
                        Utils.sleep(1000);
                        // Keep screen on during mirroring
                        deviceManager.runShell(device, "svc power stayon true");
                    }
                    return;
                }
            }
        }
    }

    /**
     * Start the screen capture loop
     */
    private void startCaptureLoop() {
        if (isRunning.get()) {
            log.warn("startCaptureLoop: already running");
            return;
        }

        isRunning.set(true);
        log.info("startCaptureLoop: starting with interval {}ms", intervalMs.get());

        captureTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isPaused.get()) {
                captureAndSendFrame();
            }
        }, 0, intervalMs.get(), TimeUnit.MILLISECONDS);
    }

    /**
     * Capture screenshot and send to client
     */
    private void captureAndSendFrame() {
        try {
            // Capture screenshot using adb
            BufferedImage image = device.jadbDevice.screencap();
            if (image == null) {
                log.warn("captureAndSendFrame: null image returned");
                return;
            }

            // Thumbnailator-based encoding (JPEG/PNG)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String format;
            if (useCompression.get()) {
                // JPEG doesn't support alpha channel; convert ARGB -> RGB
                BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbImage.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();

                Thumbnails.of(rgbImage)
                        .size(rgbImage.getWidth(), rgbImage.getHeight())
                        .outputFormat("jpg")
                        .outputQuality(JPEG_QUALITY)
                        .toOutputStream(baos);
                format = "jpeg";
            } else {
                Thumbnails.of(image)
                        .size(image.getWidth(), image.getHeight())
                        .outputFormat("png")
                        .scale(1.0)
                        .toOutputStream(baos);
                format = "png";
            }
            byte[] imageBytes = baos.toByteArray();

            // Create frame header
            long currentFrameId = frameId.incrementAndGet();
            Map<String, Object> header = new HashMap<>();
            header.put("type", "frame");
            header.put("frameId", currentFrameId);
            header.put("width", image.getWidth());
            header.put("height", image.getHeight());
            header.put("timestamp", System.currentTimeMillis());
            header.put("format", format);
            header.put("size", imageBytes.length);

            String headerJson = GsonHelper.toJson(header);
            byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);

            // Send header length (4 bytes) + header + image data
            ByteBuffer buffer = ByteBuffer.allocate(4 + headerBytes.length + imageBytes.length);
            buffer.putInt(headerBytes.length);
            buffer.put(headerBytes);
            buffer.put(imageBytes);

            send(buffer.array());

            if (log.isTraceEnabled()) {
                log.trace("captureAndSendFrame: sent frame {}, format={}, size={}KB", currentFrameId, format, imageBytes.length / 1024);
            }

        } catch (Exception e) {
            log.error("captureAndSendFrame: error: {}", e.getMessage());
            sendErrorMessage("Capture error: " + e.getMessage());
        }
    }

    /**
     * Set capture interval
     */
    private void setInterval(int newIntervalMs) {
        if (newIntervalMs < 50 || newIntervalMs > 5000) {
            sendErrorMessage("Interval must be between 50 and 5000ms");
            return;
        }

        log.info("setInterval: changing from {} to {}ms", intervalMs.get(), newIntervalMs);
        intervalMs.set(newIntervalMs);

        // Restart capture task with new interval
        if (captureTask != null) {
            captureTask.cancel(false);
        }

        captureTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isPaused.get()) {
                captureAndSendFrame();
            }
        }, 0, newIntervalMs, TimeUnit.MILLISECONDS);

        sendStatusMessage("interval_changed", "Interval set to " + newIntervalMs + "ms");
    }

    /**
     * Set compression mode
     */
    private void setCompression(boolean newCompression) {
        boolean currentCompression = useCompression.get();
        if (currentCompression == newCompression) {
            log.debug("setCompression: already set to {}", newCompression);
            return;
        }

        log.info("setCompression: changing from {} to {}", currentCompression, newCompression);
        useCompression.set(newCompression);

        String format = newCompression ? "JPEG" : "PNG";
        sendStatusMessage("compression_changed", "Compression set to " + format);
    }

    /**
     * Send status message to client
     */
    private void sendStatusMessage(String status, String message) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "status");
            msg.put("status", status);
            msg.put("message", message);
            send(GsonHelper.toJson(msg));
        } catch (IOException e) {
            log.error("sendStatusMessage: error", e);
        }
    }

    /**
     * Send error message to client
     */
    private void sendErrorMessage(String error) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "error");
            msg.put("error", error);
            send(GsonHelper.toJson(msg));
        } catch (IOException e) {
            log.error("sendErrorMessage: error: {}", e.getMessage());
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        log.info("cleanup: device: {}", device.serial);
        isRunning.set(false);

        if (captureTask != null) {
            captureTask.cancel(true);
            captureTask = null;
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Reset screen stay-on setting
        deviceManager.runShell(device, "svc power stayon false");
    }
}

