package com.jpage4500.devicemanager.data;

public class StatusEvent {
    public final String label;
    public final long timestampMs;

    public StatusEvent(String label) {
        this.label = label;
        this.timestampMs = System.currentTimeMillis();
    }
}
