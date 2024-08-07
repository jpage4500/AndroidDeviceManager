package com.jpage4500.devicemanager.utils;

import com.jpage4500.devicemanager.ui.ExploreScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.prefs.Preferences;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static boolean isWindows() {
        return TextUtils.containsIgnoreCase(System.getProperty("os.name"), "windows");
    }

    public static boolean isMac() {
        return TextUtils.containsIgnoreCase(System.getProperty("os.name"), "mac");
    }

    public static boolean isLinux() {
        return TextUtils.containsIgnoreCase(System.getProperty("os.name"), "linux");
    }

    public static void runBackground(Runnable runnable) {
        new Thread(runnable).start();
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

    public static boolean openBrowser(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
            return true;
        } catch (Exception e) {
            log.debug("openBrowser: {}, {}", url, e.getMessage());
            return false;
        }
    }

    public static boolean openFile(File outputfile) {
        try {
            Desktop.getDesktop().open(outputfile);
            return true;
        } catch (IOException e) {
            log.debug("openFile: {}, {}", outputfile.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    public static boolean editFile(File file) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.EDIT)) {
                desktop.edit(file);
            } else if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(file);
            }
            return true;
        } catch (Exception e) {
            log.error("editFile: Exception: {}, {}", file.getAbsolutePath(), e.getMessage());
            return false;
        }
    }

    public static String getDownloadFolder() {
        String downloadFolder = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_DOWNLOAD_FOLDER);
        if (TextUtils.isEmpty(downloadFolder)) {
            // default download folder = ~/Downloads
            downloadFolder = System.getProperty("user.home") + "/Downloads";
        }
        return downloadFolder;
    }

    public enum CompareResult {
        VERSION_EQUALS,
        VERSION_NEWER,
        VERSION_OLDER,
        VERSION_UNKNOWN,
    }

    /**
     * compare 2 version strings using the following logic:
     * - compare each value of a standard version string (ie: A.B.C) from left to right
     *
     * @return VERSION_EQUALS if both versions are equal
     * - VERSION_NEWER if v2 is NEWER than v1
     * - VERSION_OLDER if v2 is OLDER than v1
     * - VERSION_UNKNOWN if one of the versions isn't formatted correctly or unknown
     */
    public static CompareResult compareVersion(String v1, String v2) {
        if (TextUtils.equals(v1, v2)) return CompareResult.VERSION_EQUALS;
        else if (TextUtils.isEmpty(v1)) return CompareResult.VERSION_UNKNOWN;
        else if (TextUtils.isEmpty(v2)) return CompareResult.VERSION_UNKNOWN;

        // try to compare 2 version strings..
        // 4.0.953.beta *VS* 4.0.1000.beta
        // 311.0.0.44.117 *VS* 304.0.0.39.118
        String[] v1Arr = v1.split("\\.");
        String[] v2Arr = v2.split("\\.");
        for (int i = 0; i < v1Arr.length; i++) {
            String v1Split = v1Arr[i];
            if (i >= v2Arr.length) {
                // version array isn't same length
                // v1: 24.08.01.2101, v2: 24.08.01
                // ex: 4.0.953.beta *VS* 4.0
                return CompareResult.VERSION_OLDER;
            }
            String v2Split = v2Arr[i];
            // compare both splits
            try {
                int v1Num = Integer.parseInt(v1Split);
                int v2Num = Integer.parseInt(v2Split);
                // both versions are numbers
                if (v1Num > v2Num) return CompareResult.VERSION_OLDER;
                else if (v1Num < v2Num) return CompareResult.VERSION_NEWER;
                // V1 == V2.. continue looking at next split
            } catch (Exception e) {
                if (!TextUtils.equalsIgnoreCase(v1Split, v2Split)) {
                    // one or the other values aren't numbers..
                    return CompareResult.VERSION_UNKNOWN;
                }
                // V1 == V2.. continue looking at next split
            }
        }

        // if v1 length is shorter than v2 length - and we get here - v2 > v1
        // v1: 24.08.01, v2: 24.08.01.2101
        if (v1Arr.length < v2Arr.length) return CompareResult.VERSION_NEWER;

        // same version
        return CompareResult.VERSION_EQUALS;
    }

    public static String getStackTraceString() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stackTrace.length; i++) {
            // ignore THIS call
            if (i < 2) continue;
            StackTraceElement element = stackTrace[i];
            //String className = element.getClassName();
            String fileName = element.getFileName();
            int lineNumber = element.getLineNumber();
            // only show this app's classes
            if (fileName == null || lineNumber == -1) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("{" + fileName + ", " + element.getMethodName() + ", " + lineNumber + "}");
        }

        return sb.toString();
    }

}
