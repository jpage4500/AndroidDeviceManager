package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;

/**
 * create and manage device view
 */
public class BaseScreen extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(BaseScreen.class);

    private String prefKey;

    public BaseScreen(String prefKey, int defaultWidth, int defaultHeight) {
        this.prefKey = prefKey;
        restoreFrameSize(defaultWidth, defaultHeight);

        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                onWindowStateChanged(WindowState.ACTIVATED);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                onWindowStateChanged(WindowState.DEACTIVATED);
            }

            @Override
            public void windowOpened(WindowEvent e) {
                onWindowStateChanged(WindowState.OPENED);
            }

            @Override
            public void windowClosing(WindowEvent e) {
                onWindowStateChanged(WindowState.CLOSING);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                onWindowStateChanged(WindowState.CLOSED);
            }
        });

        // TODO: handle window resizing
        //if (PreferenceUtils.getPreference(PreferenceUtils.PrefBoolean.PREF_DEBUG_MODE)) {
        //    addComponentListener(new ComponentAdapter() {
        //        @Override
        //        public void componentResized(ComponentEvent componentEvent) {
        //            log.trace("componentResized: {}: W:{}, H:{}", prefKey, getWidth(), getHeight());
        //        }
        //    });
        //}

        // NOTE: this breaks dragging the scrollbar on Mac
        // getRootPane().putClientProperty("apple.awt.draggableWindowBackground", true);

        // TODO: not sure if this is used.. I don't see it on Mac or Linux
        //if (!Utils.isMac()) {
        //    BufferedImage image = UiUtils.getImage("system_tray.png", 100, 100);
        //    setIconImage(image);
        //}

    }

    public enum WindowState {
        ACTIVATED,      // visible
        DEACTIVATED,    // background
        OPENED,
        CLOSING,        // closing (user closed window)
        CLOSED          // closed (NOTE: not sent if HIDE_ON_CLOSE is used)
    }

    protected void onWindowStateChanged(WindowState state) {
        //log.trace("onWindowStateChanged: {}: {}", prefKey, state);
    }

    protected JButton createSmallToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        return createToolbarButton(toolbar, imageName, label, tooltip, UiUtils.IMG_SIZE_TOOLBAR_SMALL, listener);
    }

    /**
     * create a 'standard' toolbar button with 40x40 image and label below
     */
    protected JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        return createToolbarButton(toolbar, imageName, label, tooltip, UiUtils.IMG_SIZE_TOOLBAR, listener);
    }

    protected JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, int size, ActionListener listener) {
        JButton button = new JButton(label);
        if (imageName != null) {
            ImageIcon icon = UiUtils.getImageIcon(imageName, size, size);
            //image = replaceColor(image, new Color(0, 38, 255, 184));
            button.setIcon(icon);
        }

        button.setFont(new Font(Font.SERIF, Font.PLAIN, 10));
        if (tooltip != null) button.setToolTipText(tooltip);
        button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalTextPosition(SwingConstants.CENTER);
        button.addActionListener(listener);
        toolbar.add(button);
        return button;
    }

    public interface CustomActionListener {
        void actionPerformed(ActionEvent e);
    }

    /**
     * create shortcut key using CMD key
     */
    protected JMenuItem createCmdAction(JMenu menu, String label, int key, CustomActionListener listener) {
        Action action = new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(e);
            }
        };
        if (key != 0) {
            int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            KeyStroke keyStroke = KeyStroke.getKeyStroke(key, mask);
            action.putValue(Action.ACCELERATOR_KEY, keyStroke);
        }

        return UiUtils.addMenuItem(menu, label, action);
    }

    /**
     * create shortcut key using OPTION key
     */
    protected Action createOptionAction(JMenu menu, String label, int key, CustomActionListener listener) {
        Action action = new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(e);
            }
        };
        KeyStroke keyStroke = KeyStroke.getKeyStroke(key, InputEvent.ALT_DOWN_MASK);
        action.putValue(Action.ACCELERATOR_KEY, keyStroke);

        if (menu != null) {
            UiUtils.addMenuItem(menu, label, action);
        }

        return action;
    }

    /**
     * save current frame size
     */
    protected void saveFrameSize() {
        Preferences prefs = Preferences.userRoot();
        Rectangle rect = getBounds();
        prefs.put(prefKey, GsonHelper.toJson(rect));
    }

    /**
     * restore frame size
     */
    private void restoreFrameSize(int defaultWidth, int defaultHeight) {
        Preferences prefs = Preferences.userRoot();
        String savedFrameSize = prefs.get(prefKey, null);
        Rectangle r = GsonHelper.fromJson(savedFrameSize, Rectangle.class);
        if (r == null) {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (screenSize.width - defaultWidth) / 2;
            int y = (screenSize.height - defaultHeight) / 2;
            r = new Rectangle(x, y, defaultWidth, defaultHeight);
        }
        setLocation(r.x, r.y);
        setSize(r.width, r.height);
    }

}
