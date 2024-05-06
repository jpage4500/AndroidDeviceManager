package com.jpage4500.devicemanager.utils;

/**
 * simple class to track and log the time it takes to perform a task
 */
public class Timer {
    private long startTimeMs;

    public Timer() {
        reset();
    }

    public long elapsedTimeMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * @return true if timer elapsed time is < maxTimeMs
     */
    public boolean isValid(long maxTimeMs) {
        return elapsedTimeMs() < maxTimeMs;
    }

    public void reset() {
        startTimeMs = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return System.currentTimeMillis() - startTimeMs + "ms";
    }
}
