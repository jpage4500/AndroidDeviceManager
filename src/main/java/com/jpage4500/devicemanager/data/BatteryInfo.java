package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * extended battery details parsed from "dumpsys battery"
 * <p>
 * the first section of the dump is the current state ("level: 72", "temperature: 251", ..) which every
 * device reports. Samsung devices also dump several log buffers which give us historical data:
 * <pre>
 * [EventLogBuffer]
 * 08-03 03:54:21.884  android.intent.action.ACTION_POWER_CONNECTED
 * [BattActionChangedLogBuffer]
 * 08-04 14:03:54.308  Sending ACTION_BATTERY_CHANGED: level:82, status:3, .., voltage:4247, temperature:250, ..
 * </pre>
 * history is merged (de-duped by timestamp) on every refresh so we build up a longer timeline than the
 * device itself keeps. Devices without log buffers still get one sample added per refresh.
 */
public class BatteryInfo {
    private static final Logger log = LoggerFactory.getLogger(BatteryInfo.class);

    // max # of entries to keep in each history list (oldest are dropped)
    public static final int MAX_SAMPLES = 500;
    public static final int MAX_EVENTS = 200;

    // log buffer entry: "08-04 14:03:54.308  Sending ACTION_BATTERY_CHANGED: level:82, .."
    private static final Pattern LOG_LINE = Pattern.compile("^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(.*)$");
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    // allow for device clock skew before assuming a log entry is from last year
    private static final long MAX_FUTURE_MS = 24 * 60 * 60 * 1000L;

    private static final String LOG_BATTERY_CHANGED = "Sending ACTION_BATTERY_CHANGED:";
    private static final String LOG_POWER_CONNECTED = "ACTION_POWER_CONNECTED";
    private static final String LOG_POWER_DISCONNECTED = "ACTION_POWER_DISCONNECTED";

    /**
     * a single point-in-time battery reading
     */
    public static class Sample {
        // NOTE: entries read from a device log buffer use device-local time
        public long timeMs;
        public Integer level;       // 1-100
        public Float tempC;         // celsius
        public Integer voltageMv;
        public Integer currentUa;   // negative when discharging
        public Boolean isCharging;

        public Sample() {
        }

        public Sample(long timeMs) {
            this.timeMs = timeMs;
        }

        /**
         * @return "72%, 25.1°C, 4136 mV, -14 mA, charging"
         */
        public String getDisplay() {
            List<String> partList = new ArrayList<>();
            if (level != null) partList.add(level + "%");
            String temp = formatTempC(tempC);
            if (temp != null) partList.add(temp);
            if (voltageMv != null) partList.add(voltageMv + " mV");
            if (currentUa != null) partList.add((currentUa / 1000) + " mA");
            if (Boolean.TRUE.equals(isCharging)) partList.add("charging");
            return TextUtils.join(partList, ", ");
        }
    }

    /**
     * power connected/disconnected event
     */
    public static class ChargeEvent {
        public long timeMs;
        public boolean isConnected;

        public ChargeEvent() {
        }

        public ChargeEvent(long timeMs, boolean isConnected) {
            this.timeMs = timeMs;
            this.isConnected = isConnected;
        }

        public String getDisplay() {
            return isConnected ? "POWER CONNECTED" : "POWER DISCONNECTED";
        }
    }

    // -- most recent values --
    public Integer level;
    public Float tempC;
    public Integer voltageMv;
    public Integer currentUa;
    public Device.PowerStatus powerStatus = Device.PowerStatus.POWER_NONE;

    // last time "dumpsys battery" was read (host clock)
    public long lastPollMs;

    // battery level/temp over time (oldest first)
    public List<Sample> sampleList = new ArrayList<>();
    // power connected/disconnected events (oldest first)
    public List<ChargeEvent> eventList = new ArrayList<>();

