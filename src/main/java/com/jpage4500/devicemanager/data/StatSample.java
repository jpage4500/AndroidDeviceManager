package com.jpage4500.devicemanager.data;

/**
 * one device's battery/storage readings at a point in time
 */
public class StatSample {
    // bytes in a gigabyte; free space is stored in bytes and graphed in GB
    private static final double BYTES_PER_GB = 1024d * 1024d * 1024d;

    public String serial;

    // host clock (NOTE: BatteryInfo.Sample.timeMs is device-local instead)
    public long timeMs;

    public Integer level;       // battery level, 1-100
    public Float tempC;         // battery temperature, celsius (shown to the user in fahrenheit)
    public Long freeSpace;      // bytes
    public Long totalSpace;     // bytes

    /**
     * a value that can be graphed over time
     */
    public enum StatType {
        BATTERY_LEVEL("Battery Level", "Level %", "0'%'", true),
        BATTERY_TEMP("Battery Temp", "Temp °F", "0.0'°F'", false),
        FREE_SPACE("Free Space", "Free GB", "0.0' GB'", false),
        STORAGE_USED("Storage Used", "Used %", "0'%'", true),
        ;

        public final String label;
        public final String axisLabel;
        // DecimalFormat pattern used for tooltips
        public final String valueFormat;
        // true when the value is a 0-100 percent, which gets a fixed axis instead of an auto range
        public final boolean isPercent;

        StatType(String label, String axisLabel, String valueFormat, boolean isPercent) {
            this.label = label;
            this.axisLabel = axisLabel;
            this.valueFormat = valueFormat;
            this.isPercent = isPercent;
        }

        /**
         * @return this stat's value, or null if the device didn't report it
         */
        public Number getValue(StatSample sample) {
            if (sample == null) return null;
            return switch (this) {
                case BATTERY_LEVEL -> sample.level;
                case BATTERY_TEMP -> BatteryInfo.toFahrenheit(sample.tempC);
                case FREE_SPACE -> sample.freeSpace == null ? null : sample.freeSpace / BYTES_PER_GB;
                case STORAGE_USED -> Device.getStorageUsedPercent(sample.freeSpace, sample.totalSpace);
            };
        }

        /**
         * shown in the stat drop-down
         */
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * @return true if the device reported at least 1 value worth keeping
     */
    public boolean hasAnyValue() {
        return level != null || tempC != null || freeSpace != null || totalSpace != null;
    }

    @Override
    public String toString() {
        return serial + "@" + timeMs + ": level:" + level + ", temp:" + tempC + ", free:" + freeSpace;
    }
}
