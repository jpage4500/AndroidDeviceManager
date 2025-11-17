package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.utils.GsonHelper;
import fi.iki.elonen.NanoWSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket handler for streaming device logs in real-time
 */
public class LogStreamWebSocket extends NanoWSD.WebSocket implements DeviceManager.DeviceLogListener {
    private static final Logger log = LoggerFactory.getLogger(LogStreamWebSocket.class);

    private static final int BATCH_INTERVAL_MS = 500;
    private static final int MAX_BATCH_SIZE = 50;
    private static final int MAX_BUFFER_SIZE = 1000;
    private static final long PING_INTERVAL_MS = 30000; // 30 seconds

    private final Device device;
    private final DeviceManager deviceManager;
    private LogFilter filter;
    private final List<LogEntry> batchBuffer = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isLogging = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> batchTask;
    private ScheduledFuture<?> pingTask;
    private int droppedCount = 0;

    public LogStreamWebSocket(NanoWSD.IHTTPSession handshakeRequest, Device device, LogFilter filter) {
        super(handshakeRequest);
        this.device = device;
        this.deviceManager = DeviceManager.getInstance();
        this.filter = filter;
    }

    @Override
    protected void onOpen() {
        log.info("WebSocket opened for device: {}", device.serial);

        // Send initial connection message
        sendMessage("connected", Map.of(
            "device", device.serial,
            "message", "Connected to log stream"
        ));

        // Start batching task
        startBatchTask();

        // Start ping task for connection health
        startPingTask();

        // Start logging from device
        startLogging();
    }

    @Override
    protected void onClose(NanoWSD.WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
        log.info("WebSocket closed for device: {}, code: {}, reason: {}", device.serial, code, reason);
        cleanup();
    }

    @Override
    protected void onMessage(NanoWSD.WebSocketFrame message) {
        try {
            String messageText = message.getTextPayload();
            log.debug("Received message: {}", messageText);

            @SuppressWarnings("unchecked")
            Map<String, Object> controlMessage = GsonHelper.fromJson(messageText, Map.class);
            String action = (String) controlMessage.get("action");

            if (action == null) {
                sendError("Missing 'action' field in control message");
                return;
            }

            switch (action) {
                case "pause" -> handlePause();
                case "resume" -> handleResume();
                case "filter" -> handleFilterUpdate(controlMessage);
                default -> sendError("Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("Error processing message", e);
            sendError("Error processing message: " + e.getMessage());
        }
    }

    @Override
    protected void onPong(NanoWSD.WebSocketFrame pong) {
        log.trace("Received pong from device: {}", device.serial);
    }

    @Override
    protected void onException(IOException exception) {
        log.error("WebSocket exception for device: {}", device.serial, exception);
        cleanup();
    }

    // DeviceLogListener implementation

    @Override
    public void handleLogEntries(List<LogEntry> logEntryList) {
        if (isPaused.get() || !isOpen()) {
            return;
        }

        synchronized (batchBuffer) {
            for (LogEntry entry : logEntryList) {
                // Apply filter if set
                if (filter != null && filter.filterList != null && !filter.filterList.isEmpty()) {
                    if (!filter.isMatch(entry)) {
                        continue;
                    }
                }

                // Check buffer size limit
                if (batchBuffer.size() >= MAX_BUFFER_SIZE) {
                    // Drop oldest entries
                    int toDrop = Math.min(MAX_BATCH_SIZE, batchBuffer.size() - MAX_BUFFER_SIZE + MAX_BATCH_SIZE);
                    batchBuffer.subList(0, toDrop).clear();
                    droppedCount += toDrop;
                }

                batchBuffer.add(entry);
            }
        }
    }

    @Override
    public void handleProcessMap(Map<String, String> processMap) {
        if (!isOpen()) {
            return;
        }
        sendMessage("processMap", Map.of("map", processMap));
    }

    // Private helper methods

    private void startLogging() {
        if (isLogging.compareAndSet(false, true)) {
            log.debug("Starting log capture for device: {}", device.serial);
            deviceManager.startLogging(device, null, this);
        }
    }

    private void stopLogging() {
        if (isLogging.compareAndSet(true, false)) {
            log.debug("Stopping log capture for device: {}", device.serial);
            deviceManager.stopLogging(device);
        }
    }

    private void startBatchTask() {
        batchTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendBatch();
            } catch (Exception e) {
                log.error("Error sending batch", e);
            }
        }, BATCH_INTERVAL_MS, BATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startPingTask() {
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isOpen()) {
                    ping("heartbeat".getBytes());
                }
            } catch (Exception e) {
                log.error("Error sending ping", e);
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void sendBatch() {
        if (!isOpen() || isPaused.get()) {
            return;
        }

        List<LogEntry> toSend;
        int dropped;

        synchronized (batchBuffer) {
            if (batchBuffer.isEmpty() && droppedCount == 0) {
                return;
            }

            toSend = new ArrayList<>(batchBuffer);
            batchBuffer.clear();
            dropped = droppedCount;
            droppedCount = 0;
        }

        if (!toSend.isEmpty()) {
            sendMessage("logs", Map.of("entries", toSend));
        }

        if (dropped > 0) {
            sendMessage("warning", Map.of(
                "message", "Dropped " + dropped + " log entries due to buffer overflow"
            ));
        }
    }

    private void handlePause() {
        if (isPaused.compareAndSet(false, true)) {
            log.debug("Pausing log stream for device: {}", device.serial);
            stopLogging();
            sendMessage("status", Map.of("state", "paused"));
        }
    }

    private void handleResume() {
        if (isPaused.compareAndSet(true, false)) {
            log.debug("Resuming log stream for device: {}", device.serial);
            startLogging();
            sendMessage("status", Map.of("state", "resumed"));
        }
    }

    private void handleFilterUpdate(Map<String, Object> controlMessage) {
        String filterText = (String) controlMessage.get("filterText");
        if (filterText == null || filterText.isEmpty()) {
            filter = null;
            log.debug("Cleared filter for device: {}", device.serial);
        } else {
            filter = LogFilter.parse(filterText);
            log.debug("Updated filter for device: {}, filter: {}", device.serial, filterText);
        }
        sendMessage("status", Map.of(
            "state", "filter_updated",
            "filter", filterText != null ? filterText : ""
        ));
    }

    private void sendMessage(String type, Map<String, Object> data) {
        if (!isOpen()) {
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.putAll(data);
            String json = GsonHelper.toJson(message);
            send(json);
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }

    private void sendError(String errorMessage) {
        sendMessage("error", Map.of("message", errorMessage));
    }

    private void cleanup() {
        stopLogging();

        if (batchTask != null && !batchTask.isCancelled()) {
            batchTask.cancel(false);
        }

        if (pingTask != null && !pingTask.isCancelled()) {
            pingTask.cancel(false);
        }

        scheduler.shutdown();

        synchronized (batchBuffer) {
            batchBuffer.clear();
        }

        log.debug("Cleaned up WebSocket resources for device: {}", device.serial);
    }
}