    /**
     * parse "dumpsys battery" output and merge any history into what we've already collected
     *
     * @param timezone device timezone (log buffer entries are device-local time); null to use this host's
     */
    public void update(List<String> lineList, String timezone) {
        if (lineList == null || lineList.isEmpty()) return;
        long nowMs = System.currentTimeMillis();
        ZoneId zone = getZone(timezone);
        Device.PowerStatus prevPowerStatus = powerStatus;

        // current state is collected as the newest sample
        Sample current = new Sample(nowMs);
        Device.PowerStatus status = Device.PowerStatus.POWER_NONE;
        List<Sample> newSampleList = new ArrayList<>();
        List<ChargeEvent> newEventList = new ArrayList<>();

        for (String line : lineList) {
            String text = line.trim();
            if (text.isEmpty()) continue;

            Matcher matcher = LOG_LINE.matcher(text);
            if (matcher.matches()) {
                // -- log buffer entry --
                long timeMs = parseLogTime(matcher.group(1), zone, nowMs);
                if (timeMs <= 0) continue;
                String body = matcher.group(2);
                if (TextUtils.contains(body, LOG_POWER_DISCONNECTED)) {
                    newEventList.add(new ChargeEvent(timeMs, false));
                } else if (TextUtils.contains(body, LOG_POWER_CONNECTED)) {
                    newEventList.add(new ChargeEvent(timeMs, true));
                } else if (TextUtils.startsWith(body, LOG_BATTERY_CHANGED)) {
                    Sample sample = parseBatteryChanged(body.substring(LOG_BATTERY_CHANGED.length()), timeMs);
                    if (sample != null) newSampleList.add(sample);
                }
                continue;
            }

            // -- current state: "name: value" --
            int pos = text.indexOf(": ");
            if (pos <= 0) continue;
            String name = text.substring(0, pos).trim();
            String value = text.substring(pos + 2).trim();
            switch (name) {
                //  level: 72
                case "level" -> current.level = toInt(value);
                //  temperature: 251
                case "temperature" -> current.tempC = toTempC(value);
                //  voltage: 4136
                case "voltage" -> current.voltageMv = toInt(value);
                //  current now: -14843
                case "current now" -> current.currentUa = toInt(value);
                //  AC powered: false
                case "AC powered" -> {
                    if (Boolean.parseBoolean(value)) status = Device.PowerStatus.POWER_AC;
                }
                //  USB powered: false
                case "USB powered" -> {
                    if (Boolean.parseBoolean(value)) status = Device.PowerStatus.POWER_USB;
                }
                //  Wireless powered: false
                case "Wireless powered" -> {
                    if (Boolean.parseBoolean(value)) status = Device.PowerStatus.POWER_WIRELESS;
                }
                //  Dock powered: false
                case "Dock powered" -> {
                    if (Boolean.parseBoolean(value)) status = Device.PowerStatus.POWER_DOCK;
                }
            }
        }

        // some Android TV devices list battery level as 0
        boolean isValidLevel = isValidLevel(current.level);
        if (isValidLevel) level = current.level;
        else current.level = null;
        tempC = current.tempC;
        voltageMv = current.voltageMv;
        currentUa = current.currentUa;
        powerStatus = status;
        boolean isCharging = status != Device.PowerStatus.POWER_NONE;
        current.isCharging = isCharging;

        if (isValidLevel) newSampleList.add(current);
        mergeSamples(newSampleList);
        mergeEvents(newEventList);

        // devices without an event log buffer: track connect/disconnect ourselves
        if (lastPollMs > 0 && isCharging != (prevPowerStatus != Device.PowerStatus.POWER_NONE)
            && !isEventReported(lastPollMs, isCharging)) {
            mergeEvents(List.of(new ChargeEvent(nowMs, isCharging)));
        }
        lastPollMs = nowMs;
    }

    /**
     * @return battery temperature for display ("25.1°C") or null if unknown
     */
    public String getTempDisplay() {
        return formatTempC(tempC);
    }

    /**
     * @return battery voltage for display ("4136 mV") or null if unknown
     */
    public String getVoltageDisplay() {
        if (voltageMv == null) return null;
        return voltageMv + " mV";
    }

    /**
     * @return current for display ("-14 mA"; negative when discharging) or null if unknown
     */
    public String getCurrentDisplay() {
        if (currentUa == null) return null;
        return (currentUa / 1000) + " mA";
    }

    private static String formatTempC(Float tempC) {
        if (tempC == null) return null;
        return String.format(Locale.US, "%.1f°C", tempC);
    }

