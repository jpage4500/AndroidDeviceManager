package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.FileUtils;

public class SizeData {
    public Long size;

    public SizeData(Long size) {
        this.size = size;
    }

    @Override
    public String toString() {
        if (size == null) return "";
        return FileUtils.bytesToDisplayString(size);
    }
}
