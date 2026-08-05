package com.jpage4500.devicemanager.data;

import java.awt.*;

public class Colors {
    // device icon colors
    public static final Color COLOR_ONLINE = new Color(24, 134, 0);
    public static final Color COLOR_OFFLINE = new Color(128, 128, 128);
    public static final Color COLOR_BUSY = new Color(251, 109, 8);
    public static final Color COLOR_NOT_READY = new Color(251, 247, 8);

    public static final Color COLOR_BACKGROUND = new Color(222, 222, 222);
    public static final Color COLOR_ALTERNATE_ROW = new Color(246, 246, 246);

    public static final Color COLOR_TABLE_HEADER = new Color(197, 197, 197);

    public static final Color COLOR_LIGHT_GRAY = new Color(232, 232, 232);
    public static final Color COLOR_LIST_SELECTED_NO_FOCUS = new Color(0, 81, 255, 108);

    public static final Color COLOR_SUCCESS = new Color(24, 134, 0);
    public static final Color COLOR_ERROR = new Color(255, 0, 0);

    public static final Color COLOR_TOOLBAR_HOVER = new Color(8, 48, 251);

    public static final Color COLOR_SERVER_RUNNING = new Color(24, 134, 0);

    // log recording state
    public static final Color COLOR_START_RECORDING = COLOR_ONLINE;
    public static final Color COLOR_STOP_RECORDING = COLOR_BUSY;

    // -- battery history chart --
    // level and temperature are plotted on separate stacked charts (never a shared y-axis) so
    // these 2 only ever need to be told apart from each other, not from a wider series palette
    public static final Color COLOR_CHART_LEVEL = new Color(42, 120, 214);
    // same hue as the line at ~10% alpha; a wash under the level line, never a solid block
    public static final Color COLOR_CHART_LEVEL_FILL = new Color(42, 120, 214, 26);
    public static final Color COLOR_CHART_TEMP = new Color(235, 104, 52);
    // "charging" bands are drawn behind the data as background context, so they stay neutral -
    // a colored band here would read as a third data series
    public static final Color COLOR_CHART_CHARGING = new Color(225, 224, 217, 140);
    public static final Color COLOR_CHART_GRID = new Color(225, 224, 217);
    public static final Color COLOR_CHART_AXIS = new Color(195, 194, 183);
    public static final Color COLOR_CHART_LABEL = new Color(137, 135, 129);
    public static final Color COLOR_CHART_THRESHOLD = new Color(208, 59, 59);
}
