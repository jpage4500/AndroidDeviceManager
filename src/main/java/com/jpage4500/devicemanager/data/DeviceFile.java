package com.jpage4500.devicemanager.data;

import java.util.Date;

public class DeviceFile {
    public String name;
    public boolean isDir;
    public boolean isLink; // symbolic link
    public Long size;
    public Date date;
}
