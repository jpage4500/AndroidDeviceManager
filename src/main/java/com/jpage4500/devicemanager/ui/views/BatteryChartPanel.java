package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.BatteryHistory;
import com.jpage4500.devicemanager.data.BatteryInfo;
import com.jpage4500.devicemanager.data.Colors;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.Range;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
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
 */
public class BatteryChartPanel extends JPanel {
    // level gets more vertical room than temperature
    private static final int WEIGHT_LEVEL = 3;
    private static final int WEIGHT_TEMP = 2;

    private static final BasicStroke STROKE_LINE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke STROKE_HAIRLINE = new BasicStroke(1f);
    private static final Shape SHAPE_POINT = new Ellipse2D.Double(-4d, -4d, 8d, 8d);

    // history entries are written when something changes, not on a timer, so an idle device can leave
    // hours between samples; split the line there instead of drawing a straight edge across the gap
    private static final long MAX_GAP_MS = 15 * 60 * 1000L;

    private static final Font FONT_LABEL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    private static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 12);

    // ChartPanel otherwise renders into a 1024x768 image and scales it up, which looks blurry on a
    // retina display. cap it generously rather than leaving it unbounded, so a large window can't turn
    // every repaint into a multi-megapixel render
    private static final int MAX_DRAW_SIZE = 4096;
    // tick label resolution has to follow the zoom level, not the size of the whole history: past a day
    // an "HH:mm" tick can't say which day a reading is from, and below ~20 minutes the ticks fall closer
    // together than a minute, so the same "09:12" gets printed a dozen times across the axis
    private static final long DAY_MS = 24 * 60 * 60 * 1000L;
    private static final long LABEL_SECONDS_BELOW_MS = 20 * 60 * 1000L;

    // -- wheel zoom --
    // zoom speed: how much the visible time range grows/shrinks per wheel notch. a trackpad swipe
    // delivers many notches at once, so this is deliberately gentle - raise it to zoom faster
    private static final double ZOOM_STEP = 1.10;
    // tightest view allowed. this is a readability floor as much as a safety one - devices log every
    // few seconds at best, so a narrower window is mostly empty axis. it also keeps the range away from
    // zero width, where JFreeChart throws ("A positive range length is required") on every later zoom
    // and the view gets stuck
    private static final long MIN_ZOOM_SPAN_MS = 5 * 60 * 1000L;

    private final JFreeChart chart;
    // fully zoomed out span, and the tightest zoom allowed
    private final long fullSpanMs;
    private final long minSpanMs;
    // current tick label pattern; tracked so it's only reset when it actually changes
    private String datePattern;

    public BatteryChartPanel(BatteryHistory history) {
        super(new BorderLayout());
        List<BatteryInfo.Sample> sampleList = history.getSampleList();

        DateAxis timeAxis = new DateAxis();
        timeAxis.setLowerMargin(0.01);
        timeAxis.setUpperMargin(0.01);
        styleAxis(timeAxis);

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

        // read the auto range only once the subplots are attached, so it reflects the real data extent
        fullSpanMs = (long) timeAxis.getRange().getLength();
        minSpanMs = Math.min(MIN_ZOOM_SPAN_MS, Math.max(1L, fullSpanMs));

        // re-pick the label format whenever the visible range changes (zoom, pan, auto range reset)
        updateDateFormat(timeAxis);
        timeAxis.addChangeListener(axisChangeEvent -> updateDateFormat(timeAxis));

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setDisplayToolTips(true);
        chartPanel.setMaximumDrawWidth(MAX_DRAW_SIZE);
        chartPanel.setMaximumDrawHeight(MAX_DRAW_SIZE);
        chartPanel.setBackground(Color.WHITE);
        // NOTE: JFreeChart's own wheel zoom is left off on purpose - it halves the range per notch with
        // no lower bound, so a trackpad swipe collapses the axis and wedges the view. this handler zooms
        // the time axis only (level is a fixed 0-100 and temp auto-fits), at a gentler step and clamped
        // at both ends so it can't reach a degenerate range
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.addMouseWheelListener(mouseWheelEvent ->
            handleWheelZoom(chartPanel, combinedPlot, mouseWheelEvent));
        // the built-in popup menu stays (it holds "Auto Range"); double click resets without the menu
        chartPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) chartPanel.restoreAutoBounds();
            }
        });
        add(chartPanel, BorderLayout.CENTER);
    }

    /**
     * zoom the shared time axis around the pointer, clamped so the range can never collapse (which
     * would throw and leave the chart unrecoverable) nor drift out past the data
     */
    private void handleWheelZoom(ChartPanel chartPanel, CombinedDomainXYPlot plot, MouseWheelEvent mouseWheelEvent) {
        ValueAxis timeAxis = plot.getDomainAxis();
        Range range = timeAxis.getRange();
        double spanMs = range.getLength();
        // scrolling up gives a negative rotation, so this shrinks the range = zoom in. precise rotation
        // keeps trackpad momentum smooth instead of quantizing to whole notches
        double newSpanMs = spanMs * Math.pow(ZOOM_STEP, mouseWheelEvent.getPreciseWheelRotation());

        if (newSpanMs >= fullSpanMs) {
            // fully zoomed out - snap back to the data instead of panning off into empty space
            chartPanel.restoreAutoBounds();
            return;
        }
        newSpanMs = Math.max(newSpanMs, minSpanMs);
        if (newSpanMs == spanMs) return;

        // hold whatever time sits under the pointer still while the range grows/shrinks around it
        double anchorMs = range.getCentralValue();
        Rectangle2D dataArea = chartPanel.getChartRenderingInfo().getPlotInfo().getDataArea();
        if (dataArea != null && dataArea.getWidth() > 0) {
            Point2D point = chartPanel.translateScreenToJava2D(mouseWheelEvent.getPoint());
            anchorMs = timeAxis.java2DToValue(point.getX(), dataArea, plot.getDomainAxisEdge());
        }
        double anchorFraction = (anchorMs - range.getLowerBound()) / spanMs;
        // an anchor outside the plot (over an axis or the margin) would throw the range off; centre it
        if (anchorFraction < 0d || anchorFraction > 1d) anchorFraction = 0.5d;
        double lowerMs = anchorMs - anchorFraction * newSpanMs;
        timeAxis.setRange(new Range(lowerMs, lowerMs + newSpanMs));
    }

    /**
     * the underlying chart (can be rendered to an image without a visible window)
     */
    public JFreeChart getChart() {
        return chart;
    }

    /**
     * match the tick label resolution to how far in the user has zoomed, so neighbouring ticks always
     * read differently
     * <p>
     * NOTE: comparing the pattern first isn't just an optimisation - setting the override fires another
     * axis change event straight back at this method, and the equal-pattern check is what ends it
     */
    private void updateDateFormat(DateAxis timeAxis) {
        double spanMs = timeAxis.getRange().getLength();
        String pattern;
        if (spanMs > DAY_MS) pattern = "MM-dd HH:mm";
        else if (spanMs > LABEL_SECONDS_BELOW_MS) pattern = "HH:mm";
        else pattern = "HH:mm:ss";
        if (pattern.equals(datePattern)) return;
        datePattern = pattern;
        timeAxis.setDateFormatOverride(new SimpleDateFormat(pattern));
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
        styleAxis(levelAxis);

        XYAreaRenderer areaRenderer = new XYAreaRenderer(XYAreaRenderer.AREA);
        areaRenderer.setOutline(false);
        areaRenderer.setAutoPopulateSeriesPaint(false);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            areaRenderer.setSeriesPaint(i, Colors.COLOR_CHART_LEVEL_FILL);
        }

        XYLineAndShapeRenderer lineRenderer = new XYLineAndShapeRenderer(true, false);
        styleLineRenderer(lineRenderer, dataset, Colors.COLOR_CHART_LEVEL);
        lineRenderer.setDefaultToolTipGenerator(createTooltipGenerator("0'%'"));

        XYPlot plot = new XYPlot(dataset, null, levelAxis, areaRenderer);
        plot.setDataset(1, dataset);
        plot.setRenderer(1, lineRenderer);
        plot.mapDatasetToRangeAxis(1, 0);
        // draw dataset 0 (the wash) first so the line sits on top of it
        plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        stylePlot(plot);
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
        styleAxis(tempAxis);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        styleLineRenderer(renderer, dataset, Colors.COLOR_CHART_TEMP);
        renderer.setDefaultToolTipGenerator(createTooltipGenerator("0.0'°F'"));

        XYPlot plot = new XYPlot(dataset, null, tempAxis, renderer);
        stylePlot(plot);
        return plot;
    }

    /**
     * split samples into 1 series per contiguous run of readings
     * <p>
     * a run ends wherever the next sample is too far away to draw a line to. keeping runs as separate
     * series (rather than 1 series with nulls at the gaps) means an area fill stays a closed shape over
     * the time it covers, and lets a run holding a single sample be drawn as a dot
     */
    private static TimeSeriesCollection buildSegments(List<BatteryInfo.Sample> sampleList,
                                                      Function<BatteryInfo.Sample, Number> valueFunc) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries series = null;
        long prevMs = 0;
        for (BatteryInfo.Sample sample : sampleList) {
            Number value = valueFunc.apply(sample);
            if (value == null) continue;
            if (series == null || sample.timeMs - prevMs > MAX_GAP_MS) {
                series = new TimeSeries("run" + dataset.getSeriesCount());
                dataset.addSeries(series);
            }
            series.addOrUpdate(new FixedMillisecond(sample.timeMs), value);
            prevMs = sample.timeMs;
        }
        return dataset;
    }

    /**
     * every run is the same measurement so they all wear one color; a run holding a single sample also
     * gets a marker, since a 1 point line has nothing to connect and would draw nothing at all
     */
    private static void styleLineRenderer(XYLineAndShapeRenderer renderer, TimeSeriesCollection dataset, Color color) {
        renderer.setAutoPopulateSeriesPaint(false);
        renderer.setAutoPopulateSeriesShape(false);
        renderer.setDefaultShape(SHAPE_POINT);
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            renderer.setSeriesPaint(i, color);
            renderer.setSeriesStroke(i, STROKE_LINE);
            renderer.setSeriesShapesVisible(i, dataset.getSeries(i).getItemCount() == 1);
            renderer.setSeriesShapesFilled(i, true);
        }
    }

    /**
     * tooltips supplement the axes; every reading is also in the list view so nothing is hover-only
     */
    private static StandardXYToolTipGenerator createTooltipGenerator(String valueFormat) {
        return new StandardXYToolTipGenerator("{2} @ {1}",
            new SimpleDateFormat("MM-dd HH:mm:ss"), new DecimalFormat(valueFormat));
    }

    private static IntervalMarker createChargingMarker(long[] span) {
        IntervalMarker marker = new IntervalMarker(span[0], span[1], Colors.COLOR_CHART_CHARGING);
        // background context rather than a mark - no border around it
        marker.setOutlinePaint(null);
        return marker;
    }

    private static void stylePlot(XYPlot plot) {
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        // solid hairlines: a dashed grid reads as a threshold when it's only a grid
        plot.setDomainGridlinePaint(Colors.COLOR_CHART_GRID);
        plot.setDomainGridlineStroke(STROKE_HAIRLINE);
        plot.setRangeGridlinePaint(Colors.COLOR_CHART_GRID);
        plot.setRangeGridlineStroke(STROKE_HAIRLINE);
        plot.setAxisOffset(new RectangleInsets(0, 0, 0, 0));
        plot.setDomainCrosshairVisible(true);
        plot.setDomainCrosshairPaint(Colors.COLOR_CHART_AXIS);
        plot.setDomainCrosshairStroke(STROKE_HAIRLINE);
    }

    private static void styleAxis(ValueAxis axis) {
        axis.setLabelFont(FONT_TITLE);
        axis.setLabelPaint(Colors.COLOR_CHART_LABEL);
        axis.setTickLabelFont(FONT_LABEL);
        axis.setTickLabelPaint(Colors.COLOR_CHART_LABEL);
        axis.setAxisLinePaint(Colors.COLOR_CHART_AXIS);
        axis.setAxisLineStroke(STROKE_HAIRLINE);
        axis.setTickMarkPaint(Colors.COLOR_CHART_AXIS);
    }
}
