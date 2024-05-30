package com.jpage4500.devicemanager.logging;

import org.slf4j.ILoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An implementation of {@link ILoggerFactory} which always returns {@link AppLogger} instances.
 */
public class AppLoggerFactory implements ILoggerFactory {
    private static final boolean EXTRA_LOGGING = false;

    private static final int TAG_MAX_LENGTH = 23;
    private String tagPrefix;
    private int logLevel = Log.VERBOSE;
    private String replaceNewlinesWith;

    private boolean logToFile;
    private int fileLogLevel = Log.DEBUG;
    private File fileLog;
    private final long maxFileSize = (1000000); // 1 Meg;
    private ExecutorService fileExecutorService;

    private final ConcurrentHashMap<String, AppLogger> nameToLogMap = new ConcurrentHashMap<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm:ss", Locale.US);

    /**
     * set a short prefix string to the TAG field
     * NOTE: total tag length is only 23 characters so you'll want to make this VERY short
     * Example: "blr" = "blr_className"
     * - if not set just the "className" will be logged (instead of "com.package.something.className")
     *
     * @param prefix short string to prefix on all log output (tag field)
     */
    public void setTagPrefix(String prefix) {
        if (prefix == null || prefix.equals(tagPrefix)) {
            // no change
            return;
        }
        tagPrefix = prefix;

        // update name for any existing loggers
        for (Map.Entry<String, AppLogger> entry : nameToLogMap.entrySet()) {
            String name = entry.getKey();
            String simpleName = getSimpleName(name);
            String tag = getTag(simpleName);
            AppLogger appLogger = entry.getValue();
            if (EXTRA_LOGGING) System.out.println("setTagPrefix: updating: " + name + " from: " + appLogger.getName() + " to: " + tag);
            appLogger.setName(tag);
        }
    }

    /**
     * set min debug level threshold for which logging will occur
     * examples:
     * Log.VERBOSE = log VERBOSE and higher (DEBUG, INFO, WARN, ERROR)
     * Log.WARN = log WARN and higher (ERROR)
     * - defaults to Log.VERBOSE
     *
     * @param level Log.LEVEL to use
     */
    public void setDebugLevel(int level) {
        logLevel = level;
        if (EXTRA_LOGGING) System.out.println("setDebugLevel: " + level);
    }

    /**
     * replace any newlines ('\n') in log messages with the given string
     */
    public void setReplaceNewlinesWith(String replace) {
        replaceNewlinesWith = replace;
        if (EXTRA_LOGGING) System.out.println("setReplaceNewlinesWith: " + replace);
    }

    public String getReplaceNewlinesWith() {
        return replaceNewlinesWith;
    }

    /**
     * @param logToFile true to log to a file
     */
    public void setLogToFile(boolean logToFile) {
        this.logToFile = logToFile;
    }

    /**
     * @param fileLogLevel level to log to file (default = INFO+)
     */
    public void setFileLogLevel(int fileLogLevel) {
        this.fileLogLevel = fileLogLevel;
    }

    @Override
    public org.slf4j.Logger getLogger(final String name) {
        AppLogger appLogger = this.nameToLogMap.get(name);
        if (appLogger == null) {
            String simpleName = getSimpleName(name);
            String tag = getTag(simpleName);
            appLogger = new AppLogger(simpleName, tag, this);
            if (EXTRA_LOGGING) System.out.println("getLogger: name:" + name + ", tag: " + tag);

            AppLogger existingAppLogger = this.nameToLogMap.putIfAbsent(name, appLogger);
            if (existingAppLogger != null) {
                appLogger = existingAppLogger;
            }
        }

        return appLogger;
    }

    /**
     * change com.package.name.className to just "className"
     */
    private String getSimpleName(String name) {
        if (name == null) {
            System.out.println("getSimpleName: name is NULL!");
            return "";
        }

        int indexOfLastDot = name.lastIndexOf('.');
        // dot must not be the first or last character
        if (indexOfLastDot > 0 && indexOfLastDot < name.length() - 2) {
            return name.substring(indexOfLastDot + 1);
        }
        return "";
    }

    /**
     * @param name class 'simple' name
     * @return logging TAG field; includes optional PREFIX and limits length to TAG_MAX_LENGTH
     */
    private String getTag(final String name) {
        if (name == null) {
            System.out.println("getTag: name is NULL!");
            return "";
        }

        String prefix = tagPrefix != null ? (tagPrefix + "_") : "";
        String tag = prefix + name;
        if (tag.length() > TAG_MAX_LENGTH) {
            tag = tag.substring(0, TAG_MAX_LENGTH);
        }
        return tag;
    }

    /**
     * @return true if app should log this logLevel
     */
    boolean isEnabled(int logLevel) {
        return this.logLevel <= logLevel;
    }

    public SimpleDateFormat getDateFormat() {
        return sdf;
    }

    public boolean shouldLogToFile(int logLevel) {
        return (logToFile && fileLogLevel <= logLevel);
    }

    public File getFileLog() {
        if (fileLog == null) {
            fileLog = new File("device_manager_log.txt");
        }
        return fileLog;
    }

    public ExecutorService getFileExecutorService() {
        if (fileExecutorService == null) {
            fileExecutorService = Executors.newSingleThreadExecutor();
        }
        return fileExecutorService;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }
}
