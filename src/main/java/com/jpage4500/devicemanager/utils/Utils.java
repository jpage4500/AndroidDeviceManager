package com.jpage4500.devicemanager.utils;

public class Utils {
    public static boolean isWindows() {
        return TextUtils.containsIgnoreCase(System.getProperty("os.name"), "windows");
    }

    public static boolean isMac() {
        return TextUtils.containsIgnoreCase(System.getProperty("os.name"), "mac");
    }

    public static boolean isLinux() {
        return TextUtils.containsIgnoreCase(System.getProperty("os.name"), "linux");
    }
}
