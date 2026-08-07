package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.Range;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.TimeSeries;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

import javax.swing.Icon;

/**
 * shared look and behaviour for the time-series charts ({@link BatteryChartPanel},
 * {@link StatsChartPanel})
 */
public class ChartUtils {

    public static final BasicStroke STROKE_LINE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    public static final BasicStroke STROKE_HAIRLINE = new BasicStroke(1f);
    public static final Shape SHAPE_POINT = new Ellipse2D.Double(-4d, -4d, 8d, 8d);

    public static final Font FONT_LABEL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
    public static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 12);

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
    // tightest view allowed. this is a readability floor as much as a safety one - devices are sampled
    // every few minutes at best, so a narrower window is mostly empty axis. it also keeps the range away
    // from zero width, where JFreeChart throws ("A positive range length is required") on every later
    // zoom and the view gets stuck
    private static final long MIN_ZOOM_SPAN_MS = 5 * 60 * 1000L;

    /**
     * wrap a chart in a ChartPanel with the shared rendering settings, wheel zoom and double-click reset
     *
     * @param domainPlot the plot that owns the shared time axis (a CombinedDomainXYPlot when the chart
     *                   is made of stacked subplots)
     * @param timeAxis   the same axis; passed separately so its tick format can follow the zoom level
     */
    public static ChartPanel createChartPanel(JFreeChart chart, XYPlot domainPlot, DateAxis timeAxis) {
        // read the auto range only once the subplots are attached, so it reflects the real data extent
        long fullSpanMs = (long) timeAxis.getRange().getLength();
        long minSpanMs = Math.min(MIN_ZOOM_SPAN_MS, Math.max(1L, fullSpanMs));

        // re-pick the label format whenever the visible range changes (zoom, pan, auto range reset)
        DateFormatUpdater formatUpdater = new DateFormatUpdater();
        formatUpdater.update(timeAxis);
        timeAxis.addChangeListener(axisChangeEvent -> formatUpdater.update(timeAxis));

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setDisplayToolTips(true);
        chartPanel.setMaximumDrawWidth(MAX_DRAW_SIZE);
        chartPanel.setMaximumDrawHeight(MAX_DRAW_SIZE);
        chartPanel.setBackground(Color.WHITE);
        // NOTE: JFreeChart's own wheel zoom is left off on purpose - it halves the range per notch with
        // no lower bound, so a trackpad swipe collapses the axis and wedges the view. this handler zooms
        // the time axis only (the range axes are fixed or auto-fit), at a gentler step and clamped at
        // both ends so it can't reach a degenerate range
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.addMouseWheelListener(mouseWheelEvent ->
            handleWheelZoom(chartPanel, domainPlot, mouseWheelEvent, fullSpanMs, minSpanMs));
        // the built-in popup menu stays (it holds "Auto Range"); double click resets without the menu
        chartPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                if (mouseEvent.getClickCount() == 2) chartPanel.restoreAutoBounds();
            }
        });
        return chartPanel;
    }

    /**
     * zoom the shared time axis around the pointer, clamped so the range can never collapse (which
     * would throw and leave the chart unrecoverable) nor drift out past the data
     */
    private static void handleWheelZoom(ChartPanel chartPanel, XYPlot plot, MouseWheelEvent mouseWheelEvent,
                                        long fullSpanMs, long minSpanMs) {
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
     * match the tick label resolution to how far in the user has zoomed, so neighbouring ticks always
     * read differently
     */
    private static class DateFormatUpdater {
        // current tick label pattern; tracked so it's only reset when it actually changes
        private String datePattern;

        /**
         * NOTE: comparing the pattern first isn't just an optimisation - setting the override fires
         * another axis change event straight back at this method, and the equal-pattern check is what
         * ends it
         */
        void update(DateAxis timeAxis) {
            double spanMs = timeAxis.getRange().getLength();
            String pattern;
            if (spanMs > DAY_MS) pattern = "MM-dd HH:mm";
            else if (spanMs > LABEL_SECONDS_BELOW_MS) pattern = "HH:mm";
            else pattern = "HH:mm:ss";
            if (pattern.equals(datePattern)) return;
            datePattern = pattern;
            timeAxis.setDateFormatOverride(new SimpleDateFormat(pattern));
        }
    }

    /**
     * split readings into 1 series per contiguous run
     * <p>
     * a run ends wherever the next reading is too far away to draw a line to. keeping runs as separate
     * series (rather than 1 series with nulls at the gaps) means an area fill stays a closed shape over
     * the time it covers, and lets a run holding a single reading be drawn as a dot
     *
     * @param keyPrefix each run is keyed "<prefix>/<n>" - series keys have to be unique within a
     *                  dataset, so this has to be unique per set of readings added to one
     * @param maxGapMs  readings further apart than this start a new run
     */
    public static <T> List<TimeSeries> buildSegments(List<T> itemList, ToLongFunction<T> timeFunc,
                                                     Function<T, Number> valueFunc, Object keyPrefix,
                                                     long maxGapMs) {
        List<TimeSeries> seriesList = new ArrayList<>();
        TimeSeries series = null;
        long prevMs = 0;
        for (T item : itemList) {
            Number value = valueFunc.apply(item);
            if (value == null) continue;
            long timeMs = timeFunc.applyAsLong(item);
            if (series == null || timeMs - prevMs > maxGapMs) {
                series = new TimeSeries(keyPrefix + "/" + seriesList.size());
                seriesList.add(series);
            }
            series.addOrUpdate(new FixedMillisecond(timeMs), value);
            prevMs = timeMs;
        }
        return seriesList;
    }

    // tells apart devices sharing a color; 8 colors x 6 patterns = 48 combinations
    private static final float[][] DASH_PATTERNS = {
        null,                               // solid
        {8f, 6f},                           // dashed
        {2f, 4f},                           // dotted
        {10f, 4f, 2f, 4f},                  // dash-dot
        {16f, 6f},                          // long dash
        {10f, 4f, 2f, 4f, 2f, 4f},          // dash-dot-dot
    };

    /**
     * the color for the given series; keyed on a stable index so filtering never repaints the rest
     */
    public static Color getSeriesColor(int index) {
        return Colors.COLOR_CHART_SERIES[Math.abs(index) % Colors.COLOR_CHART_SERIES.length];
    }

    /**
     * the line pattern for the given series - what keeps devices apart once the colors run out
     */
    public static BasicStroke getSeriesStroke(int index) {
        int patternIndex = (Math.abs(index) / Colors.COLOR_CHART_SERIES.length) % DASH_PATTERNS.length;
        float[] dash = DASH_PATTERNS[patternIndex];
        if (dash == null) return STROKE_LINE;
        return new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, dash, 0f);
    }

    /**
     * one-time renderer setup; per-series colors come from {@link #styleSeries}
     */
    public static void prepareLineRenderer(XYLineAndShapeRenderer renderer) {
        renderer.setAutoPopulateSeriesPaint(false);
        renderer.setAutoPopulateSeriesShape(false);
        renderer.setDefaultShape(SHAPE_POINT);
    }

    /**
     * @param isSinglePoint a 1 point run gets a marker; it has no line to draw and would show nothing
     */
    public static void styleSeries(XYLineAndShapeRenderer renderer, int seriesIndex, Color color, boolean isSinglePoint) {
        styleSeries(renderer, seriesIndex, color, STROKE_LINE, isSinglePoint);
    }

    public static void styleSeries(XYLineAndShapeRenderer renderer, int seriesIndex, Color color,
                                   BasicStroke stroke, boolean isSinglePoint) {
        renderer.setSeriesPaint(seriesIndex, color);
        renderer.setSeriesStroke(seriesIndex, stroke);
        renderer.setSeriesShapesVisible(seriesIndex, isSinglePoint);
        renderer.setSeriesShapesFilled(seriesIndex, true);
    }

    /**
     * a short sample of a series' line, for use as a legend swatch
     */
    public static Icon createSeriesIcon(int index, int width, int height) {
        Color color = getSeriesColor(index);
        BasicStroke stroke = getSeriesStroke(index);
        return new Icon() {
            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(stroke);
                int midY = y + height / 2;
                g2.drawLine(x, midY, x + width, midY);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return width;
            }

            @Override
            public int getIconHeight() {
                return height;
            }
        };
    }

    /**
     * tooltips supplement the axes
     */
    public static StandardXYToolTipGenerator createTooltipGenerator(String valueFormat) {
        return new StandardXYToolTipGenerator("{2} @ {1}",
            new SimpleDateFormat("MM-dd HH:mm:ss"), new DecimalFormat(valueFormat));
    }

    public static void stylePlot(XYPlot plot) {
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

    public static void styleAxis(ValueAxis axis) {
        axis.setLabelFont(FONT_TITLE);
        axis.setLabelPaint(Colors.COLOR_CHART_LABEL);
        axis.setTickLabelFont(FONT_LABEL);
        axis.setTickLabelPaint(Colors.COLOR_CHART_LABEL);
        axis.setAxisLinePaint(Colors.COLOR_CHART_AXIS);
        axis.setAxisLineStroke(STROKE_HAIRLINE);
        axis.setTickMarkPaint(Colors.COLOR_CHART_AXIS);
    }
}
