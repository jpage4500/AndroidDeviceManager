package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * current battery state parsed from "dumpsys battery"
 * <p>
 * this is the first section of that dump ("level: 72", "temperature: 251", ..) which every device
 * reports. history over time comes from "dumpsys batterystats" instead - see {@link BatteryHistory}.
 */
public class BatteryInfo {

    /**
     * a single point-in-time battery reading
     */
    public static class Sample {
        // NOTE: read from the device's own history buffer, so this is device-local time
        public long timeMs;
        public Integer level;       // 1-100
        public Float tempC;         // celsius; shown to the user in fahrenheit
        public Integer voltageMv;
        public Boolean isCharging;

        public Sample(long timeMs) {
            this.timeMs = timeMs;
        }

        /**
         * @return "72%, 77.2°F, 4136 mV, charging"
         */
        public String getDisplay() {
            List<String> partList = new ArrayList<>();
            if (level != null) partList.add(level + "%");
            String temp = formatTemp(tempC);
            if (temp != null) partList.add(temp);
            if (voltageMv != null) partList.add(voltageMv + " mV");
            if (Boolean.TRUE.equals(isCharging)) partList.add("charging");
            return TextUtils.join(partList, ", ");
        }
    }

    // -- most recent values --
    public Integer level;
    public Float tempC;
    public Integer voltageMv;
    public Integer currentUa;   // negative when discharging
    public Device.PowerStatus powerStatus = Device.PowerStatus.POWER_NONE;

    /**
     * parse the current state out of "dumpsys battery"
     * <p>
     * NOTE: Samsung devices also dump log buffers here whose lines are prefixed with a timestamp
     * ("08-04 14:03:54.308  Sending ACTION_BATTERY_CHANGED: level:82, .."). those can't be mistaken for
     * state lines because the timestamp ends up part of the name, which then matches nothing below.
     */
    public void update(List<String> lineList) {
        if (lineList == null || lineList.isEmpty()) return;
        Integer newLevel = null;
        Float newTempC = null;
        Integer newVoltageMv = null;
        Integer newCurrentUa = null;
        Device.PowerStatus status = Device.PowerStatus.POWER_NONE;

        for (String line : lineList) {
            String text = line.trim();
            if (text.isEmpty()) continue;
            // "name: value"
            int pos = text.indexOf(": ");
            if (pos <= 0) continue;
            String name = text.substring(0, pos).trim();
            String value = text.substring(pos + 2).trim();
            switch (name) {
                //  level: 72
                case "level" -> newLevel = toInt(value);
                //  temperature: 251
                case "temperature" -> newTempC = toTempC(value);
                //  voltage: 4136
                case "voltage" -> newVoltageMv = toInt(value);
                //  current now: -14843
                case "current now" -> newCurrentUa = toInt(value);
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
                default -> {
                }
            }
        }

        // some Android TV devices list battery level as 0; keep the last good value in that case
        if (isValidLevel(newLevel)) level = newLevel;
        tempC = newTempC;
        voltageMv = newVoltageMv;
        currentUa = newCurrentUa;
        powerStatus = status;
    }

    /**
     * @return battery temperature for display ("77.2°F") or null if unknown
     */
    public String getTempDisplay() {
        return formatTemp(tempC);
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

    /**
     * dumpsys reports celsius; temperature is shown to the user in fahrenheit
     */
    public static Float toFahrenheit(Float tempC) {
        if (tempC == null) return null;
        return tempC * 9f / 5f + 32f;
    }

    static String formatTemp(Float tempC) {
        Float tempF = toFahrenheit(tempC);
        if (tempF == null) return null;
        return String.format(Locale.US, "%.1f°", tempF);
    }

    static boolean isValidLevel(Integer level) {
        return level != null && level >= 1 && level <= 100;
    }

    /**
     * dumpsys reports temperature in 1/10th degrees celsius (251 = 25.1C)
     */
    static Float toTempC(String value) {
        Integer temp = toInt(value);
        // 0 means unknown; ignore anything outside of -50C to 100C
        if (temp == null || temp == 0 || temp < -500 || temp > 1000) return null;
        return temp / 10f;
    }

    static Integer toInt(String value) {
        if (TextUtils.isEmpty(value)) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "level:" + level + ", temp:" + getTempDisplay() + ", " + powerStatus;
    }
}
