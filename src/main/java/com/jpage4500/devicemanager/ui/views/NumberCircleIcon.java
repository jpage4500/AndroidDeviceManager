package com.jpage4500.devicemanager.ui.views;

import javax.swing.*;
import java.awt.*;

/**
 * Icon that displays a number inside a circle
 * - has optional hover effect which can be used with this code:
 * label.addMouseListener(new MouseAdapter() {
 * -               public void mouseEntered(MouseEvent e) {
 * -                   icon.setHovered(true);
 * -                   label.repaint();
 * -               }
 * -               public void mouseExited(MouseEvent e) {
 * -                   icon.setHovered(false);
 * -                   label.repaint();
 * -               }
 * -           });
 */
public class NumberCircleIcon implements Icon {
    private int number;
    private int diameter;
    private Color circleColor;
    private Color hoverColor;
    private Color textColor;
    private boolean isHovered = false;

    public NumberCircleIcon(int number, int diameter, Color circleColor, Color textColor) {
        this.number = number;
        this.diameter = diameter;
        this.circleColor = circleColor;
        this.hoverColor = circleColor.darker();
        this.textColor = textColor;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public void setDiameter(int diameter) {
        this.diameter = diameter;
    }

    public void setCircleColor(Color color) {
        this.circleColor = color;
        this.hoverColor = color.darker();
    }

    public void setTextColor(Color color) {
        this.textColor = color;
    }

    public void setHovered(boolean hovered) {
        this.isHovered = hovered;
    }

    @Override
    public int getIconWidth() {
        return diameter;
    }

    @Override
    public int getIconHeight() {
        return diameter;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the circle
        g2.setColor(isHovered ? hoverColor : circleColor);
        g2.fillOval(x, y, diameter, diameter);

        // Draw the text
        if (number > 0) {
            g2.setColor(textColor);
            g2.setFont(c.getFont().deriveFont(Font.BOLD, diameter * 0.5f));
            FontMetrics fm = g2.getFontMetrics();
            String text = String.valueOf(number);
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent() - fm.getDescent();
            int textX = x + diameter / 2 - textWidth / 2;
            int textY = y + diameter / 2 + textHeight / 2;

            g2.drawString(text, textX, textY);
        }
        g2.dispose();
    }
}
