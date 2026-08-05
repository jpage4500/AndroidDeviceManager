package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Colors;
import com.jpage4500.devicemanager.utils.UiUtils;

import javax.swing.JPanel;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * a group of related values drawn as a rounded card floating above the window background
 * <p>
 * this is what gives a long list of values some structure: each card is one visual group, separated
 * from its neighbors by the gap between them instead of by a titled border around everything
 */
public class CardPanel extends JPanel {
    private static final int ARC = 14;

    public CardPanel(LayoutManager layout) {
        super(layout);
        // the rounded fill is painted below; an opaque panel would paint square corners over it first
        setOpaque(false);
        // components added to a card don't set a background of their own, so they inherit this one
        setBackground(Colors.COLOR_CARD_BACKGROUND);
    }

    /**
     * make the whole card open something on click
     * <p>
     * the labels and bars inside a card have no mouse listeners of their own, so a click anywhere on
     * the card arrives here. the hand cursor and the highlight on hover are the only hints that a card
     * is clickable - there's no chevron, which would compete with the card's own content
     */
    public void setClickAction(Runnable action) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        UiUtils.addLeftClickListener(this, mouseEvent -> action.run());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent mouseEvent) {
                setBackground(Colors.COLOR_CARD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {
                setBackground(Colors.COLOR_CARD_BACKGROUND);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // -1: drawRoundRect draws *on* the far edge, which would otherwise fall outside the panel
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
            g2.setColor(Colors.COLOR_CARD_BORDER);
            g2.drawRoundRect(0, 0, w, h, ARC, ARC);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
