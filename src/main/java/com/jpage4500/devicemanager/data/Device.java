package com.jpage4500.devicemanager.data;

public class Device {
    public String serial;
    public String model;
    public String phone;
    public String imei;
    public String carrier;
    public String custom1;
    public String custom2;

    // to show device status (viewing, copying, installing, etc)
    public String status;

    public boolean isOnline;

    // only fetch device detail (IMEI, phone, etc) once -- shouldn't change
    public boolean hasFetchedDetails;
}
