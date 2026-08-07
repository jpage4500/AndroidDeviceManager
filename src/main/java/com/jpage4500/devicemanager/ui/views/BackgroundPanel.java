package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.data.Icons;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.UiUtils;

import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

/**
 * a panel with the app's background image tiled over it, like the device list and device info screens
 */
public class BackgroundPanel extends JPanel {
    // loaded once, at full size; drawBackgroundImage tiles it
    private static final BufferedImage BACKGROUND_IMAGE = UiUtils.getImage(Icons.BACKGROUND, 0);

    private boolean showBackground;

    public BackgroundPanel(LayoutManager layout) {
        super(layout);
        refreshShowBackground();
    }

    /**
     * re-read the Settings toggle; call whenever the screen refreshes
     */
    public void refreshShowBackground() {
        showBackground = PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_SHOW_BACKGROUND, true);
    }

    /**
     * painting has to start here for the background to be included: a child repainting itself would
     * otherwise punch a hole in it that the next repaint wouldn't fill back in
     */
    @Override
    protected boolean isPaintingOrigin() {
        return showBackground;
    }

    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);
        // over the content, not behind it: the children are filled, so a background underneath would
        // only show in the gaps between them
        if (showBackground) UiUtils.drawBackgroundImage(graphics, BACKGROUND_IMAGE, 0, getWidth(), getHeight());
    }
}
