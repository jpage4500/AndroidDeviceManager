package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * a property of a connected device that can be counted up across the whole fleet ("how many devices
 * are on each OS version")
 * <p>
 * unlike {@link StatSample.StatType} this reads live devices, not recorded history - there's nothing to
 * plot over time, so it's drawn as a pie.
 */
public class DeviceStat implements ChartStat {
    private static final String PREF_PREFIX = "DEVICE:";

    // devices that never reported the property still have to be counted, or the total stops matching
    // the number of devices
    public static final String UNKNOWN = "Unknown";

    private final String label;
    private final Function<Device, String> valueFunc;

    private DeviceStat(String label, Function<Device, String> valueFunc) {
        this.label = label;
        this.valueFunc = valueFunc;
    }

    /**
     * @param customColumnList labels of the user's custom columns (see SettingsDialog.getCustomColumnLabels)
     */
    public static List<DeviceStat> getList(List<String> customColumnList) {
        List<DeviceStat> statList = new ArrayList<>();
        statList.add(new DeviceStat("OS", device -> device.os));
        statList.add(new DeviceStat("Model", device -> device.model));
        statList.add(new DeviceStat("Carrier", device -> device.carrier));
        for (String columnLabel : customColumnList) {
            statList.add(new DeviceStat(columnLabel, device -> device.customAppVersionList == null
                ? null : device.customAppVersionList.get(columnLabel)));
        }
        return statList;
    }

    /**
     * @return the device's value for this stat, or {@link #UNKNOWN} if it didn't report one
     */
    public String getValue(Device device) {
        String value = device != null ? valueFunc.apply(device) : null;
        return TextUtils.isEmpty(value) ? UNKNOWN : value;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public String getPrefKey() {
        return PREF_PREFIX + label;
    }

    @Override
    public Icons getIcon() {
        return Icons.CHART_PIE;
    }

    /**
     * shown in the stat drop-down
     */
    @Override
    public String toString() {
        return label;
    }
}
