package com.jpage4500.devicemanager.utils;

import javax.swing.*;
import java.awt.*;

/**
 * Central animation utility holding reusable animation classes for screen input feedback.
 * Provides unified color scheme and base Animation lifecycle.
 */
public final class Animations {
    // Unified colors for all animations
    public static final Color MAIN_COLOR = new Color(0, 255, 180);
    public static final Color GLOW_COLOR = new Color(0, 255, 180, 80);

    private Animations() { /* no instances */ }

    /** Base animation class with timing and progress helpers */
    public abstract static class Animation {
        protected final long startTime = System.currentTimeMillis();
        protected final int durationMs;
        protected Animation(int durationMs) { this.durationMs = durationMs; }
        public boolean isFinished() { return System.currentTimeMillis() - startTime >= durationMs; }
        public double progress() { return Math.min(1.0, (System.currentTimeMillis() - startTime) / (double) durationMs); }
        public abstract void paint(Graphics2D g);
    }

    /** Tap (click) animation: expanding ring with glow and inner pulse */
    public static class TapAnimation extends Animation {
        private final int x, y;
        private final int maxRadius = 30; // tuned size
        private static final int TAP_GROW_MS = 200;
        private static final int TAP_HOLD_MS = 50;
        private static final int TAP_FADE_MS = 350;
        public TapAnimation(int x, int y) { super(TAP_GROW_MS + TAP_HOLD_MS + TAP_FADE_MS); this.x = x; this.y = y; }
        @Override
        public void paint(Graphics2D g) {
            long elapsed = System.currentTimeMillis() - startTime;
            float alpha = elapsed < TAP_GROW_MS + TAP_HOLD_MS ? 1f : 1f - Math.min(1f, (elapsed - TAP_GROW_MS - TAP_HOLD_MS) / (float) TAP_FADE_MS);
            if (alpha <= 0f) return;
            double pGrow = Math.min(1.0, elapsed / (double) TAP_GROW_MS);
            int outerR = (int) (maxRadius * pGrow);
            int innerR = Math.max(12, (int) (outerR * 0.4));
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            if (outerR > 0) {
                g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(GLOW_COLOR);
                g2.drawOval(x - outerR, y - outerR, outerR * 2, outerR * 2);
            }
            g2.setStroke(new BasicStroke(4f));
            g2.setColor(MAIN_COLOR);
            g2.drawOval(x - outerR, y - outerR, outerR * 2, outerR * 2);
            double pulseScale;
            if (elapsed < TAP_GROW_MS) pulseScale = pGrow; else if (elapsed < TAP_GROW_MS + TAP_HOLD_MS) pulseScale = 1.0; else pulseScale = 1.0 - ((elapsed - TAP_GROW_MS - TAP_HOLD_MS) / (double) TAP_FADE_MS) * 0.3;
            int pulseR = (int) (innerR * pulseScale);
            g2.setColor(new Color(MAIN_COLOR.getRed(), MAIN_COLOR.getGreen(), MAIN_COLOR.getBlue(), (int)(150 * alpha)));
            g2.fillOval(x - pulseR, y - pulseR, pulseR * 2, pulseR * 2);
            g2.dispose();
        }
    }

