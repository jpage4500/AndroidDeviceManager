package com.jpage4500.devicemanager.ui.views;

import com.jpage4500.devicemanager.utils.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

public class HintTextField extends JTextField implements KeyListener {
    private static final Logger log = LoggerFactory.getLogger(HintTextField.class);

    private static final int SHIFT_COMMAND_MASK = InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK;

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

        addTextListener(listener);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                super.keyTyped(e);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);
                switch (e.getExtendedKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> {
                        setText(null);
                        e.consume();
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                super.keyReleased(e);
            }
        });
    }

    public void addTextListener(TextListener listener) {
        if (listener == null) return;
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
    }

    /**
     * get value ignoring the hint text
     */
    public String getCleanText() {
        String text = getText();
        if (TextUtils.equals(text, hintText)) return "";
        else return text;
    }

    /**
     * clear text and replace with hint text
     */
    public void reset() {
        String text = getText();
        if (!TextUtils.equals(text, hintText)) setText(hintText);
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

    /**
     * allow searching when focus is on another component
     */
    public void setupSearch(JComponent component) {
        component.removeKeyListener(this);
        component.addKeyListener(this);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        char keyChar = e.getKeyChar();
        int keyCode = e.getKeyCode();
        switch (keyCode) {
            case KeyEvent.VK_BACK_SPACE: {
                // delete last character
                String cleanText = getCleanText();
                if (!cleanText.isEmpty()) {
                    cleanText = cleanText.substring(0, cleanText.length() - 1);
                    if (cleanText.isEmpty()) setText(hintText);
                    else setText(cleanText);
                }
                break;
            }
            case KeyEvent.VK_ESCAPE:
                // clear text
                setText(hintText);
                break;
            case KeyEvent.VK_TAB:
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_RIGHT:
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN:
            case KeyEvent.VK_PAGE_UP:
            case KeyEvent.VK_PAGE_DOWN:
                // ignore these keys
                break;
            default:
                // only interested in printable characters
                boolean include;
                switch (keyChar) {
                    case '.':
                    case ' ':
                    case ',':
                    case '-':
                    case '_':
                    case '!':
                    case '%':
                    case '&':
                    case '*':
                    case '$':
                    case '#':
                    case '@':
                    case '(':
                    case ')':
                    case '[':
                    case ']':
                    case '+':
                    case '"':
                    case '?':
                    case '\'':
                        // allow these characters
                        include = true;
                        break;
                    default:
                        include = Character.isLetterOrDigit(keyChar);
                        break;
                }
                if (include) {
                    String cleanText = getCleanText();
                    cleanText += keyChar;
                    setText(cleanText);
                }
        }
    }

    @Override
    public void keyTyped(KeyEvent keyEvent) {

    }

    @Override
    public void keyReleased(KeyEvent keyEvent) {

    }

}