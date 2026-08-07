package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.DeviceStat;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.labels.StandardPieToolTipGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.general.DefaultPieDataset;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

/**
 * how the connected devices split across one of their properties - OS version, model, carrier
 * <p>
 * a snapshot, not a history: this reads the devices as they are right now, so there's no time axis and
 * nothing is read off disk.
 */
public class StatsPieChartPanel extends JPanel {
    // the palette is a fixed 8 and slices past that are too thin to read anyway, so the tail is folded
    // into 1 "Other" slice. NOTE: never silently - getFoldedCount() reports it and the screen says so
    private static final int MAX_SLICES = 8;
    private static final String OTHER = "Other";

    private static final int MAX_DRAW_SIZE = 4096;
    // white gap between slices, so neighbouring colors don't bleed into each other
    private static final BasicStroke STROKE_SLICE = new BasicStroke(2f);

    private final JFreeChart chart;
    // value -> number of devices, largest first
    private final Map<String, Integer> countMap;
    private final int numCategories;
    private final int numFolded;

    public StatsPieChartPanel(List<Device> deviceList, DeviceStat deviceStat) {
        super(new BorderLayout());

        Map<String, Integer> allCounts = countDevices(deviceList, deviceStat);
        numCategories = allCounts.size();
        countMap = foldTail(allCounts);
        // NOTE: derived from the counts, not from whether an "Other" key is present - a device could
        // genuinely report "Other" as its value
        numFolded = numCategories > MAX_SLICES ? numCategories - (MAX_SLICES - 1) : 0;

        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            dataset.setValue(entry.getKey(), entry.getValue());
        }

        PiePlot<String> plot = new PiePlot<>(dataset);
        stylePlot(plot);
        // NOTE: colors are assigned over the sorted keys, so the biggest slice is always series color 1
        // - the same value keeps its color as long as its rank does
        int index = 0;
        for (String key : countMap.keySet()) {
            plot.setSectionPaint(key, ChartUtils.getSeriesColor(index++));
        }

        chart = new JFreeChart(null, null, plot, true);
        chart.setBackgroundPaint(Color.WHITE);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setBackgroundPaint(Color.WHITE);
            legend.setBorder(0, 0, 0, 0);
            legend.setItemFont(ChartUtils.FONT_LABEL);
            legend.setItemPaint(Colors.COLOR_CHART_LABEL);
        }

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setDisplayToolTips(true);
        chartPanel.setMaximumDrawWidth(MAX_DRAW_SIZE);
        chartPanel.setMaximumDrawHeight(MAX_DRAW_SIZE);
        chartPanel.setBackground(Color.WHITE);
        add(chartPanel, BorderLayout.CENTER);
    }

    /**
     * the underlying chart (can be rendered to an image without a visible window)
     */
    public JFreeChart getChart() {
        return chart;
    }

    public boolean isEmpty() {
        return countMap.isEmpty();
    }

    /**
     * @return how many distinct values the devices reported, before any folding
     */
    public int getCategoryCount() {
        return numCategories;
    }

    /**
     * @return how many of those values were folded into "Other" (0 when everything is shown)
     */
    public int getFoldedCount() {
        return numFolded;
    }

    /**
     * value -> device count, largest first; ties broken by name so the order (and so the colors) don't
     * jump around between refreshes
     */
    private static Map<String, Integer> countDevices(List<Device> deviceList, DeviceStat deviceStat) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Device device : deviceList) {
            counts.merge(deviceStat.getValue(device), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> entryList = new ArrayList<>(counts.entrySet());
        entryList.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
            .thenComparing(Map.Entry.comparingByKey()));

        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entryList) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    /**
     * keep the largest slices and roll the rest up, so the chart never asks for a 9th color
     */
    private static Map<String, Integer> foldTail(Map<String, Integer> counts) {
        if (counts.size() <= MAX_SLICES) return counts;

        Map<String, Integer> folded = new LinkedHashMap<>();
        int otherCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (folded.size() < MAX_SLICES - 1) folded.put(entry.getKey(), entry.getValue());
            else otherCount += entry.getValue();
        }
        folded.put(OTHER, otherCount);
        return folded;
    }

    private static void stylePlot(PiePlot<String> plot) {
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        // JFreeChart still defaults to a drop shadow behind the pie
        plot.setShadowPaint(null);
        plot.setSectionOutlinesVisible(true);
        plot.setAutoPopulateSectionOutlinePaint(false);
        plot.setDefaultSectionOutlinePaint(Color.WHITE);
        plot.setDefaultSectionOutlineStroke(STROKE_SLICE);
        plot.setCircular(true);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} ({1})",
            NumberFormat.getIntegerInstance(), new DecimalFormat("0%")));
        plot.setToolTipGenerator(new StandardPieToolTipGenerator("{0}: {1} devices ({2})",
            NumberFormat.getIntegerInstance(), new DecimalFormat("0.0%")));
        plot.setLabelFont(ChartUtils.FONT_LABEL);
        plot.setLabelPaint(Colors.COLOR_CHART_LABEL);
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setLabelLinkPaint(Colors.COLOR_CHART_AXIS);
        plot.setInsets(new RectangleInsets(4, 4, 4, 4));
        // room around the circle for the labels that sit outside it
        plot.setInteriorGap(0.06);
        plot.setIgnoreZeroValues(true);
    }
}
