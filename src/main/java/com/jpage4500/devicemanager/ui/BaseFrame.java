package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.ui.views.CustomFrame;
import com.jpage4500.devicemanager.ui.views.EmptyView;
import com.jpage4500.devicemanager.ui.views.StatusBar;
import com.jpage4500.devicemanager.utils.UiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;

/**
 * create and manage device view
 */
public class BaseFrame extends CustomFrame {
    private static final Logger log = LoggerFactory.getLogger(BaseFrame.class);

    public EmptyView emptyView;
    public StatusBar statusBar;
    public JToolBar toolbar;

    public BaseFrame(String prefKey) {
        super(prefKey);
    }

    /**
     * create a 'standard' toolbar button with 40x40 image and label below
     */
    protected JButton createToolbarButton(JToolBar toolbar, String imageName, String label, String tooltip, ActionListener listener) {
        BufferedImage image = UiUtils.getImage(imageName, 40, 40);
        //image = replaceColor(image, new Color(0, 38, 255, 184));
        JButton button = new JButton(label, new ImageIcon(image));

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
    protected Action createAction(JMenu menu, String label, int key, CustomActionListener listener) {
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
