package com.jpage4500.devicemanager.manager;

import java.awt.image.BufferedImage;

/**
 * source of screen frames and touch/key input for a mirrored device (local or remote)
 */
public interface ScreenMirrorSource {
    String QUALITY_HIGH = "high";
    String QUALITY_MEDIUM = "medium";
    String QUALITY_LOW = "low";

    interface Listener {
        void onFrame(BufferedImage image, int width, int height);

        void onStatus(String status, String message);

        void onError(String error);

        void onClosed();
    }

    void start(int intervalMs, Listener listener);

    void stop();

    void setQuality(String quality);

    void sendTap(int x, int y);

    void sendSwipe(int x1, int y1, int x2, int y2, int durationMs);

    void sendText(String text);

    void sendKeyEvent(int keycode);

    void executeCommand(String command);
}
