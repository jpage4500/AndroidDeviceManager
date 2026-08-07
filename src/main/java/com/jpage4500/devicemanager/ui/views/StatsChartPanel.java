package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.StatSample;
import com.jpage4500.devicemanager.manager.DeviceStatsManager;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;

import java.awt.BorderLayout;
import java.awt.Color;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.JPanel;

/**
 * one stat, every device, over time
 * <p>
 * 1 plot with 1 line per device (not stacked subplots like {@link BatteryChartPanel}) since every
 * line shares a scale. the chart's own legend is off - the device list beside it is the legend.
 */
public class StatsChartPanel extends JPanel {
    // much beyond 1 refresh interval is a real gap (app closed, device unplugged), not a line
    // NOTE: a heuristic - the history may have been recorded under a different refresh setting
    private static final double GAP_INTERVALS = 2.5;
    private static final long MIN_GAP_MS = TimeUnit.MINUTES.toMillis(15);

    private final JFreeChart chart;

    /**
     * @param sampleMap        serial -> that device's samples, oldest first
     * @param statType         which value to plot
     * @param serialList       EVERY known device, not just the drawn ones - a line's color/pattern comes
     *                         from its index here, so hiding a device mustn't recolor the rest
     * @param visibleSerialSet the devices to actually draw
     * @param displayNameMap   serial -> name to show in tooltips
     */
    public StatsChartPanel(Map<String, List<StatSample>> sampleMap, StatSample.StatType statType,
                           List<String> serialList, Set<String> visibleSerialSet,
                           Map<String, String> displayNameMap) {
        super(new BorderLayout());

        long maxGapMs = Math.max(MIN_GAP_MS, (long) (DeviceStatsManager.getSampleIntervalMs() * GAP_INTERVALS));

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        ChartUtils.prepareLineRenderer(renderer);
        // series index -> device name (a device contributes 1 series per unbroken run)
        List<String> seriesNameList = new ArrayList<>();

        for (int i = 0; i < serialList.size(); i++) {
            String serial = serialList.get(i);
            // NOTE: skipped AFTER i is read, so the devices still drawn keep the color they had
            if (!visibleSerialSet.contains(serial)) continue;
            List<StatSample> sampleList = sampleMap.get(serial);
            if (sampleList == null || sampleList.isEmpty()) continue;

            String displayName = displayNameMap.getOrDefault(serial, serial);
            Color color = ChartUtils.getSeriesColor(i);
            // NOTE: the stroke pattern is what keeps device 9 apart from device 1, which share a color
            List<TimeSeries> segmentList = ChartUtils.buildSegments(sampleList,
                sample -> sample.timeMs, statType::getValue, serial, maxGapMs);

            for (TimeSeries series : segmentList) {
                int seriesIndex = dataset.getSeriesCount();
                dataset.addSeries(series);
                seriesNameList.add(displayName);
                ChartUtils.styleSeries(renderer, seriesIndex, color, ChartUtils.getSeriesStroke(i),
                    series.getItemCount() == 1);
            }
        }
        renderer.setDefaultToolTipGenerator(createTooltipGenerator(seriesNameList, statType.valueFormat));

        DateAxis timeAxis = new DateAxis();
        timeAxis.setLowerMargin(0.01);
        timeAxis.setUpperMargin(0.01);
        ChartUtils.styleAxis(timeAxis);

        XYPlot plot = new XYPlot(dataset, timeAxis, createValueAxis(statType), renderer);
        ChartUtils.stylePlot(plot);

        chart = new JFreeChart(null, null, plot, false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);

        ChartPanel chartPanel = ChartUtils.createChartPanel(chart, plot, timeAxis);
        add(chartPanel, BorderLayout.CENTER);
    }

    /**
     * the underlying chart (can be rendered to an image without a visible window)
     */
    public JFreeChart getChart() {
        return chart;
    }

    /**
     * true when nothing could be plotted - every selected device is missing this stat
     */
    public boolean isEmpty() {
        XYPlot plot = (XYPlot) chart.getPlot();
        XYDataset dataset = plot.getDataset();
        return dataset == null || dataset.getSeriesCount() == 0;
    }

    private static NumberAxis createValueAxis(StatSample.StatType statType) {
        NumberAxis axis = new NumberAxis(statType.axisLabel);
        if (statType.isPercent) {
            // a percent has a known full scale; auto-fitting would make a 3% spread look like a cliff
            axis.setRange(0d, 100d);
            axis.setTickUnit(new NumberTickUnit(25d));
        } else if (statType == StatSample.StatType.FREE_SPACE) {
            // 0 GB is a real zero (a full disk), so the axis should show how close to it a device is
            axis.setAutoRangeIncludesZero(true);
        } else {
            // temperature: 0F is not "no temperature"; including zero would flatten every reading
            axis.setAutoRangeIncludesZero(false);
        }
        ChartUtils.styleAxis(axis);
        return axis;
    }

    /**
     * "Pixel 8: 72% @ 08-07 14:03" - the name is the only way to tell overlapping lines apart
     */
    private static XYToolTipGenerator createTooltipGenerator(List<String> seriesNameList, String valueFormat) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm");
        DecimalFormat numberFormat = new DecimalFormat(valueFormat);
        return (dataset, series, item) -> {
            String name = series < seriesNameList.size() ? seriesNameList.get(series) : "";
            Number value = dataset.getY(series, item);
            if (value == null) return null;
            return name + ": " + numberFormat.format(value)
                + " @ " + dateFormat.format(new Date((long) dataset.getXValue(series, item)));
        };
    }
}
