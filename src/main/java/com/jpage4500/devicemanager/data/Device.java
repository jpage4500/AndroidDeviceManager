package com.jpage4500.devicemanager.data;

import java.util.Map;

public class Device {
    public String serial;
    public String model;
    public String phone;
    public String imei;
    public String carrier;
    public String release;
    public String sdk;
    public String freeSpace;
    public String custom1;
    public String custom2;

    // map of property name -> key
    public Map<String, String> customPropertyList;
    // map of application name -> version
    public Map<String, String> customAppList;

    // to show device status (viewing, copying, installing, etc)
    public String status;

    public boolean isOnline;

    // only fetch device detail (IMEI, phone, etc) once -- shouldn't change
    public boolean hasFetchedDetails;

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (serial != null) sb.append(serial);
        if (model != null) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(model);
        }
        if (phone != null) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(phone);
        }
        return sb.toString();
    }
}
