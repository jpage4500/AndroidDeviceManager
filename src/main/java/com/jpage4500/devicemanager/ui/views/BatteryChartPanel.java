package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.BatteryHistory;
import com.jpage4500.devicemanager.data.BatteryInfo;
import com.jpage4500.devicemanager.data.Colors;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import java.util.function.Function;

import javax.swing.JPanel;

/**
 * battery level and temperature over time, from {@link BatteryHistory}
 * <p>
 * level (%) and temperature (F) are different scales, so each gets its own chart stacked on a shared
 * time axis rather than 2 y-axes on 1 plot - overlaying 2 unrelated scales lines them up arbitrarily
 * and invents a correlation that isn't in the data. charging periods are shaded behind both charts,
 * which is what makes the pair readable together ("temperature climbed while it was plugged in").
 * <p>
 * shared styling and zoom behaviour lives in {@link ChartUtils}.
 */
public class BatteryChartPanel extends JPanel {
    // level gets more vertical room than temperature
    private static final int WEIGHT_LEVEL = 3;
    private static final int WEIGHT_TEMP = 2;

    // history entries are written when something changes, not on a timer, so an idle device can leave
    // hours between samples; split the line there instead of drawing a straight edge across the gap
    private static final long MAX_GAP_MS = 15 * 60 * 1000L;

    private final JFreeChart chart;

    public BatteryChartPanel(BatteryHistory history) {
        super(new BorderLayout());
        List<BatteryInfo.Sample> sampleList = history.getSampleList();

        DateAxis timeAxis = new DateAxis();
        timeAxis.setLowerMargin(0.01);
        timeAxis.setUpperMargin(0.01);
        ChartUtils.styleAxis(timeAxis);

        XYPlot levelPlot = createLevelPlot(sampleList);
        XYPlot tempPlot = createTempPlot(sampleList);

        // NOTE: a CombinedDomainXYPlot doesn't own its subplots' domain markers, so the charging bands
        // go on each subplot (with their own marker instances) or they only show up on one of them
        for (long[] span : history.getChargingSpanList()) {
            levelPlot.addDomainMarker(createChargingMarker(span), Layer.BACKGROUND);
            tempPlot.addDomainMarker(createChargingMarker(span), Layer.BACKGROUND);
        }

        CombinedDomainXYPlot combinedPlot = new CombinedDomainXYPlot(timeAxis);
        combinedPlot.setGap(12d);
        combinedPlot.setBackgroundPaint(Color.WHITE);
        combinedPlot.setOutlineVisible(false);
        combinedPlot.add(levelPlot, WEIGHT_LEVEL);
        combinedPlot.add(tempPlot, WEIGHT_TEMP);

        chart = new JFreeChart(null, null, combinedPlot, false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);

        ChartPanel chartPanel = ChartUtils.createChartPanel(chart, combinedPlot, timeAxis);
        add(chartPanel, BorderLayout.CENTER);
    }

    /**
     * the underlying chart (can be rendered to an image without a visible window)
     */
    public JFreeChart getChart() {
        return chart;
    }

    /**
     * battery level: 0% is a real zero (an empty battery) so filling down to the baseline is honest.
     * the fill sits under a solid line, drawn as a 2nd dataset because an area renderer's own outline
     * traces the whole polygon (including the baseline) instead of just the top edge
     */
    private XYPlot createLevelPlot(List<BatteryInfo.Sample> sampleList) {
        TimeSeriesCollection dataset = buildSegments(sampleList, sample -> sample.level);

        NumberAxis levelAxis = new NumberAxis("Level %");
        levelAxis.setRange(0d, 100d);
        levelAxis.setTickUnit(new NumberTickUnit(25d));
        ChartUtils.styleAxis(levelAxis);

        XYAreaRenderer areaRenderer = new XYAreaRenderer(XYAreaRenderer.AREA);
        areaRenderer.setOutline(false);
        areaRenderer.setAutoPopulateSeriesPaint(false);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            areaRenderer.setSeriesPaint(i, Colors.COLOR_CHART_LEVEL_FILL);
        }

        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        styleLineRenderer(lineRenderer, dataset, Colors.COLOR_CHART_LEVEL);
        lineRenderer.setDefaultToolTipGenerator(ChartUtils.createTooltipGenerator("0'%'"));

        XYPlot plot = new XYPlot(dataset, null, levelAxis, areaRenderer);
        plot.setDataset(1, dataset);
        plot.setRenderer(1, lineRenderer);
        plot.mapDatasetToRangeAxis(1, 0);
        // draw dataset 0 (the wash) first so the line sits on top of it
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        ChartUtils.stylePlot(plot);
        return plot;
    }

    /**
     * temperature: 0C is not "no temperature", so this is a line only - an area fill down to a
     * baseline would overstate the magnitude of every reading
     */
    private XYPlot createTempPlot(List<BatteryInfo.Sample> sampleList) {
        TimeSeriesCollection dataset = buildSegments(sampleList, sample -> BatteryInfo.toFahrenheit(sample.tempC));

        // NOTE: the axis is left on auto range, deliberately. a fixed range here (eg. to make room for a
        // reference line above the data) is silently discarded by ChartPanel.restoreAutoBounds() on
        // zoom-out, so the chart would draw differently before and after the first zoom
        NumberAxis tempAxis = new NumberAxis("Temp °F");
        tempAxis.setAutoRangeIncludesZero(false);
        ChartUtils.styleAxis(tempAxis);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        styleLineRenderer(renderer, dataset, Colors.COLOR_CHART_TEMP);
        renderer.setDefaultToolTipGenerator(ChartUtils.createTooltipGenerator("0.0'°F'"));

        XYPlot plot = new XYPlot(dataset, null, tempAxis, renderer);
        ChartUtils.stylePlot(plot);
        return plot;
    }

    private static TimeSeriesCollection buildSegments(List<BatteryInfo.Sample> sampleList,
                                                      Function<BatteryInfo.Sample, Number> valueFunc) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        List<TimeSeries> seriesList = ChartUtils.buildSegments(sampleList, sample -> sample.timeMs,
            valueFunc, "run", MAX_GAP_MS);
        for (TimeSeries series : seriesList) {
            dataset.addSeries(series);
        }
        return dataset;
    }

    /**
     * every run is the same measurement so they all wear one color
     */
    private static void styleLineRenderer(XYLineAndShapeRenderer renderer, TimeSeriesCollection dataset, Color color) {
        ChartUtils.prepareLineRenderer(renderer);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            ChartUtils.styleSeries(renderer, i, color, dataset.getSeries(i).getItemCount() == 1);
        }
    }

    private static IntervalMarker createChargingMarker(long[] span) {
        IntervalMarker marker = new IntervalMarker(span[0], span[1], Colors.COLOR_CHART_CHARGING);
        // background context rather than a mark - no border around it
        marker.setOutlinePaint(null);
        return marker;
    }
}
