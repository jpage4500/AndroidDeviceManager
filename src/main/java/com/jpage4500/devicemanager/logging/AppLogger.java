package com.jpage4500.devicemanager.logging;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.Date;

public class AppLogger extends MarkerIgnoringBase {
    private static final long serialVersionUID = -1227274521521287937L;

    private final AppLoggerFactory appLoggerFactory;

    private final String fullName;

    protected AppLogger(String fullName, String tag, AppLoggerFactory appLoggerFactory) {
        this.fullName = fullName;
        this.name = tag;
        this.appLoggerFactory = appLoggerFactory;
    }

    void setName(String tagName) {
        this.name = tagName;
    }

    /**
     * Only log trace and debug lines if user has enabled debug mode
     *
     * @return true if this log level is enabled
     */
    @Override
    public boolean isTraceEnabled() {
        return appLoggerFactory.isEnabled(Log.VERBOSE);
    }

    @Override
    public void trace(final String msg) {
        if (!isTraceEnabled()) return;
        log(Log.VERBOSE, msg, null);
    }

    @Override
    public void trace(final String format, final Object arg) {
        if (!isTraceEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg);
        log(Log.VERBOSE, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        if (!isTraceEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
        log(Log.VERBOSE, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        if (!isTraceEnabled()) return;
        FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
        log(Log.VERBOSE, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        if (!isTraceEnabled()) return;
        log(Log.VERBOSE, msg, t);
    }

    /**
     * Only log trace and debug lines if user has enabled debug mode
     *
     * @return true if this log level is enabled
     */
    @Override
    public boolean isDebugEnabled() {
        return appLoggerFactory.isEnabled(Log.DEBUG);
    }

    @Override
    public void debug(final String msg) {
        if (!isDebugEnabled()) return;
        log(Log.DEBUG, msg, null);
    }

    @Override
    public void debug(final String format, final Object arg) {
        if (!isDebugEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg);
        log(Log.DEBUG, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        if (!isDebugEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
        log(Log.DEBUG, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        if (!isDebugEnabled()) return;
        FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
        log(Log.DEBUG, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        if (!isDebugEnabled()) return;
        log(Log.DEBUG, msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return appLoggerFactory.isEnabled(Log.INFO);
    }

    @Override
    public void info(final String msg) {
        if (!isInfoEnabled()) return;
        log(Log.INFO, msg, null);
    }

    @Override
    public void info(final String format, final Object arg) {
        if (!isInfoEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg);
        log(Log.INFO, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        if (!isInfoEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
        log(Log.INFO, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void info(final String format, final Object... arguments) {
        if (!isInfoEnabled()) return;
        FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
        log(Log.INFO, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void info(final String msg, final Throwable t) {
        if (!isInfoEnabled()) return;
        log(Log.INFO, msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return appLoggerFactory.isEnabled(Log.WARN);
    }

    @Override
    public void warn(final String msg) {
        if (!isWarnEnabled()) return;
        log(Log.WARN, msg, null);
    }

    @Override
    public void warn(final String format, final Object arg) {
        if (!isWarnEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg);
        log(Log.WARN, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        if (!isWarnEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
        log(Log.WARN, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        if (!isWarnEnabled()) return;
        FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
        log(Log.WARN, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        if (!isWarnEnabled()) return;
        log(Log.WARN, msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return appLoggerFactory.isEnabled(Log.ERROR);
    }

    @Override
    public void error(final String msg) {
        if (!isErrorEnabled()) return;
        log(Log.ERROR, msg, null);
    }

    @Override
    public void error(final String format, final Object arg) {
        if (!isErrorEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg);
        log(Log.ERROR, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        if (!isErrorEnabled()) return;
        FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
        log(Log.ERROR, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void error(final String format, final Object... arguments) {
        if (!isErrorEnabled()) return;
        FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
        log(Log.ERROR, ft.getMessage(), ft.getThrowable());
    }

    @Override
    public void error(final String msg, final Throwable t) {
        if (!isErrorEnabled()) return;
        log(Log.ERROR, msg, t);
    }

    /**
     * handle everything log() method:
     * - logs to file if enabled
     * - logs LONG output to multiple lines if enabled
     */
    private void log(int logLevel, String message, Throwable tr) {
        if (tr != null) {
            // append throwable if set
            message += '\n' + getStackTraceString(tr);
        }

        String replaceNewlinesWith = appLoggerFactory.getReplaceNewlinesWith();
        if (replaceNewlinesWith != null) {
            message = message.replaceAll("\\n", replaceNewlinesWith);
        }

        // date
        System.out.print(appLoggerFactory.getDateFormat().format(new Date()));
        System.out.print(": ");

        // thread
        Thread thread = Thread.currentThread();
        long threadId = thread.getId();
        if (appLoggerFactory.getMainThreadId() == threadId) {
            System.out.print("[UI]: ");
        } else {
            System.out.print("[" + threadId + "]: ");
        }

        // log level
        switch (logLevel) {
            case Log.VERBOSE:
                System.out.print('V');
                break;
            case Log.DEBUG:
                System.out.print('D');
                break;
            case Log.INFO:
                System.out.print('I');
                break;
            case Log.WARN:
                System.out.print('W');
                break;
            case Log.ERROR:
                System.out.print('E');
                break;
        }
        System.out.print(": ");

        // class name
        System.out.print(name);
        System.out.print(": ");

        // message
        System.out.println(message);

        // allow logging to a file or server
        AppLoggerFactory.LogListener logListener = appLoggerFactory.getLogListener();
        if (logListener != null) {
            // send entire (original) message
            logListener.onLogEvent(logLevel, fullName, message);
        }
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }

        // This is to reduce the amount of log spew that apps do in the non-error
        // condition of the network being unavailable.
        Throwable t = tr;
        while (t != null) {
            if (t instanceof UnknownHostException) {
                return "";
            }
            t = t.getCause();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
