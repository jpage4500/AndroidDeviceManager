package com.jpage4500.devicemanager.manager.server;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.LogEntry;
import com.jpage4500.devicemanager.data.LogFilter;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.utils.GsonHelper;
import fi.iki.elonen.NanoWSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * WebSocket handler for streaming device logs in real-time
 */
public class LogStreamWebSocket extends NanoWSD.WebSocket implements DeviceManager.DeviceLogListener {
    private static final Logger log = LoggerFactory.getLogger(LogStreamWebSocket.class);

    // message types
    public static final String TYPE_CONNECTED = "connected";
    public static final String TYPE_LOGS = "logs";
    public static final String TYPE_PROCESS_MAP = "processMap";
    public static final String TYPE_STATUS = "status";
    public static final String TYPE_WARNING = "warning";
    public static final String TYPE_ERROR = "error";

    // control actions
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_RESUME = "resume";
    public static final String ACTION_FILTER = "filter";
    public static final String ACTION_PING = "ping";

    private static final int BATCH_INTERVAL_MS = 500;
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

    public LogStreamWebSocket(NanoWSD.IHTTPSession handshakeRequest, Device device, LogFilter filter) {
        super(handshakeRequest);
        this.device = device;
        this.deviceManager = DeviceManager.getInstance();
        this.filter = filter;
    }

    @Override
    protected void onOpen() {
        log.info("onOpen: device: {}", device.serial);

        // send initial connection message
        sendMessage(TYPE_CONNECTED, Map.of(
            "device", device.serial,
            "message", "Connected to log stream"
        ));

        // start batching task
        startBatchTask();

        // start ping task for connection health
        startPingTask();

        // start logging from device
        startLogging();
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
                sendError("Missing 'action' field in control message");
                return;
            }

            switch (action) {
                case ACTION_PAUSE -> handlePause();
                case ACTION_RESUME -> handleResume();
                case ACTION_FILTER -> handleFilterUpdate(controlMessage);
                case ACTION_PING -> handlePing();
                default -> sendError("Unknown action: " + action);
            }
        } catch (Exception e) {
            log.error("onMessage: Exception: device: {}, {}", device.serial, e.getMessage());
            sendError("Error processing message: " + e.getMessage());
        }
    }

    @Override
    protected void onPong(NanoWSD.WebSocketFrame pong) {
        log.trace("onPong: device: {}", device.serial);
    }

    @Override
    protected void onException(IOException exception) {
        log.error("onException: device: {}, {}", device.serial, exception.getMessage());
        cleanup();
    }

    // DeviceLogListener implementation

    @Override
    public void handleLogEntries(List<LogEntry> logEntryList) {
        if (isPaused.get() || !isOpen()) return;

        synchronized (batchBuffer) {
            for (LogEntry entry : logEntryList) {
                // apply filter if set
                if (filter != null && filter.filterList != null && !filter.filterList.isEmpty()) {
                    if (!filter.isMatch(entry)) continue;
                }
                batchBuffer.add(entry);
            }
        }
    }

    @Override
    public void handleProcessMap(Map<String, String> processMap) {
        if (!isOpen()) return;
        sendMessage(TYPE_PROCESS_MAP, Map.of("map", processMap));
    }

    @Override
    public void handleError(String error) {

    }

    // private helper methods

    private void startLogging() {
        if (isLogging.compareAndSet(false, true)) {
            log.debug("startLogging: device: {}", device.serial);
            deviceManager.startLogging(device, null, null, this);
        }
    }

    private void stopLogging() {
        if (isLogging.compareAndSet(true, false)) {
            log.debug("stopLogging: device: {}", device.serial);
            deviceManager.stopLogging(device);
        }
    }

    private void startBatchTask() {
        batchTask = scheduler.scheduleWithFixedDelay(this::sendBatch, BATCH_INTERVAL_MS, BATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startPingTask() {
        pingTask = scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (isOpen()) {
                    ping("heartbeat".getBytes());
                }
            } catch (Exception e) {
                log.error("pingTask: device: {} {}", device.serial, e.getMessage());
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void sendBatch() {
        if (!isOpen() || isPaused.get()) return;

        List<LogEntry> toSend;

        synchronized (batchBuffer) {
            if (batchBuffer.isEmpty()) return;
            toSend = new ArrayList<>(batchBuffer);
            batchBuffer.clear();
        }

        // convert from List<LogEntry> to List<String>
        List<String> logStrList = new ArrayList<>(toSend.size());
        for (LogEntry entry : toSend) {
            logStrList.add(entry.toString());
        }
        String json = GsonHelper.toJson(logStrList);

        try {
            byte[] compressed = gzipCompress(json);
            //log.trace("sendBatch: compressed:{} decompressed:{} ratio:{}", compressed.length, json.length(), String.format("%.2f", (double) compressed.length / (double) json.length()));
            send(compressed);
        } catch (IOException e) {
            log.error("sendBatch: gzip error device:{} size:{}, {}", device.serial, logStrList.size(), e.getMessage());
            log.trace("sendBatch: {}", json);
        }
    }

    private void handlePause() {
        if (isPaused.compareAndSet(false, true)) {
            log.debug("handlePause: device: {}", device.serial);
            stopLogging();
            sendMessage(TYPE_STATUS, Map.of("state", "paused"));
        }
    }

    private void handleResume() {
        if (isPaused.compareAndSet(true, false)) {
            log.trace("handleResume: device: {}", device.serial);
            startLogging();
            sendMessage(TYPE_STATUS, Map.of("state", "resumed"));
        }
    }

    private void handleFilterUpdate(Map<String, Object> controlMessage) {
        String filterText = (String) controlMessage.get("filterText");
        if (filterText == null || filterText.isEmpty()) {
            filter = null;
            log.trace("handleFilterUpdate: device: {} cleared", device.serial);
        } else {
            filter = LogFilter.parse(filterText);
            log.trace("handleFilterUpdate: device: {} filter: {}", device.serial, filterText);
        }
        sendMessage(TYPE_STATUS, Map.of("state", "filter_updated", "filter", filterText != null ? filterText : ""));
    }

    private void handlePing() {
        // respond to client heartbeat ping with pong
        log.trace("handlePing: device: {}", device.serial);
        sendMessage("pong", Map.of("timestamp", System.currentTimeMillis()));
    }

    private void sendMessage(String type, Map<String, Object> data) {
        if (!isOpen()) return;

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.putAll(data);
            String json = GsonHelper.toJson(message);
            send(json);
        } catch (IOException e) {
            log.error("sendMessage: device: {} type: {} {}", device.serial, type, e.getMessage());
            log.error("sendMessage: device: {} type: {} {}", device.serial, type, e.getMessage());
        }
    }

    private void sendError(String errorMessage) {
        sendMessage(TYPE_ERROR, Map.of("message", errorMessage));
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

        log.trace("cleanup: device: {}", device.serial);
    }

    private byte[] gzipCompress(String text) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }
}
