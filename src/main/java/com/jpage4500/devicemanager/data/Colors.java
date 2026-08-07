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
    // hairline between sections of a panel, or between rows of a list
    public static final Color COLOR_DIVIDER = new Color(222, 226, 232);
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

    // -- device stats chart (1 line per device) --
    // 8 hues validated as a set against a white plot background (colorblind-safe in any adjacent pair)
    // NOTE: a fixed order, not a wheel - past 8 the LINE PATTERN changes (ChartUtils.getSeriesStroke)
    public static final Color[] COLOR_CHART_SERIES = {
        new Color(0x2a78d6),    // blue
        new Color(0xeb6834),    // orange
        new Color(0x1baf7a),    // aqua
        new Color(0xeda100),    // yellow
        new Color(0xe87ba4),    // magenta
        new Color(0x008300),    // green
        new Color(0x4a3aa7),    // violet
        new Color(0xe34948),    // red
    };

    // -- device info cards --
    // NOTE: the app runs FlatLightLaf, so these are fixed light-theme colors. the card background is
    // hardcoded, so the text on it has to be too - a LAF-supplied foreground would go white on white
    // if a dark theme is ever added
    // cards sit on a slightly darker page so their rounded edges read without a heavy border
    public static final Color COLOR_CARD_PAGE = new Color(233, 236, 241);
    public static final Color COLOR_CARD_BACKGROUND = new Color(241, 244, 249);
    public static final Color COLOR_CARD_BORDER = new Color(225, 230, 237);
    // a clickable card under the mouse
    public static final Color COLOR_CARD_HOVER = new Color(227, 233, 243);
    // hairline between rows of the same card; lighter than the border around it
    public static final Color COLOR_CARD_SEPARATOR = new Color(228, 232, 238);
    // property name, and any other primary text on a card
    public static final Color COLOR_CARD_LABEL = new Color(58, 63, 70);
    // property values, gauge bars and card icons all share one accent so the screen reads as a set
    public static final Color COLOR_CARD_ACCENT = new Color(50, 102, 128);
    // supporting values under a gauge ("Free: 108.0 GB, Total: 221.8 GB")
    public static final Color COLOR_CARD_DETAIL = new Color(90, 100, 112);
    // unfilled part of a gauge bar
    public static final Color COLOR_GAUGE_TRACK = new Color(214, 229, 242);
}
