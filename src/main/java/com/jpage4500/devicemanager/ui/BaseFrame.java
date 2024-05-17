package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.ui.views.CustomFrame;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * create and manage device view
 */
public class BaseFrame extends CustomFrame {
    private static final Logger log = LoggerFactory.getLogger(BaseFrame.class);

    public BaseFrame(String prefKey) {
        super(prefKey);

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

        // NOTE: this breaks dragging the scrollbar on Mac
        // getRootPane().putClientProperty("apple.awt.draggableWindowBackground", true);
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
        return createToolbarButton(toolbar, imageName, label, tooltip, 20, listener);
    }

    /**
     * create a 'standard' toolbar button with 40x40 image and label below
     */
    protected JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        return createToolbarButton(toolbar, imageName, label, tooltip, 40, listener);
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
    protected Action createCmdAction(JMenu menu, String label, int key, CustomActionListener listener) {
        Action action = new AbstractAction(label) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(e);
            }
        };
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        KeyStroke keyStroke = KeyStroke.getKeyStroke(key, mask);
        action.putValue(Action.ACCELERATOR_KEY, keyStroke);

        if (menu != null) {
            JMenuItem closeItem = new JMenuItem(label);
            closeItem.setAction(action);
            menu.add(closeItem);
        }

        return action;
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
            JMenuItem closeItem = new JMenuItem(label);
            closeItem.setAction(action);
            menu.add(closeItem);
        }

        return action;
    }

}
