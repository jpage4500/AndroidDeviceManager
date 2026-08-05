package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.BatteryHistory;
import com.jpage4500.devicemanager.data.BatteryInfo;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.manager.DeviceManager;
import com.jpage4500.devicemanager.ui.views.BatteryChartPanel;
import com.jpage4500.devicemanager.utils.DialogHelper;

import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * battery level/temperature history for a single device
 * <p>
 * one window per device (see {@link AppController}) so several can be opened side by side and
 * compared. reading the history costs the device several seconds, so the window opens showing progress
 * and swaps in the chart when the read lands.
 */
public class BatteryScreen extends BaseScreen {
    private static final Logger log = LoggerFactory.getLogger(BatteryScreen.class);

    private final JPanel contentPanel;
    private JMenuItem listMenuItem;
    private BatteryHistory history;

    public BatteryScreen(App app, Device device) {
        super(app, device, "battery-" + device.serial, 1000, 600);
        contentPanel = new JPanel(new BorderLayout());

        setupMenuBar();
        setContentPane(contentPanel);
        bindEscapeToClose();
        updateDevice(device);
        refresh();
    }

    public void updateDevice(Device device) {
        this.device = device;
        setTitle(buildTitle());
    }

    /**
     * title carries the time span once it's known - the history's extent is the first thing you want to
     * know about a chart like this
     */
    private String buildTitle() {
        String name = device.isOnline ? device.getDisplayName() : "OFFLINE [" + device.getDisplayName() + "]";
        if (history == null || history.isEmpty()) return "Battery: " + name;
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm");
        long[] timeRange = history.getTimeRange();
        return "Battery: " + name + "  (" + dateFormat.format(new Date(timeRange[0]))
            + " - " + dateFormat.format(new Date(timeRange[1])) + ")";
    }

    private void setupMenuBar() {
        JMenu windowMenu = buildWindowMenu();

        JMenu batteryMenu = new JMenu("Battery");
        createCmdMenuItem(batteryMenu, "Refresh", KeyEvent.VK_R, e -> refresh());
        listMenuItem = createCmdMenuItem(batteryMenu, "View as List", KeyEvent.VK_L, e -> showHistoryList());
        listMenuItem.setEnabled(false);

        JMenuBar menubar = new JMenuBar();
        menubar.add(windowMenu);
        menubar.add(batteryMenu);
        setJMenuBar(menubar);
    }

    /**
     * (re)read the history off the device and rebuild the chart
     */
    private void refresh() {
        showProgress("Reading battery history from " + device.getDisplayName());
        app.setDeviceBusy(device, true);
        DeviceManager.getInstance().fetchBatteryHistory(device, result -> SwingUtilities.invokeLater(() -> {
            app.setDeviceBusy(device, false);
            history = result;
            contentPanel.removeAll();
            if (result == null) {
                contentPanel.add(centeredLabel("Unable to read battery history from this device"), BorderLayout.CENTER);
            } else if (result.isEmpty()) {
                contentPanel.add(centeredLabel(result.isUnsupportedFormat()
                    ? "This device reports a battery history format which isn't supported"
                    : "No battery history available on this device"), BorderLayout.CENTER);
            } else {
                contentPanel.add(new BatteryChartPanel(result), BorderLayout.CENTER);
            }
            listMenuItem.setEnabled(result != null && !result.isEmpty());
            setTitle(buildTitle());
            contentPanel.revalidate();
            contentPanel.repaint();
        }));
    }

    private void showProgress(String message) {
        contentPanel.removeAll();
        // NOTE: no "fill" here - it stretches the rows apart instead of keeping the label and bar
        // together in the middle of the window
        JPanel panel = new JPanel(new MigLayout("wrap 1, align center center"));
        panel.add(new JLabel(message), "align center");
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        panel.add(progressBar, "width 320!, align center");
        contentPanel.add(panel, BorderLayout.CENTER);
        listMenuItem.setEnabled(false);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private static JLabel centeredLabel(String message) {
        return new JLabel(message, SwingConstants.CENTER);
    }

    /**
     * the same samples the chart plots, read as a list (newest first) - so no reading is only reachable
     * by hovering the chart
     */
    private void showHistoryList() {
        if (history == null || history.isEmpty()) return;
        Map<String, String> displayMap = new LinkedHashMap<>();
        // millis are shown because a device can log more than one entry within the same second;
        // each row is then exactly 1 reading instead of several concatenated onto a line
        SimpleDateFormat sampleFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        List<BatteryInfo.Sample> sampleList = history.getSampleList();
        long prevSec = -1;
        String prevValue = null;
        // newest first
        for (int i = sampleList.size() - 1; i >= 0; i--) {
            BatteryInfo.Sample sample = sampleList.get(i);
            String value = sample.getDisplay();
            long sec = sample.timeMs / 1000;
            // a device logs several entries for one state change; where they land in the same second
            // AND read the same there's nothing to tell apart, so keep a single row
            if (sec == prevSec && value.equals(prevValue)) continue;
            displayMap.put(sampleFormat.format(new Date(sample.timeMs)), value);
            prevSec = sec;
            prevValue = value;
        }
        DialogHelper.showListDialog(this, buildTitle(), displayMap, null);
    }

    @Override
    protected void onWindowStateChanged(WindowState state) {
        super.onWindowStateChanged(state);
        if (state == WindowState.CLOSING) {
            closeWindow();
        }
    }

    @Override
    public void closeWindow() {
        log.trace("closeWindow: {}", device.getDisplayName());
        saveFrameSize();
        app.onBatteryClosed(device.serial);
        dispose();
    }
}
