package com.jpage4500.devicemanager.utils;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class MyMouseAdapter extends MouseAdapter {
    private final ClickListener listener;
    private final Boolean leftClick;
    private boolean isEntered;

    /**
     * @param leftClick - true to only respond on LEFT click; false to only respond on RIGHT click; null to respond to BOTH
     */
    public MyMouseAdapter(ClickListener listener, Boolean leftClick) {
        this.leftClick = leftClick;
        this.listener = listener;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        super.mouseReleased(e);
        if (isEntered) {
            if (leftClick != null) {
                boolean isLeftClick = SwingUtilities.isLeftMouseButton(e);
                if (leftClick != isLeftClick) return;
            }
            listener.onClick(e);
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        super.mouseEntered(e);
        isEntered = true;
    }

    @Override
    public void mouseExited(MouseEvent e) {
        super.mouseExited(e);
        isEntered = false;
    }
}
