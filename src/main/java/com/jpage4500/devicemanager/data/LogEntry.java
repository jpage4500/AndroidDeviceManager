package com.jpage4500.devicemanager.data;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LogEntry {
    public String date;     // formatted date
    public String tid;
    public String pid;
    public String level;
    public String message;

    /**
     * 10-16 11:34:17.824  2063  2063 D PluginAODManager: onNotificationInfoUpdated() 0|com.test.pm|2000|null|10400
     * 10-16 11:34:17.825  2063  2063 I AODNotificationManager: updateVisibleNotifications: 4
     * 10-16 11:34:17.858  2063  2063 D QS      : setQSExpansion 0.0 -588.0
     * 10-16 11:34:18.310  1142  1853 D SemNscXgbMsL1: Probability - Non real time: [0.79989874]
     * 05-13 15:20:12.334  1195  1195 W adbd    : timeout expired while flushing socket, closing
     * 05-13 15:20:12.876  3192  4081 D ModemODPMPoller: Current Modem ODPM (mw): 69, threshold: 800
     */
    public LogEntry(String line, SimpleDateFormat inFormat, SimpleDateFormat outFormat) {
        String[] lineArr = line.split("\\s+", 6);
        if (lineArr.length < 6) return;
        String dateStr = lineArr[0] + " " + lineArr[1];
        try {
            Date inDate = inFormat.parse(dateStr);
            date = outFormat.format(inDate);
        } catch (Exception e) {
            System.out.println("Exception: " + dateStr);
        }

        pid = lineArr[2];
        tid = lineArr[3];
        level = lineArr[4];
        message = lineArr[5];
    }
}
