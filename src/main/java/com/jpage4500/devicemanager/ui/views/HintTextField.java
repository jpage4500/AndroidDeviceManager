package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class HintTextField extends JTextField {
    private static final Logger log = LoggerFactory.getLogger(HintTextField.class);

    private Font origFont;
    private Font hintFont;

    private String hintText;

    public HintTextField(final String hint) {
        this.hintText = hint;

        origFont = getFont();
        hintFont = new Font(origFont.getFontName(), Font.ITALIC, origFont.getSize());
        setFont(hintFont);
        setForeground(Color.GRAY);
        setText(hint);

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                setFont(origFont);
                if (getText().equals(hintText)) {
                    setText("");
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (getText().isEmpty()) {
                    setText(hintText);
                    setFont(hintFont);
                }
            }
        });
    }

    @Override
    public void setText(String t) {
        super.setText(t);

        boolean isHint = TextUtils.equalsIgnoreCase(t, hintText);

        if (isHint) {
            setFont(hintFont);
            setForeground(Color.GRAY);
        } else {
            setFont(origFont);
            setForeground(Color.BLACK);
        }
    }
}