package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.ChartStat;
import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceStat;
import com.jpage4500.devicemanager.data.StatSample;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.manager.DeviceStatsManager;
import com.jpage4500.devicemanager.ui.dialog.SettingsDialog;
import com.jpage4500.devicemanager.ui.views.ChartLegendRenderer;
import com.jpage4500.devicemanager.ui.views.ChartUtils;
import com.jpage4500.devicemanager.ui.views.CheckBoxList;
import com.jpage4500.devicemanager.ui.views.StatsChartPanel;
import com.jpage4500.devicemanager.ui.views.StatsPieChartPanel;
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

    private final JComboBox<ChartStat> statComboBox;
    private final ChartLegendRenderer legendRenderer;
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

        statComboBox = new JComboBox<>(buildStatList().toArray(new ChartStat[0]));
        statComboBox.setSelectedItem(restoreSelectedStat(statComboBox));
        statComboBox.addActionListener(actionEvent -> {
            PreferenceUtils.setPreference(PreferenceUtils.Pref.PREF_STATS_SELECTED_STAT, getSelectedStat().getPrefKey());
            updateChart();
        });

        legendRenderer = new ChartLegendRenderer(seriesIconList);
        deviceCheckBoxList = new CheckBoxList();
        deviceCheckBoxList.setCellRenderer(legendRenderer);
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
        ChartStat chartStat = getSelectedStat();
        // a pie's colors key to the VALUES, not to devices, so the device swatches would be telling a
        // lie about what the colors mean
        legendRenderer.setShowSwatch(chartStat instanceof StatSample.StatType);
        deviceCheckBoxList.repaint();

        if (chartStat instanceof DeviceStat deviceStat) {
            showDeviceStat(deviceStat, checkedSerialList);
        } else if (chartStat instanceof StatSample.StatType statType) {
            showHistoryStat(statType, checkedSerialList);
        }

        chartHolder.revalidate();
        chartHolder.repaint();
    }

    /**
     * recorded history for the checked devices, over time
     */
    private void showHistoryStat(StatSample.StatType statType, List<String> checkedSerialList) {
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
        updateHistorySummary(checkedSerialList);
    }

    /**
     * how the connected devices split across one of their properties, right now
     * <p>
     * offline devices are left out even when they're checked - they're in the list for their history,
     * and their last known OS/model isn't part of "what's connected"
     */
    private void showDeviceStat(DeviceStat deviceStat, List<String> checkedSerialList) {
        Set<String> checkedSet = new HashSet<>(checkedSerialList);
        List<Device> deviceList = new ArrayList<>();
        for (Device device : DeviceManager.getInstance().getDevices()) {
            if (device.isOnline && checkedSet.contains(device.serial)) deviceList.add(device);
        }

        if (deviceList.isEmpty()) {
            chartHolder.add(centeredLabel("No connected devices selected"), BorderLayout.CENTER);
            summaryLabel.setText("");
            return;
        }

        StatsPieChartPanel piePanel = new StatsPieChartPanel(deviceList, deviceStat);
        chartHolder.add(piePanel, BorderLayout.CENTER);

        StringBuilder sb = new StringBuilder();
        sb.append(deviceList.size()).append(" devices, ");
        // NOTE: the label is used as-is - lower-casing turns a custom column like "PM" into "pm"
        sb.append(piePanel.getCategoryCount()).append(' ').append(deviceStat.getLabel());
        sb.append(piePanel.getCategoryCount() == 1 ? " value" : " values");
        // the pie only has 8 colors, so say when the tail was rolled up rather than quietly dropping it
        if (piePanel.getFoldedCount() > 0) {
            sb.append(" (smallest ").append(piePanel.getFoldedCount()).append(" grouped as Other)");
        }
        summaryLabel.setText(sb.toString());
    }

    private void updateHistorySummary(List<String> checkedSerialList) {
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

    /**
     * the recorded-over-time stats first, then the properties of whatever is connected right now
     * <p>
     * the custom columns are whatever the user configured, so this list isn't fixed
     */
    private static List<ChartStat> buildStatList() {
        List<ChartStat> statList = new ArrayList<>(List.of(StatSample.StatType.values()));
        statList.addAll(DeviceStat.getList(SettingsDialog.getCustomColumnLabels()));
        return statList;
    }

    private ChartStat getSelectedStat() {
        Object selected = statComboBox.getSelectedItem();
        return selected instanceof ChartStat chartStat ? chartStat : StatSample.StatType.BATTERY_LEVEL;
    }

    /**
     * NOTE: matched against what's actually in the box - a custom column that has since been removed
     * falls back to the first stat rather than selecting nothing
     */
    private static ChartStat restoreSelectedStat(JComboBox<ChartStat> comboBox) {
        String prefKey = PreferenceUtils.getPreference(PreferenceUtils.Pref.PREF_STATS_SELECTED_STAT);
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            ChartStat chartStat = comboBox.getItemAt(i);
            if (TextUtils.equals(chartStat.getPrefKey(), prefKey)) return chartStat;
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
