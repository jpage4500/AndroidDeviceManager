package com.jpage4500.devicemanager.ui;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.*;

public class HintTextField extends JTextField {
    private Font origFont;
    private Font hintFont;

    public HintTextField(final String hint) {
        setText(hint);

        origFont = getFont();
        hintFont = new Font(origFont.getFontName(), Font.ITALIC, origFont.getSize());
        setFont(hintFont);
        setForeground(Color.GRAY);

        this.addFocusListener(new FocusAdapter() {

            @Override
            public void focusGained(FocusEvent e) {
                if (getText().equals(hint)) {
                    setText("");
                    setFont(origFont);
                } else {
                    setText(getText());
                    setFont(origFont);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (getText().equals(hint) || getText().length() == 0) {
                    setText(hint);
                    setFont(hintFont);
                    setForeground(Color.GRAY);
                } else {
                    setText(getText());
                    setFont(origFont);
                    setForeground(Color.BLACK);
                }
            }
        });

    }
}