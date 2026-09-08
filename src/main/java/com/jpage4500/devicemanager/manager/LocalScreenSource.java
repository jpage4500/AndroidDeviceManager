package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * mirrors a locally connected device by grabbing screenshots over adb
 */
public class LocalScreenSource implements ScreenMirrorSource {
    private static final Logger log = LoggerFactory.getLogger(LocalScreenSource.class);

    private static final double SCALE_HIGH = 1.0;
    private static final double SCALE_MEDIUM = 0.75;
    private static final double SCALE_LOW = 0.5;

    // give up and report an error after this many failed captures in a row
    private static final int MAX_CAPTURE_ERRORS = 3;

    private final Device device;
    private final DeviceManager deviceManager;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService inputExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<String> quality = new AtomicReference<>(QUALITY_HIGH);

    private ScheduledFuture<?> captureTask;
    private Listener listener;
    private int captureErrors;

    public LocalScreenSource(Device device) {
        this.device = device;
        this.deviceManager = DeviceManager.getInstance();
    }

    @Override
    public void start(int intervalMs, Listener listener) {
        stopCaptureTask();
        this.listener = listener;
        this.captureErrors = 0;
        log.debug("start: {}, intervalMs:{}", device.serial, intervalMs);

        inputExecutor.submit(() -> deviceManager.wakeDevice(device));
        listener.onStatus("connected", "Screen stream started");
        captureTask = scheduler.scheduleWithFixedDelay(this::captureFrame, 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        log.debug("stop: {}", device.serial);
        stopCaptureTask();
        scheduler.shutdownNow();
        inputExecutor.shutdown();
    }

    @Override
    public void setQuality(String quality) {
        this.quality.set(quality);
        if (listener != null) listener.onStatus("quality_changed", "Quality set to " + quality);
    }

    @Override
    public void sendTap(int x, int y) {
        runInput(String.format("input tap %d %d", x, y));
    }

    @Override
    public void sendSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        runInput(String.format("input swipe %d %d %d %d %d", x1, y1, x2, y2, durationMs));
    }

    @Override
    public void sendText(String text) {
        runInput(String.format("input text \"%s\"", text.replace(" ", "%s")));
    }

    @Override
    public void sendKeyEvent(int keycode) {
        runInput(String.format("input keyevent %d", keycode));
    }

    @Override
    public void executeCommand(String command) {
        runInput(command);
    }

    private void runInput(String command) {
        if (inputExecutor.isShutdown()) return;
        inputExecutor.submit(() -> {
            log.debug("runInput: {}", command);
            deviceManager.runShell(device, command);
        });
    }

    private void captureFrame() {
        Listener listener = this.listener;
        if (listener == null) return;
        try {
            BufferedImage image = deviceManager.captureScreenshotInternal(device);
            if (image == null) throw new Exception("no image returned");
            captureErrors = 0;

            double scale = getScale(quality.get());
            BufferedImage frame = image;
            if (scale < 1.0) {
                frame = Thumbnails.of(image)
                    .size((int) (image.getWidth() * scale), (int) (image.getHeight() * scale))
                    .asBufferedImage();
            }
            listener.onFrame(frame, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            log.error("captureFrame: {}, error:{}", device.serial, e.getMessage());
            if (++captureErrors < MAX_CAPTURE_ERRORS) return;
            // stop capturing - the window decides whether to reconnect
            stopCaptureTask();
            listener.onError("Capture error: " + e.getMessage());
        }
    }

    private void stopCaptureTask() {
        if (captureTask != null) {
            captureTask.cancel(false);
            captureTask = null;
        }
    }

    private double getScale(String quality) {
        return switch (quality) {
            case QUALITY_MEDIUM -> SCALE_MEDIUM;
            case QUALITY_LOW -> SCALE_LOW;
            default -> SCALE_HIGH;
        };
    }
}
