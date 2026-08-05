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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * battery history parsed from "dumpsys batterystats --history"
 * <p>
 * every device keeps this buffer, unlike the log buffers in "dumpsys battery" which only Samsung
 * devices dump - so this is the portable source of battery history. how far back it reaches depends
 * on how busy a device is (the buffer is size-capped, not time-capped): a busy phone holds roughly a
 * day, an idle one several days.
 * <p>
 * there are 2 dump formats and both are parsed. newer devices (Android 14+) print an absolute
 * device-local timestamp per entry:
 * <pre>
 * Battery History [Format: 2] (100% used, 4105KB used of 4096KB, ..):
 *   08-03 17:44:49.027 100 status=discharging health=good plug=wireless temp=299 volt=4395 ..
 *   08-04 11:30:20.794 100 temp=262
 * </pre>
 * older devices print an offset per entry, with periodic "TIME:" markers carrying the wall clock at
 * that offset - so an entry's real time is (latest marker) + (entry offset - marker offset):
 * <pre>
 * Battery History (97% used, 3980KB used of 4096KB, ..):
 *                     0 (20) TIME: 2026-08-05-06-19-47-874
 *                     0 (1) 031 status=discharging health=good plug=none temp=270 volt=3779 ..
 *        +1h59m03s002ms (2) 024 temp=260 -cellular_high_tx_power
 * </pre>
 * in both formats fields are delta-encoded - printed only when they change - so the last seen
 * temp/voltage/status/plug is carried forward onto later entries.
 * <p>
 * this is fetched on demand (it costs several seconds per device) and isn't persisted.
 */
public class BatteryHistory {
    private static final Logger log = LoggerFactory.getLogger(BatteryHistory.class);

    // "Battery History [Format: 2] (100% used, .." - older devices omit the "[Format: N]" entirely
    private static final Pattern HEADER_FORMAT = Pattern.compile("Battery History(?: \\[Format: (\\d+)])?");
    // prints an absolute timestamp per entry
    private static final int FORMAT_ABSOLUTE = 2;
    // a header with no format is the older dump, which prints an offset per entry
    private static final int FORMAT_RELATIVE = 1;

    // "       +1h59m03s002ms (2) 024 temp=260 .." / "     0 (20) TIME: .."
    private static final Pattern RELATIVE_LINE = Pattern.compile("^(\\S+)\\s+\\(\\d+\\)\\s*(.*)$");
    // "+1h59m03s002ms" - note "m" is minutes and "ms" is millis, hence the lookahead
    private static final Pattern RELATIVE_OFFSET = Pattern.compile(
        "^\\+?(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:(\\d+)s)?(?:(\\d+)ms)?$");
    // "TIME: 2026-08-05-06-19-47-874" / "RESET:TIME: 2026-08-04-16-34-10" (millis are optional)
    private static final Pattern MARKER_TIME = Pattern.compile("TIME:\\s*(\\d{4}(?:-\\d{2}){5})(?:-(\\d{3}))?");
    private static final DateTimeFormatter MARKER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss", Locale.US);

    // "100 status=charging temp=299" - battery level is printed first, zero padded
    private static final Pattern LEVEL = Pattern.compile("^(\\d{3})(\\s|$)");

    // absolute format entry: "08-03 17:44:49.027 100 status=discharging .."
    private static final Pattern ABSOLUTE_LINE = Pattern.compile("^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(.*)$");
    private static final DateTimeFormatter ABSOLUTE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    // allow for device clock skew before assuming an entry is from last year
    private static final long MAX_FUTURE_MS = 24 * 60 * 60 * 1000L;

    private static final String FLAG_PLUGGED = "+plugged";
    private static final String FLAG_UNPLUGGED = "-plugged";
    private static final String PLUG_NONE = "none";
    private static final String STATUS_CHARGING = "charging";

    // a charger can flap plug=/status= every few seconds while negotiating; merge bands separated by
    // less than this so a wireless charger doesn't render as hundreds of 5 second slivers
    private static final long MERGE_SPAN_MS = 2 * 60 * 1000L;

    /**
     * values carried forward between entries, since each entry only prints what changed
     */
    private static class Reading {
        Float tempC;
        Integer voltageMv;
        String status;
        String plug;
        Boolean isPlugged;
    }

    // history format the device reported (0 if the header was never seen)
    private int format;
    // oldest first
    private final List<BatteryInfo.Sample> sampleList = new ArrayList<>();

