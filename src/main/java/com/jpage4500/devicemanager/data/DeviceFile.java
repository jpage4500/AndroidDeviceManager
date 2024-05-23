package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * represents a file on a device
 */
public class DeviceFile {
    private static final Logger log = LoggerFactory.getLogger(DeviceFile.class);

    public static final String[] GROUP_ARR = new String[]{
            "shell", "everybody", "media_rw"
    };
    public static final String DEFAULT_USER = "shell";

    private static final SimpleDateFormat inDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");

    public String name;
    public long size;
    public long dateMs;
    public String permissions;
    public String user;
    public String group;

    public boolean isDirectory;
    public boolean isSymbolicLink;
    public boolean isReadOnly;

    @Override
    public String toString() {
        return name;
    }

    /**
     * create a new DeviceFile object by parsing result from "ls -al"
     */
    public static DeviceFile fromEntry(String line) {
        //drwxr-xr-x  27 root   root       4096 2008-12-31 19:00 .
        //drwxr-xr-x  27 root   root       4096 2008-12-31 19:00 ..
        //drwxr-xr-x  78 root   root       1620 2024-05-22 10:00 apex
        //lrw-r--r--   1 root   root         11 2008-12-31 19:00 bin -> /system/bin
        //l?????????   ? ?      ?             ?                ? cache -> ?
        //l?????????   ? ?      ?             ?                ? init -> ?
        //lrw-r--r--   1 root   root         21 2008-12-31 19:00 sdcard -> /storage/self/primary
        //drwx--x---   4 shell  everybody    80 2024-05-22 10:00 storage
        if (line == null) return null;
        String[] lineArr = line.split("\\s+", 8);
        if (lineArr.length < 8) {
            //log.trace("fromEntry: invalid line:{}", line);
            return null;
        }
        String permissions = lineArr[0];
        String user = lineArr[2];
        String group = lineArr[3];
        String size = lineArr[4];
        String date = lineArr[5];
        String time = lineArr[6];
        String name = lineArr[7];

        // make sure these fields are not empty
        if (TextUtils.isEmptyAny(group, user, size, date, time, name)) return null;
        // ignore "." folder
        if (TextUtils.equalsAny(name, false, ".", "..")) return null;
        // permissions must be 10+ characters
        if (TextUtils.length(permissions) < 10) return null;
        // ingore any entry that lists "?" for one of it's fields
        if (TextUtils.equalsAny("?", false, group, user, size, date, time, name)) return null;

        DeviceFile file = new DeviceFile();

        // -- date/time --
        try {
            Date dateTime = inDateFormat.parse(date + " " + time);
            file.dateMs = dateTime.getTime();
        } catch (ParseException e) {
            log.debug("fromEntry: invalid date: {}, {}", date + " " + time, e.getMessage());
            return null;
        }

        // -- size --
        try {
            file.size = Long.parseLong(size);
        } catch (NumberFormatException e) {
            log.debug("fromEntry: invalid size: {}, {}", size, e.getMessage());
        }

        // -- permissions --
        file.permissions = permissions;
        file.user = user;
        file.group = group;
        if (permissions.charAt(0) == 'd') file.isDirectory = true;
        else if (permissions.charAt(0) == 'l') file.isSymbolicLink = true;

        // assume user is "shell" and group is "shell"
        char[] checkPermissions = new char[3];
        if (TextUtils.equalsAny(user, false, DEFAULT_USER)) {
            // look at user permissions (chars 1-3)
            permissions.getChars(1, 4, checkPermissions, 0);
        } else if (TextUtils.equalsAny(group, false, GROUP_ARR)) {
            // look at group permissions (chars 4-6)
            permissions.getChars(4, 7, checkPermissions, 0);
        } else {
            // look at other permissions (chars 7-9)
            permissions.getChars(7, 10, checkPermissions, 0);
        }
        if (checkPermissions[0] != 'r') file.isReadOnly = true;

        // -- name --
        file.name = name.replaceAll("\\\\", "");
        if (file.isSymbolicLink) {
            // sdcard -> /storage/self/primary
            int index = file.name.indexOf(" -> ");
            if (index > 0) file.name = file.name.substring(0, index);
        }

        return file;
    }

}
