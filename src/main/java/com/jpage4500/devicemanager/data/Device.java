package com.jpage4500.devicemanager.data;

import java.util.Objects;

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

    // only fetch device detail (IMEI, phone, etc) once -- shouldn't change
    public boolean hasFetchedDetails;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return Objects.equals(serial, device.serial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serial);
    }
}
