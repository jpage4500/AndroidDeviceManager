package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.StatSample;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.DeviceStatsManager;
import com.jpage4500.devicemanager.ui.views.ChartLegendRenderer;
import com.jpage4500.devicemanager.ui.views.ChartUtils;
import com.jpage4500.devicemanager.ui.views.CheckBoxList;
import com.jpage4500.devicemanager.ui.views.StatsChartPanel;
import com.jpage4500.devicemanager.utils.DialogHelper;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;

import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JViewport;
import javax.swing.ListModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * one stat for every device, graphed over the last few days
 * <p>
 * app-wide (1 window) since the point of it is comparing devices; reads {@link DeviceStatsManager}
 * and never talks to a device.
 */
public class StatsScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(StatsScreen.class);

    // wide enough for a name with a phone number on it ("Galaxy S21 5G - 15408421504")
    private static final int DEFAULT_DEVICE_WIDTH = 270;
    // legend swatch: wide enough that a dash pattern is still recognizable
    private static final int SWATCH_WIDTH = 22;
    private static final int SWATCH_HEIGHT = 10;

    private final JComboBox<StatSample.StatType> statComboBox;
    private final CheckBoxList deviceCheckBoxList;
    private final JPanel chartHolder;
    private final JLabel summaryLabel;
    private final JSplitPane splitPane;

    // loaded history: serial -> samples, oldest first
    private Map<String, List<StatSample>> sampleMap = new HashMap<>();
    // every device with history; the index picks its color/pattern so this order has to stay stable
    private List<String> serialList = new ArrayList<>();
    private Map<String, String> displayNameMap = new HashMap<>();
    private final List<Icon> seriesIconList = new ArrayList<>();

    public StatsScreen(App app) {
        super(app, null, "stats", 1100, 700);

        statComboBox = new JComboBox<>(StatSample.StatType.values());
        statComboBox.setSelectedItem(restoreSelectedStat());
        statComboBox.addActionListener(actionEvent -> {
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_STATS_SELECTED_STAT, getSelectedStat().name());
            updateChart();
        });

        deviceCheckBoxList = new CheckBoxList();
        deviceCheckBoxList.setCellRenderer(new ChartLegendRenderer(seriesIconList));
        deviceCheckBoxList.setChangeListener(() -> {
            saveHiddenDevices();
            updateChart();
        });

        chartHolder = new JPanel(new BorderLayout());
        summaryLabel = new JLabel();
        summaryLabel.setForeground(Colors.COLOR_CHART_LABEL);

        JPanel topPanel = new JPanel(new MigLayout("fillx, insets 4 8 4 8", "[][]push[]"));
        topPanel.add(new JLabel("Stat:"));
        topPanel.add(statComboBox, "width 160!");
        topPanel.add(summaryLabel);

        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(new BottomAlignedPanel(deviceCheckBoxList)), chartHolder);
        // a wider window grows the chart, not the device list
        splitPane.setResizeWeight(0d);
        splitPane.setDividerLocation(PreferenceUtils.getPreference(
            PreferenceUtils.PrefInt.PREF_STATS_DEVICE_WIDTH, DEFAULT_DEVICE_WIDTH));

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(topPanel, BorderLayout.NORTH);
        contentPanel.add(splitPane, BorderLayout.CENTER);

        setupMenuBar();
        setContentPane(contentPanel);
        bindEscapeToClose();
        setTitle(buildTitle());
        refresh();
    }

    @Override
    protected String buildTitle() {
        return "Device Stats";
    }

    private void setupMenuBar() {
        JMenu windowMenu = buildWindowMenu();

        JMenu statsMenu = new JMenu("Stats");
        createCmdMenuItem(statsMenu, "Refresh", KeyEvent.VK_R, e -> refresh());
        createCmdMenuItem(statsMenu, "Select All Devices", KeyEvent.VK_A, e -> setAllChecked(true));
        createCmdMenuItem(statsMenu, "Select No Devices", KeyEvent.VK_D, e -> setAllChecked(false));
        statsMenu.addSeparator();
        createMenuItem(statsMenu, "Clear History", null, e -> clearHistory());

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(statsMenu);
        setJMenuBar(menubar);
    }

    /**
     * re-read history whenever the window is brought forward (samples are far too rare to poll for)
     */
    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        if (state == WindowState.ACTIVATED) refresh();
    }

    @Override
    protected void onClosing() {
        PreferenceUtils.setPreference(PreferenceUtils.PrefInt.PREF_STATS_DEVICE_WIDTH, splitPane.getDividerLocation());
    }

    /**
     * re-read the recorded history and rebuild both the device list and the chart
     */
    private void refresh() {
        sampleMap = DeviceStatsManager.getInstance().loadSamples();
        buildDeviceList();
        updateChart();
    }

    /**
     * the device list is the union of "has history" and "connected right now", sorted by name
     */
    private void buildDeviceList() {
        Map<String, Device> deviceMap = new LinkedHashMap<>();
        for (Device device : DeviceManager.getInstance().getDevices()) {
            if (TextUtils.notEmpty(device.serial)) deviceMap.put(device.serial, device);
        }

        Set<String> serialSet = new LinkedHashSet<>(deviceMap.keySet());
        serialSet.addAll(sampleMap.keySet());
        List<String> serials = new ArrayList<>(serialSet);

        Map<String, String> nameMap = buildDisplayNames(serials, deviceMap);
        // NOTE: this order also assigns the colors, so it has to be one that doesn't shuffle whenever
        // a device is checked or unchecked
        serials.sort(Comparator.comparing(nameMap::get, String.CASE_INSENSITIVE_ORDER));

        serialList = serials;
        displayNameMap = nameMap;

        Set<String> hiddenSet = new HashSet<>(GsonHelper.stringToList(
            PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_STATS_HIDDEN_DEVICES), String.class));

        seriesIconList.clear();
        deviceCheckBoxList.removeAll();
        for (int i = 0; i < serials.size(); i++) {
            String serial = serials.get(i);
            seriesIconList.add(ChartUtils.createSeriesIcon(i, SWATCH_WIDTH, SWATCH_HEIGHT));
            JCheckBox checkBox = new JCheckBox(displayNameMap.get(serial), !hiddenSet.contains(serial));
            checkBox.setToolTipText(serial);
            deviceCheckBoxList.addCheckbox(checkBox);
        }
    }

    /**
     * identical devices report the same model, so duplicate names get their serial appended
     */
    private static Map<String, String> buildDisplayNames(List<String> serials, Map<String, Device> deviceMap) {
        Map<String, Integer> nameCountMap = new HashMap<>();
        Map<String, String> nameMap = new HashMap<>();
        for (String serial : serials) {
            Device device = deviceMap.get(serial);
            String name = device != null ? device.getDisplayName() : null;
            if (TextUtils.isEmpty(name)) name = serial;
            nameMap.put(serial, name);
            nameCountMap.merge(name, 1, Integer::sum);
        }
        for (String serial : serials) {
            String name = nameMap.get(serial);
            if (nameCountMap.getOrDefault(name, 0) > 1 && !TextUtils.equals(name, serial)) {
                nameMap.put(serial, name + " (" + serial + ")");
            }
        }
        return nameMap;
    }

    private void updateChart() {
        chartHolder.removeAll();

        List<String> checkedSerialList = getCheckedSerials();
        StatSample.StatType statType = getSelectedStat();

        if (sampleMap.isEmpty()) {
            chartHolder.add(centeredLabel("No stats recorded yet - a sample is taken each time devices are refreshed"),
                BorderLayout.CENTER);
        } else if (checkedSerialList.isEmpty()) {
            chartHolder.add(centeredLabel("Select a device to graph"), BorderLayout.CENTER);
        } else {
            // NOTE: the full device list goes in alongside the checked ones so each device keeps the
            // color it had; the chart draws only what's checked
            StatsChartPanel chartPanel = new StatsChartPanel(sampleMap, statType, serialList,
                new HashSet<>(checkedSerialList), displayNameMap);
            if (chartPanel.isEmpty()) {
                chartHolder.add(centeredLabel("No " + statType.label.toLowerCase() + " readings for the selected devices"),
                    BorderLayout.CENTER);
            } else {
                chartHolder.add(chartPanel, BorderLayout.CENTER);
            }
        }

        updateSummary(checkedSerialList);
        chartHolder.revalidate();
        chartHolder.repaint();
    }

    private void updateSummary(List<String> checkedSerialList) {
        int numSamples = 0;
        long oldestMs = Long.MAX_VALUE;
        for (String serial : checkedSerialList) {
            List<StatSample> sampleList = sampleMap.get(serial);
            if (sampleList == null || sampleList.isEmpty()) continue;
            numSamples += sampleList.size();
            oldestMs = Math.min(oldestMs, sampleList.get(0).timeMs);
        }
        if (numSamples == 0) {
            summaryLabel.setText(DeviceStatsManager.getRetentionDays() + " day history");
            return;
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm");
        summaryLabel.setText(checkedSerialList.size() + " devices, " + numSamples + " samples since "
            + dateFormat.format(new Date(oldestMs)));
    }

    /**
     * NOTE: read by index, not by label - identical devices can share a label
     */
    private List<String> getCheckedSerials() {
        List<String> checkedList = new ArrayList<>();
        ListModel<?> model = deviceCheckBoxList.getModel();
        for (int i = 0; i < model.getSize() && i < serialList.size(); i++) {
            JCheckBox checkBox = (JCheckBox) model.getElementAt(i);
            if (checkBox.isSelected()) checkedList.add(serialList.get(i));
        }
        return checkedList;
    }

    private void setAllChecked(boolean isChecked) {
        ListModel<?> model = deviceCheckBoxList.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            ((JCheckBox) model.getElementAt(i)).setSelected(isChecked);
        }
        deviceCheckBoxList.repaint();
        saveHiddenDevices();
        updateChart();
    }

    private void saveHiddenDevices() {
        List<String> hiddenList = new ArrayList<>(serialList);
        hiddenList.removeAll(getCheckedSerials());
        PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_STATS_HIDDEN_DEVICES, GsonHelper.toJson(hiddenList));
    }

    private void clearHistory() {
        if (!DialogHelper.showConfirmDialog(this, "Clear History",
            "Delete all recorded device stats?")) {
            return;
        }
        DeviceStatsManager.getInstance().clearSamples();
        log.debug("clearHistory: stats cleared");
        refresh();
    }

    private StatSample.StatType getSelectedStat() {
        Object selected = statComboBox.getSelectedItem();
        return selected instanceof StatSample.StatType statType ? statType : StatSample.StatType.BATTERY_LEVEL;
    }

    private static StatSample.StatType restoreSelectedStat() {
        String name = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_STATS_SELECTED_STAT);
        for (StatSample.StatType statType : StatSample.StatType.values()) {
            if (TextUtils.equals(statType.name(), name)) return statType;
        }
        return StatSample.StatType.BATTERY_LEVEL;
    }

    private static JLabel centeredLabel(String message) {
        return new JLabel(message, SwingConstants.CENTER);
    }

    /**
     * holds the device list against the BOTTOM of the scroll pane instead of the top
     * <p>
     * BorderLayout.SOUTH only sinks its child if the panel is taller than that child, and a plain panel
     * in a scroll pane is only ever its own preferred height - so this reports that it tracks the
     * viewport height whenever the list is shorter than the space available. once the list outgrows the
     * viewport it stops tracking and scrolls normally.
     */
    private static class BottomAlignedPanel extends JPanel implements Scrollable {
        private static final int SCROLL_UNIT = 16;

        BottomAlignedPanel(Component content) {
            super(new BorderLayout());
            add(content, BorderLayout.SOUTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return SCROLL_UNIT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
        }
    }
}
