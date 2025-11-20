package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import fi.iki.elonen.NanoWSD;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.atomic.AtomicReference;

/**
 * websocket handler for streaming device screen as PNG images
 */
public class ScreenStreamWebSocket extends NanoWSD.WebSocket {
    private static final Logger log = LoggerFactory.getLogger(ScreenStreamWebSocket.class);

    // control actions
    public static final String ACTION_CLOSE = "close";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESUME = "resume";
    public static final String ACTION_SET_INTERVAL = "setInterval";
    public static final String ACTION_SET_COMPRESSION = "setCompression";
    public static final String ACTION_SET_QUALITY = "setQuality";
    public static final String ACTION_INPUT = "input";

    // input types
    public static final String INPUT_TAP = "tap";
    public static final String INPUT_SWIPE = "swipe";
    public static final String INPUT_TEXT = "text";
    public static final String INPUT_KEYEVENT = "keyevent";

    // default refresh interval in milliseconds
    private static final int DEFAULT_INTERVAL_MS = 250;

    // Quality settings
    private static final String QUALITY_HIGH = "high";
    private static final String QUALITY_MEDIUM = "medium";
    private static final String QUALITY_LOW = "low";
    private static final float JPEG_QUALITY_HIGH = 0.70f;
    private static final float JPEG_QUALITY_MEDIUM = 0.50f;
    private static final float JPEG_QUALITY_LOW = 0.30f;
    private static final double SCALE_HIGH = 1.0;
    private static final double SCALE_MEDIUM = 0.75;
    private static final double SCALE_LOW = 0.5;

    private final Device device;
    private final DeviceManager deviceManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> captureTask;
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger intervalMs = new AtomicInteger(DEFAULT_INTERVAL_MS);
    private final AtomicLong frameId = new AtomicLong(0);
    private final AtomicBoolean useCompression = new AtomicBoolean(false);
    private final AtomicReference<String> quality = new AtomicReference<>(QUALITY_HIGH);

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

        // wake device screen before starting
        deviceManager.wakeDevice(device);

        // send initial connection message
        sendStatusMessage("connected", "Screen stream started");

