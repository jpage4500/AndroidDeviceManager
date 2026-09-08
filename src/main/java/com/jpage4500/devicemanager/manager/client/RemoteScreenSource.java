package com.jpage4500.devicemanager.manager.client;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.ScreenMirrorSource;

import java.awt.image.BufferedImage;

/**
 * mirrors a device shared by another ADM instance
 */
public class RemoteScreenSource implements ScreenMirrorSource {
    private final RemoteConnection remoteConnection;
    private final String serial;

    public RemoteScreenSource(Device device) {
        this.remoteConnection = device.remoteConnection;
        this.serial = device.serial;
    }

    @Override
    public void start(int intervalMs, Listener listener) {
        remoteConnection.startScreenStream(serial, intervalMs, true, new RemoteConnection.ScreenStreamListener() {
            @Override
            public void onFrame(BufferedImage image, int width, int height) {
                listener.onFrame(image, width, height);
            }

            @Override
            public void onStatus(String status, String message) {
                listener.onStatus(status, message);
            }

            @Override
            public void onError(String error) {
                listener.onError(error);
            }

            @Override
            public void onClosed() {
                listener.onClosed();
            }
        });
    }

    @Override
    public void stop() {
        remoteConnection.stopScreenStream(serial);
    }

    @Override
    public void setQuality(String quality) {
        remoteConnection.setScreenStreamQuality(serial, quality);
    }

    @Override
    public void sendTap(int x, int y) {
        remoteConnection.sendScreenInputTap(serial, x, y);
    }

    @Override
    public void sendSwipe(int x1, int y1, int x2, int y2, int durationMs) {
        remoteConnection.sendScreenInputSwipe(serial, x1, y1, x2, y2, durationMs);
    }

    @Override
    public void sendText(String text) {
        remoteConnection.sendScreenInputText(serial, text);
    }

    @Override
    public void sendKeyEvent(int keycode) {
        remoteConnection.sendScreenInputKeyEvent(serial, keycode);
    }

    @Override
    public void executeCommand(String command) {
        remoteConnection.executeCommand(serial, command);
    }
}
