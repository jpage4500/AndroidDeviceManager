package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class LogEntry {
    private static final Logger log = LoggerFactory.getLogger(LogEntry.class);

    public String date;     // formatted date
    public String tid;
    public String pid;
    public String level;
    public String message;

    public Long timestamp;

    /**
     * 10-16 11:34:17.824  2063  2063 D PluginAODManager: onNotificationInfoUpdated() 0|com.test.pm|2000|null|10400
     * 10-16 11:34:17.825  2063  2063 I AODNotificationManager: updateVisibleNotifications: 4
     * 10-16 11:34:17.858  2063  2063 D QS      : setQSExpansion 0.0 -588.0
     * 10-16 11:34:18.310  1142  1853 D SemNscXgbMsL1: Probability - Non real time: [0.79989874]
     * 05-13 15:20:12.334  1195  1195 W adbd    : timeout expired while flushing socket, closing
     * 05-13 15:20:12.876  3192  4081 D ModemODPMPoller: Current Modem ODPM (mw): 69, threshold: 800
     */
    public LogEntry(String line, SimpleDateFormat dateFormat, int year) {
        String[] lineArr = line.split("\\s+", 6);
        if (lineArr.length < 6) return;
        String dayStr = lineArr[0];
        String timeStr = lineArr[1];
        // remove micro-seconds (could be useful to parse but not necessary to display today)
        if (timeStr.length() > 4) timeStr = timeStr.substring(0, timeStr.length() - 4);
        // NOTE: appending year is necessary to parse to Date correctly
        date = year + "-" + dayStr + " " + timeStr;
        try {
            Date eventDate = dateFormat.parse(date);
            timestamp = eventDate.getTime();
        } catch (Exception e) {
            System.out.println("Exception: " + date);
        }

        pid = lineArr[2];
        tid = lineArr[3];
        level = lineArr[4];
        message = lineArr[5];
    }
}