    /** Swipe (gesture) animation: directional arrow with glow and start marker */
    public static class SwipeAnimation extends Animation {
        private final int x1, y1, x2, y2;
        private static final int GROWTH_MS = 250;
        private static final int HOLD_MS = 700;
        private static final int FADE_MS = 350;
        private static final double HEAD_LEN = 32;
        private static final double HEAD_WIDTH = 26;
        public SwipeAnimation(int x1, int y1, int x2, int y2) { super(GROWTH_MS + HOLD_MS + FADE_MS); this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; }
        @Override
        public void paint(Graphics2D g) {
            long elapsed = System.currentTimeMillis() - startTime;
            float alpha = elapsed < GROWTH_MS + HOLD_MS ? 1f : 1f - Math.min(1f, (elapsed - GROWTH_MS - HOLD_MS) / (float) FADE_MS);
            if (alpha <= 0f) return;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            double dx = x2 - x1;
            double dy = y2 - y1;
            double len = Math.hypot(dx, dy);
            if (len < 2) return;
            double growthProgress = Math.min(1.0, elapsed / (double) GROWTH_MS);
            double currentTotalLen = len * growthProgress;
            double effectiveHeadLen = Math.min(HEAD_LEN, currentTotalLen);
            double lineLen = Math.max(0, currentTotalLen - effectiveHeadLen);
            double ux = dx / len;
            double uy = dy / len;
            double lineEndX = x1 + ux * lineLen;
            double lineEndY = y1 + uy * lineLen;
            double tipX = x1 + ux * currentTotalLen;
            double tipY = y1 + uy * currentTotalLen;
            double backX = tipX - ux * effectiveHeadLen;
            double backY = tipY - uy * effectiveHeadLen;
            Graphics2D gGlow = (Graphics2D) g.create();
            gGlow.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            gGlow.setColor(GLOW_COLOR);
            if (lineLen > 0) gGlow.drawLine(x1, y1, (int) lineEndX, (int) lineEndY); else gGlow.drawLine(x1, y1, (int) tipX, (int) tipY);
            gGlow.dispose();
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(MAIN_COLOR);
            if (lineLen > 0) g.drawLine(x1, y1, (int) lineEndX, (int) lineEndY);
            double perpX = -uy;
            double perpY = ux;
            double halfWidth = HEAD_WIDTH / 2.0;
            double leftX = backX + perpX * halfWidth;
            double leftY = backY + perpY * halfWidth;
            double rightX = backX - perpX * halfWidth;
            double rightY = backY - perpY * halfWidth;
            Graphics2D gHeadGlow = (Graphics2D) g.create();
            gHeadGlow.setColor(GLOW_COLOR);
            int[] glowXs = {(int) tipX, (int) leftX, (int) rightX};
            int[] glowYs = {(int) tipY, (int) leftY, (int) rightY};
            gHeadGlow.fillPolygon(glowXs, glowYs, 3);
            gHeadGlow.dispose();
            g.setColor(MAIN_COLOR);
            int[] xs = {(int) tipX, (int) leftX, (int) rightX};
            int[] ys = {(int) tipY, (int) leftY, (int) rightY};
            g.fillPolygon(xs, ys, 3);
            float startAlpha = (float)(1.0 - growthProgress) * alpha;
            if (startAlpha > 0.05f) {
                Graphics2D gStart = (Graphics2D) g.create();
                gStart.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, startAlpha));
                gStart.setColor(MAIN_COLOR);
                int r = 14;
                gStart.fillOval(x1 - r, y1 - r, r * 2, r * 2);
                gStart.dispose();
            }
        }
    }

    /** Key animation: centered bubble showing key text */
    public static class KeyAnimation extends Animation {
        private final String text;
        private final JComponent panel; // used for size
        public KeyAnimation(String text, JComponent panel) { super(600); this.text = text; this.panel = panel; }
        @Override
        public void paint(Graphics2D g) {
            double p = progress();
            float alpha = (float) (1.0 - p);
            if (alpha <= 0f) return;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            int panelW = panel.getWidth();
            int panelH = panel.getHeight();
            int fontSize = Math.max(34, panelW / 9);
            Font font = g.getFont().deriveFont(Font.BOLD, fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int textW = fm.stringWidth(text);
            int textH = fm.getAscent();
            int x = (panelW - textW) / 2;
            int y = (panelH + textH) / 2;
            int padding = 26;
            int bubbleW = textW + padding * 2;
            int bubbleH = textH + padding;
            int bubbleX = x - padding;
            int bubbleY = y - textH - padding / 2;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.55f));
            g2.setColor(GLOW_COLOR);
            g2.fillRoundRect(bubbleX - 6, bubbleY - 6, bubbleW + 12, bubbleH + 12, 40, 40);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(new Color(MAIN_COLOR.getRed(), MAIN_COLOR.getGreen(), MAIN_COLOR.getBlue(), (int)(195 * alpha)));
            g2.fillRoundRect(bubbleX, bubbleY, bubbleW, bubbleH, 32, 32);
            g2.setColor(Color.BLACK);
            g2.drawString(text, x, y);
            g2.dispose();
        }
    }
}

