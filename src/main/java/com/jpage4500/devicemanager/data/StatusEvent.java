package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.TextUtils;

public class StatusEvent {
    public final String label;
    public final long timestampMs;
    /** -1 = no bar / indeterminate, 0-100 = progress fill */
    public final int progress;
    /** operation failed -> render with error styling */
    public final boolean isError;
    /** completion detail shown in the History popup but not on the status label */
    public final String detail;

    public StatusEvent(String label) {
        this(label, -1, false, null);
    }

    public StatusEvent(String label, int progress) {
        this(label, progress, false, null);
    }

    public StatusEvent(String label, int progress, boolean isError) {
        this(label, progress, isError, null);
    }

    public StatusEvent(String label, int progress, boolean isError, String detail) {
        this.label = label;
        this.progress = progress;
        this.isError = isError;
        this.detail = TextUtils.truncate(detail, 10000);
        this.timestampMs = System.currentTimeMillis();
    }
}