    /**
     * @param timezone device timezone (entries are device-local time); null to use this host's
     */
    public static BatteryHistory parse(List<String> lineList, String timezone) {
        BatteryHistory history = new BatteryHistory();
        if (lineList == null || lineList.isEmpty()) return history;
        long nowMs = System.currentTimeMillis();
        ZoneId zone = getZone(timezone);
        Reading reading = new Reading();

        // relative format only: wall clock of the most recent "TIME:" marker, and the offset it sat at
        long anchorTimeMs = 0;
        long anchorOffsetMs = -1;

        for (String line : lineList) {
            String text = line.trim();
            if (text.isEmpty()) continue;

            Matcher headerMatcher = HEADER_FORMAT.matcher(text);
            if (headerMatcher.find()) {
                Integer value = BatteryInfo.toInt(headerMatcher.group(1));
                history.format = value != null ? value : FORMAT_RELATIVE;
                continue;
            }

            if (history.format == FORMAT_RELATIVE) {
                Matcher lineMatcher = RELATIVE_LINE.matcher(text);
                if (!lineMatcher.matches()) continue;
                long offsetMs = parseOffset(lineMatcher.group(1));
                if (offsetMs < 0) continue;
                String body = lineMatcher.group(2);

                Long markerMs = parseMarkerTime(body, zone);
                if (markerMs != null) {
                    anchorTimeMs = markerMs;
                    anchorOffsetMs = offsetMs;
                    continue;
                }
                // entries before the first marker have no wall clock to anchor to
                if (anchorOffsetMs < 0) continue;
                history.addSample(body, anchorTimeMs + (offsetMs - anchorOffsetMs), reading);
            } else {
                Matcher lineMatcher = ABSOLUTE_LINE.matcher(text);
                if (!lineMatcher.matches()) continue;
                String body = lineMatcher.group(2);
                // this format stamps every entry, so markers are only a clock anchor we don't need
                if (parseMarkerTime(body, zone) != null) continue;
                long timeMs = parseAbsoluteTime(lineMatcher.group(1), zone, nowMs);
                if (timeMs <= 0) continue;
                history.addSample(body, timeMs, reading);
            }
        }

        history.sampleList.sort(Comparator.comparingLong(sample -> sample.timeMs));
        history.dropDuplicateTimes();
        log.debug("parse: format:{}, lines:{}, samples:{}", history.format, lineList.size(), history.sampleList.size());
        return history;
    }

    /**
     * parse one entry body ("031 status=discharging temp=270 volt=3779 -plugged") into a sample,
     * carrying forward any field this entry didn't print
     */
    private void addSample(String body, long timeMs, Reading reading) {
        Matcher levelMatcher = LEVEL.matcher(body);
        if (!levelMatcher.find()) return;
        Integer level = BatteryInfo.toInt(levelMatcher.group(1));
        // some Android TV devices report a level of 0
        if (!BatteryInfo.isValidLevel(level)) return;

        for (String field : body.substring(levelMatcher.end(1)).split("\\s+")) {
            if (TextUtils.startsWith(field, FLAG_PLUGGED)) {
                reading.isPlugged = true;
                continue;
            } else if (TextUtils.startsWith(field, FLAG_UNPLUGGED)) {
                reading.isPlugged = false;
                continue;
            }
            int pos = field.indexOf('=');
            if (pos <= 0) continue;
            String name = field.substring(0, pos);
            String value = field.substring(pos + 1);
            switch (name) {
                //  temp=299 (1/10th degrees celsius)
                case "temp" -> {
                    Float tempC = BatteryInfo.toTempC(value);
                    if (tempC != null) reading.tempC = tempC;
                }
                //  volt=4395
                case "volt" -> {
                    Integer voltageMv = BatteryInfo.toInt(value);
                    if (voltageMv != null) reading.voltageMv = voltageMv;
                }
                //  status=charging|discharging|not-charging|full
                case "status" -> reading.status = value;
                //  plug=none|usb|ac|wireless
                case "plug" -> reading.plug = value;
                // NOTE: Samsung also dumps current=/ap_temp=/pa_temp=/skin_temp= here; the units
                // aren't documented so they're skipped rather than reported wrongly
                default -> {
                }
            }
        }

        BatteryInfo.Sample sample = new BatteryInfo.Sample(timeMs);
        sample.level = level;
        sample.tempC = reading.tempC;
        sample.voltageMv = reading.voltageMv;
        sample.isCharging = isCharging(reading);
        sampleList.add(sample);
    }

    /**
     * the "+plugged" flag is the state bit the framework itself tracks, so it wins when present;
     * status= and plug= can disagree with each other while a charger is negotiating
     */
    private static boolean isCharging(Reading reading) {
        if (reading.isPlugged != null) return reading.isPlugged;
        if (reading.plug != null) return !TextUtils.equals(reading.plug, PLUG_NONE);
        return TextUtils.equals(reading.status, STATUS_CHARGING);
    }

    /**
     * several entries can land on the same millisecond (a state change dumps more than one line); they
     * describe one instant, so keep only the last - it carries the most up to date values
     */
    private void dropDuplicateTimes() {
        for (int i = sampleList.size() - 2; i >= 0; i--) {
            if (sampleList.get(i).timeMs == sampleList.get(i + 1).timeMs) sampleList.remove(i);
        }
    }

