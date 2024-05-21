package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class HintTextField extends JTextField {
    private static final Logger log = LoggerFactory.getLogger(HintTextField.class);

    private final Font origFont;
    private final Font hintFont;
    private final String hintText;

    public interface TextListener {
        void textChanged(String text);
    }

    public HintTextField(final String hint, TextListener listener) {
        this.hintText = hint;

        origFont = getFont();
        hintFont = new Font(origFont.getFontName(), Font.ITALIC, origFont.getSize());
        setFont(hintFont);
        setForeground(Color.GRAY);
        setText(hint);

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                log.debug("focusGained: ");
                setFont(origFont);
                if (getText().equals(hintText)) {
                    setText("");
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                log.debug("focusLost: ");
                if (getText().isEmpty()) {
                    setText(hintText);
                    setFont(hintFont);
                }
            }
        });

        getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent documentEvent) {
                        listener.textChanged(getCleanText());
                    }

                    @Override
                    public void removeUpdate(DocumentEvent documentEvent) {
                        listener.textChanged(getCleanText());
                    }

                    @Override
                    public void changedUpdate(DocumentEvent documentEvent) {
                    }
                });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                switch (e.getExtendedKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> setText(null);
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                super.keyReleased(e);
            }
        });
    }

    /**
     * get value ignoring the hint text
     */
    public String getCleanText() {
        String text = getText();
        if (TextUtils.equals(text, hintText)) return "";
        else return text;
    }

    @Override
    public void setText(String t) {
        super.setText(t);

        boolean isHint = TextUtils.equals(t, hintText);

        if (isHint) {
            setFont(hintFont);
            setForeground(Color.GRAY);
        } else {
            setFont(origFont);
            setForeground(Color.BLACK);
        }
    }
}