    /**
     * "level:82, status:3, .., ac:false, usb:false, wireless:false, pogo:false, .., voltage:4247, temperature:250, .., current_avg:-260937, .."
     *
     * @return sample or null if the entry has no usable battery level
     */
    private static Sample parseBatteryChanged(String text, long timeMs) {
        Sample sample = new Sample(timeMs);
        boolean isCharging = false;
        for (String field : text.split(",")) {
            int pos = field.indexOf(':');
            if (pos <= 0) continue;
            String name = field.substring(0, pos).trim();
            String value = field.substring(pos + 1).trim();
            switch (name) {
                case "level" -> sample.level = toInt(value);
                case "temperature" -> sample.tempC = toTempC(value);
                case "voltage" -> sample.voltageMv = toInt(value);
                case "current_avg" -> sample.currentUa = toInt(value);
                case "ac", "usb", "wireless", "pogo" -> {
                    if (Boolean.parseBoolean(value)) isCharging = true;
                }
            }
        }
        if (!isValidLevel(sample.level)) return null;
        sample.isCharging = isCharging;
        return sample;
    }

    /**
     * log buffer entries have no year: "08-04 14:03:54.308"
     *
     * @return epoch millis or 0 if it can't be parsed
     */
    private static long parseLogTime(String text, ZoneId zone, long nowMs) {
        int year = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone).getYear();
        // if the entry looks like it's in the future it's from last year (Dec -> Jan rollover)
        for (int i = 0; i < 2; i++) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse((year - i) + "-" + text, LOG_TIME_FORMAT);
                long timeMs = dateTime.atZone(zone).toInstant().toEpochMilli();
                if (timeMs <= nowMs + MAX_FUTURE_MS) return timeMs;
            } catch (DateTimeException e) {
                // Feb 29 on a non-leap year, etc
                log.trace("parseLogTime: BAD_DATE: {}, {}", text, e.getMessage());
            }
        }
        return 0;
    }

    private void mergeSamples(List<Sample> addList) {
        if (addList.isEmpty()) return;
        Set<Long> timeSet = new HashSet<>();
        for (Sample sample : sampleList) timeSet.add(sample.timeMs);
        boolean isChanged = false;
        for (Sample sample : addList) {
            if (timeSet.add(sample.timeMs)) {
                sampleList.add(sample);
                isChanged = true;
            }
        }
        if (!isChanged) return;
        sampleList.sort(Comparator.comparingLong(sample -> sample.timeMs));
        trim(sampleList, MAX_SAMPLES);
    }

    private void mergeEvents(List<ChargeEvent> addList) {
        if (addList.isEmpty()) return;
        Set<Long> timeSet = new HashSet<>();
        for (ChargeEvent event : eventList) timeSet.add(event.timeMs);
        boolean isChanged = false;
        for (ChargeEvent event : addList) {
            if (timeSet.add(event.timeMs)) {
                eventList.add(event);
                isChanged = true;
            }
        }
        if (!isChanged) return;
        eventList.sort(Comparator.comparingLong(event -> event.timeMs));
        trim(eventList, MAX_EVENTS);
    }

    /**
     * @return true if the device already reported this power change itself since timeMs
     */
    private boolean isEventReported(long timeMs, boolean isConnected) {
        if (eventList.isEmpty()) return false;
        ChargeEvent newest = eventList.get(eventList.size() - 1);
        return newest.timeMs >= timeMs && newest.isConnected == isConnected;
    }

    private static void trim(List<?> list, int maxSize) {
        int extra = list.size() - maxSize;
        if (extra > 0) list.subList(0, extra).clear();
    }

    private static boolean isValidLevel(Integer level) {
        return level != null && level >= 1 && level <= 100;
    }

    /**
     * dumpsys reports temperature in 1/10th degrees celsius (251 = 25.1C)
     */
    private static Float toTempC(String value) {
        Integer temp = toInt(value);
        // 0 means unknown; ignore anything outside of -50C to 100C
        if (temp == null || temp == 0 || temp < -500 || temp > 1000) return null;
        return temp / 10f;
    }

    private static Integer toInt(String value) {
        if (TextUtils.isEmpty(value)) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ZoneId getZone(String timezone) {
        if (TextUtils.notEmpty(timezone)) {
            try {
                return ZoneId.of(timezone);
            } catch (DateTimeException e) {
                log.debug("getZone: BAD_TIMEZONE: {}", timezone);
            }
        }
        return ZoneId.systemDefault();
    }

    @Override
    public String toString() {
        return "level:" + level + ", temp:" + getTempDisplay() + ", " + powerStatus
            + ", samples:" + sampleList.size() + ", events:" + eventList.size();
    }
}
