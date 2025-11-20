package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
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
    private String titleBackup;
    private Timer resizeTitleTimer;

    public BaseScreen(String prefKey, int defaultWidth, int defaultHeight) {
        this.prefKey = prefKey;
        restoreFrameSize(defaultWidth, defaultHeight);

        // by default do nothing on exit - each screen needs to handle onWindowStateChanged(CLOSING)
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

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

        // handle window resizing by changing title to "WxH"
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
                handleResize();
            }
        });

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

    /**
     * Handle window resize - show dimensions in title temporarily
     */
    private void handleResize() {
        // backup original title on first resize
        if (titleBackup == null) {
            titleBackup = getTitle();
        }

        // show current dimensions in title
        int width = getWidth();
        int height = getHeight();
        setTitle(width + "x" + height);

        // reset or start timer to restore original title after 1 second
        if (resizeTitleTimer != null) {
            resizeTitleTimer.restart();
        } else {
            resizeTitleTimer = new Timer(1000, e -> restoreTitle());
            resizeTitleTimer.setRepeats(false);
            resizeTitleTimer.start();
        }
    }

    /**
     * Restore original title after resize completes
     */
    private void restoreTitle() {
        if (titleBackup != null) {
            setTitle(titleBackup);
            titleBackup = null;
        }
        if (resizeTitleTimer != null) {
            resizeTitleTimer.stop();
            resizeTitleTimer = null;
        }
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
    protected JMenuItem createCmdMenuItem(JMenu menu, String label, int key, CustomActionListener listener) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke keyStroke = KeyStroke.getKeyStroke(key, mask);
        return createMenuItem(menu, label, keyStroke, listener);
    }

    /**
     * create JMenuItem with label and can be actived using KeyStroke
     *
     * @param keyStroke - optional keystroke used to active menu item
     * @param listener  - listener when menu item is selected
     */
    protected JMenuItem createMenuItem(JMenu menu, String label, KeyStroke keyStroke, CustomActionListener listener) {
        Action action = new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(e);
            }
        };

        JMenuItem item = UiUtils.addMenuItem(menu, label, action);
        if (keyStroke != null) {
            action.putValue(Action.ACCELERATOR_KEY, keyStroke);
            item.setAccelerator(keyStroke);
        }
        return item;
    }

    /**
     * save current frame size
     */
    protected void saveFrameSize() {
        Preferences prefs = Preferences.userRoot();
        Rectangle rect = getBounds();
        //log.trace("saveFrameSize: {}, w:{}, h:{}", prefKey, rect.width, rect.height);
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
