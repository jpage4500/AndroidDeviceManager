package com.jpage4500.devicemanager.utils;

import javax.swing.*;
import javax.swing.Timer;

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

    public static void runDelayed(int delayMs, boolean isUiThread, Runnable runnable) {
        javax.swing.Timer timer = new Timer(delayMs, e -> {
            if (isUiThread) {
                SwingUtilities.invokeLater(runnable);
            } else {
                runnable.run();
            }
        });
        timer.setRepeats(false);
        timer.start();
    }
}
