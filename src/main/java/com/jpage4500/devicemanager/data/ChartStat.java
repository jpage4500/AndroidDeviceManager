package com.jpage4500.devicemanager.data;

/**
 * something the stats screen can graph
 * <p>
 * implemented by both kinds of stat it offers: {@link StatSample.StatType} plots a recorded value over
 * time, {@link DeviceStat} counts up a property of the devices connected right now. they share the 1
 * drop-down, so they need a common label and a stable key to remember the selection by.
 */
public interface ChartStat {

    String getLabel();

    /**
     * @return key this stat is saved under; stable across releases and unique across both kinds
     */
    String getPrefKey();

    /**
     * @return icon shown beside this stat in the list; currently says which shape of chart it draws
     */
    Icons getIcon();
}