    /**
     * "+1h59m03s002ms" or "0"
     *
     * @return offset in millis, or -1 if it can't be parsed
     */
    private static long parseOffset(String text) {
        if (TextUtils.isEmpty(text)) return -1;
        Matcher matcher = RELATIVE_OFFSET.matcher(text);
        if (!matcher.matches()) {
            // the very first entry prints a bare "0"
            return TextUtils.equals(text, "0") ? 0 : -1;
        }
        long offsetMs = 0;
        offsetMs += toLong(matcher.group(1)) * 24 * 60 * 60 * 1000L;
        offsetMs += toLong(matcher.group(2)) * 60 * 60 * 1000L;
        offsetMs += toLong(matcher.group(3)) * 60 * 1000L;
        offsetMs += toLong(matcher.group(4)) * 1000L;
        offsetMs += toLong(matcher.group(5));
        return offsetMs;
    }

    /**
     * "TIME: 2026-08-05-06-19-47-874" - the wall clock the surrounding offsets are relative to
     *
     * @return epoch millis, or null if this body isn't a time marker
     */
    private static Long parseMarkerTime(String body, ZoneId zone) {
        Matcher matcher = MARKER_TIME.matcher(body);
        if (!matcher.find()) return null;
        try {
            LocalDateTime dateTime = LocalDateTime.parse(matcher.group(1), MARKER_FORMAT);
            long timeMs = dateTime.atZone(zone).toInstant().toEpochMilli();
            return timeMs + toLong(matcher.group(2));
        } catch (DateTimeException e) {
            log.debug("parseMarkerTime: BAD_DATE: {}", matcher.group(1));
            return null;
        }
    }

    /**
     * absolute format entries have no year: "08-04 14:03:54.308"
     *
     * @return epoch millis or 0 if it can't be parsed
     */
    private static long parseAbsoluteTime(String text, ZoneId zone, long nowMs) {
        int year = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone).getYear();
        // if the entry looks like it's in the future it's from last year (Dec -> Jan rollover)
        for (int i = 0; i < 2; i++) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse((year - i) + "-" + text, ABSOLUTE_FORMAT);
                long timeMs = dateTime.atZone(zone).toInstant().toEpochMilli();
                if (timeMs <= nowMs + MAX_FUTURE_MS) return timeMs;
            } catch (DateTimeException e) {
                // Feb 29 on a non-leap year, etc
                log.trace("parseAbsoluteTime: BAD_DATE: {}, {}", text, e.getMessage());
            }
        }
        return 0;
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

    private static long toLong(String value) {
        Integer intValue = BatteryInfo.toInt(value);
        return intValue != null ? intValue : 0;
    }

    /**
     * @return battery readings, oldest first
     */
    public List<BatteryInfo.Sample> getSampleList() {
        return sampleList;
    }

    /**
     * charging periods as {startMs, endMs}, for drawing background bands behind the data
     */
    public List<long[]> getChargingSpanList() {
        List<long[]> spanList = new ArrayList<>();
        long startMs = -1;
        long lastMs = -1;
        for (BatteryInfo.Sample sample : sampleList) {
            if (Boolean.TRUE.equals(sample.isCharging)) {
                if (startMs < 0) startMs = sample.timeMs;
            } else if (startMs >= 0) {
                addSpan(spanList, startMs, sample.timeMs);
                startMs = -1;
            }
            lastMs = sample.timeMs;
        }
        // still charging at the end of the history
        if (startMs >= 0) addSpan(spanList, startMs, lastMs);
        return spanList;
    }

    private static void addSpan(List<long[]> spanList, long startMs, long endMs) {
        if (endMs <= startMs) return;
        if (!spanList.isEmpty()) {
            long[] lastSpan = spanList.get(spanList.size() - 1);
            if (startMs - lastSpan[1] <= MERGE_SPAN_MS) {
                lastSpan[1] = endMs;
                return;
            }
        }
        spanList.add(new long[]{startMs, endMs});
    }

    public boolean isEmpty() {
        return sampleList.isEmpty();
    }

    /**
     * @return true if the device reported a history format we don't parse
     */
    public boolean isUnsupportedFormat() {
        return format != 0 && format != FORMAT_ABSOLUTE && format != FORMAT_RELATIVE;
    }

    /**
     * @return time covered by the history as {startMs, endMs}, or null if empty
     */
    public long[] getTimeRange() {
        if (sampleList.isEmpty()) return null;
        return new long[]{sampleList.get(0).timeMs, sampleList.get(sampleList.size() - 1).timeMs};
    }

    @Override
    public String toString() {
        return "format:" + format + ", samples:" + sampleList.size()
            + ", charging:" + getChargingSpanList().size();
    }
}