        // start capture loop
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
        // connection health check
    }

    @Override
    protected void onException(IOException exception) {
        log.error("onException: device: {}", device.serial, exception);
        cleanup();
    }

    /**
     * handle control messages from client
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
                Number interval = getValue(message, "intervalMs", Number.class);
                if (interval != null) {
                    setInterval(interval.intValue());
                } else {
                    sendErrorMessage("Invalid intervalMs value");
                }
                break;

            case ACTION_SET_COMPRESSION:
                Boolean useCompression = getValue(message, "useCompression", Boolean.class);
                if (useCompression != null) {
                    setCompression(useCompression);
                } else {
                    sendErrorMessage("Invalid useCompression value");
                }
                break;

            case ACTION_SET_QUALITY:
                String quality = getValue(message, "quality", String.class);
                if (quality != null) {
                    setQuality(quality);
                } else {
                    sendErrorMessage("Invalid quality value");
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
     * handle input messages (tap, swipe, text, keyevent)
     */
    private void handleInputMessage(Map<String, Object> message) {
        String type = getValue(message, "type", String.class);
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
     * handle tap input
     */
    private void handleTap(Map<String, Object> message) {
        Number x = getValue(message, "x", Number.class);
        Number y = getValue(message, "y", Number.class);
        if (x == null || y == null) {
            sendErrorMessage("Invalid tap coordinates");
            return;
        }

        log.debug("handleTap: x={}, y={}", x, y);
        String command = String.format("input tap %d %d", x.intValue(), y.intValue());
        deviceManager.runShell(device, command);
    }

    /**
     * handle swipe input
     */
    private void handleSwipe(Map<String, Object> message) {
        Number x1 = getValue(message, "x1", Number.class);
        Number y1 = getValue(message, "y1", Number.class);
        Number x2 = getValue(message, "x2", Number.class);
        Number y2 = getValue(message, "y2", Number.class);
        if (x1 == null || y1 == null || x2 == null || y2 == null) {
            sendErrorMessage("Invalid swipe coordinates");
            return;
        }

        // optional duration parameter (default 300ms)
        int duration = 300;
        Number durationNum = getValue(message, "duration", Number.class);
        if (durationNum != null) {
            duration = durationNum.intValue();
        }

        log.debug("handleSwipe: x1={}, y1={}, x2={}, y2={}, duration={}", x1, y1, x2, y2, duration);
        String command = String.format("input swipe %d %d %d %d %d", x1.intValue(), y1.intValue(), x2.intValue(), y2.intValue(), duration);
        deviceManager.runShell(device, command);
    }

    /**
     * handle text input
     */
    private void handleText(Map<String, Object> message) {
        String text = getValue(message, "text", String.class);
        if (TextUtils.isEmpty(text)) {
            sendErrorMessage("Missing or empty text");
            return;
        }

        log.debug("handleText: text length={}", text.length());
        // escape text for shell command
        String escapedText = text.replace(" ", "%s");
        String command = String.format("input text \"%s\"", escapedText);
        deviceManager.runShell(device, command);
    }

    /**
     * handle keyevent input
     */
    private void handleKeyEvent(Map<String, Object> message) {
        Number keycode = getValue(message, "keycode", Number.class);
        if (keycode == null) {
            sendErrorMessage("Invalid keycode");
            return;
        }

        log.debug("handleKeyEvent: keycode={}", keycode);
        String command = String.format("input keyevent %d", keycode.intValue());
        deviceManager.runShell(device, command);
    }

    /**
     * start the screen capture loop
     */
    private void startCaptureLoop() {
        if (isRunning.get()) {
            log.warn("startCaptureLoop: already running");
            return;
        }

        isRunning.set(true);
        log.info("startCaptureLoop: starting with interval {}ms", intervalMs.get());

        captureTask = scheduler.scheduleWithFixedDelay(() -> {
            if (!isPaused.get()) {
                captureAndSendFrame();
            }
        }, 0, intervalMs.get(), TimeUnit.MILLISECONDS);
    }

    /**
     * capture screenshot and send to client
     */
    private void captureAndSendFrame() {
        try {
            // capture screenshot using adb
            BufferedImage image = device.jadbDevice.screencap();
            if (image == null) {
                log.warn("captureAndSendFrame: null image returned");
                return;
            }

            // thumbnailator-based encoding (JPEG/PNG)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            String format;
            String currentQuality = quality.get();
            double scale = getScale(currentQuality);

            // calculate scaled dimensions
            int scaledWidth = (int) (image.getWidth() * scale);
            int scaledHeight = (int) (image.getHeight() * scale);

            if (useCompression.get()) {
                float jpegQuality = getJpegQuality(currentQuality);

                Thumbnails.of(image)
                    .size(scaledWidth, scaledHeight)
                    .outputFormat("jpg")
                    .outputQuality(jpegQuality)
                    .toOutputStream(baos);
                format = "jpeg";
            } else {
                Thumbnails.of(image)
                    .size(scaledWidth, scaledHeight)
                    .outputFormat("png")
                    .toOutputStream(baos);
                format = "png";
            }
            byte[] imageBytes = baos.toByteArray();

            // create frame header
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

            // send header length (4 bytes) + header + image data
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
     * set capture interval
     */
    private void setInterval(int newIntervalMs) {
        if (newIntervalMs < 50 || newIntervalMs > 5000) {
            sendErrorMessage("Interval must be between 50 and 5000ms");
            return;
        }

        log.info("setInterval: changing from {} to {}ms", intervalMs.get(), newIntervalMs);
        intervalMs.set(newIntervalMs);

        // restart capture task with new interval
        if (captureTask != null) {
            captureTask.cancel(false);
        }

        captureTask = scheduler.scheduleWithFixedDelay(() -> {
            if (!isPaused.get()) {
                captureAndSendFrame();
            }
        }, 0, newIntervalMs, TimeUnit.MILLISECONDS);

        sendStatusMessage("interval_changed", "Interval set to " + newIntervalMs + "ms");
    }

    /**
     * set compression mode
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
     * set quality level
     */
    private void setQuality(String newQuality) {
        if (!QUALITY_HIGH.equals(newQuality) &&
            !QUALITY_MEDIUM.equals(newQuality) &&
            !QUALITY_LOW.equals(newQuality)) {
            sendErrorMessage("Quality must be 'high', 'medium', or 'low'");
            return;
        }

        String currentQuality = quality.get();
        if (currentQuality.equals(newQuality)) {
            log.debug("setQuality: already set to {}", newQuality);
            return;
        }

        log.info("setQuality: changing from {} to {}", currentQuality, newQuality);
        quality.set(newQuality);
        sendStatusMessage("quality_changed", "Quality set to " + newQuality);
    }

    /**
     * get JPEG quality
     */
    private float getJpegQuality(String qualityLevel) {
        switch (qualityLevel) {
            case QUALITY_HIGH:
                return JPEG_QUALITY_HIGH;
            case QUALITY_MEDIUM:
                return JPEG_QUALITY_MEDIUM;
            case QUALITY_LOW:
                return JPEG_QUALITY_LOW;
            default:
                return JPEG_QUALITY_HIGH;
        }
    }

    /**
     * get scale for quality level
     */
    private double getScale(String qualityLevel) {
        switch (qualityLevel) {
            case QUALITY_HIGH:
                return SCALE_HIGH;
            case QUALITY_MEDIUM:
                return SCALE_MEDIUM;
            case QUALITY_LOW:
                return SCALE_LOW;
            default:
                return SCALE_HIGH;
        }
    }

    /**
     * send status message to client
     */
    private void sendStatusMessage(String status, String message) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "status");
            msg.put("status", status);
            msg.put("message", message);
            String json = GsonHelper.toJson(msg);
            log.trace("sendStatusMessage: {}", json);
            send(json);
        } catch (IOException e) {
            log.error("sendStatusMessage: error", e);
        }
    }

    /**
     * send error message to client
     */
    private void sendErrorMessage(String error) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "error");
            msg.put("error", error);
            String json = GsonHelper.toJson(msg);
            log.error("sendErrorMessage: {}", json);
            send(json);
        } catch (IOException e) {
            log.error("sendErrorMessage: error: {}", e.getMessage());
        }
    }

    /**
     * cleanup resources
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

        // reset screen stay-on setting
        deviceManager.runShell(device, "svc power stayon false");
    }

    private <T> T getValue(Map<String, Object> message, String key, Class<T> classOfT) {
        Object value = message.get(key);
        if (classOfT.isInstance(value)) {
            return classOfT.cast(value);
        }
        log.warn("getValue: invalid type: key:{}, val:{}, expected:{}", key, value, classOfT.getSimpleName());
        return null;
    }

}

