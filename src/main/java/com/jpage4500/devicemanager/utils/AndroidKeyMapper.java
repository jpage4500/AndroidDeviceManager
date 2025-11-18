package com.jpage4500.devicemanager.utils;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps Java KeyEvent codes to Android keycodes
 * Android keycode reference: https://developer.android.com/reference/android/view/KeyEvent
 */
public class AndroidKeyMapper {

    // Android keycodes
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_HOME = 3;
    public static final int KEYCODE_MENU = 82;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_VOLUME_UP = 24;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int KEYCODE_POWER = 26;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_DEL = 67;
    public static final int KEYCODE_TAB = 61;
    public static final int KEYCODE_SPACE = 62;
    public static final int KEYCODE_ESCAPE = 111;
    public static final int KEYCODE_FORWARD_DEL = 112;
    public static final int KEYCODE_PAGE_UP = 92;
    public static final int KEYCODE_PAGE_DOWN = 93;
    public static final int KEYCODE_INSERT = 124;
    public static final int KEYCODE_F1 = 131;
    public static final int KEYCODE_F2 = 132;
    public static final int KEYCODE_F3 = 133;
    public static final int KEYCODE_F4 = 134;
    public static final int KEYCODE_F5 = 135;
    public static final int KEYCODE_F6 = 136;
    public static final int KEYCODE_F7 = 137;
    public static final int KEYCODE_F8 = 138;
    public static final int KEYCODE_F9 = 139;
    public static final int KEYCODE_F10 = 140;
    public static final int KEYCODE_F11 = 141;
    public static final int KEYCODE_F12 = 142;
    public static final int KEYCODE_MOVE_HOME = 122;
    public static final int KEYCODE_MOVE_END = 123;
    public static final int KEYCODE_WAKEUP = 224;

    private static final Map<Integer, Integer> keyMap = new HashMap<>();

    static {
        // Navigation keys
        keyMap.put(KeyEvent.VK_UP, KEYCODE_DPAD_UP);
        keyMap.put(KeyEvent.VK_DOWN, KEYCODE_DPAD_DOWN);
        keyMap.put(KeyEvent.VK_LEFT, KEYCODE_DPAD_LEFT);
        keyMap.put(KeyEvent.VK_RIGHT, KEYCODE_DPAD_RIGHT);

        // Special keys
        keyMap.put(KeyEvent.VK_ENTER, KEYCODE_ENTER);
        keyMap.put(KeyEvent.VK_BACK_SPACE, KEYCODE_DEL);
        keyMap.put(KeyEvent.VK_DELETE, KEYCODE_FORWARD_DEL);
        keyMap.put(KeyEvent.VK_TAB, KEYCODE_TAB);
        keyMap.put(KeyEvent.VK_SPACE, KEYCODE_SPACE);
        keyMap.put(KeyEvent.VK_ESCAPE, KEYCODE_BACK);

        // Page navigation
        keyMap.put(KeyEvent.VK_PAGE_UP, KEYCODE_PAGE_UP);
        keyMap.put(KeyEvent.VK_PAGE_DOWN, KEYCODE_PAGE_DOWN);
        keyMap.put(KeyEvent.VK_HOME, KEYCODE_MOVE_HOME);
        keyMap.put(KeyEvent.VK_END, KEYCODE_MOVE_END);
        keyMap.put(KeyEvent.VK_INSERT, KEYCODE_INSERT);

        // Function keys
        keyMap.put(KeyEvent.VK_F1, KEYCODE_F1);
        keyMap.put(KeyEvent.VK_F2, KEYCODE_F2);
        keyMap.put(KeyEvent.VK_F3, KEYCODE_F3);
        keyMap.put(KeyEvent.VK_F4, KEYCODE_F4);
        keyMap.put(KeyEvent.VK_F5, KEYCODE_F5);
        keyMap.put(KeyEvent.VK_F6, KEYCODE_F6);
        keyMap.put(KeyEvent.VK_F7, KEYCODE_F7);
        keyMap.put(KeyEvent.VK_F8, KEYCODE_F8);
        keyMap.put(KeyEvent.VK_F9, KEYCODE_F9);
        keyMap.put(KeyEvent.VK_F10, KEYCODE_F10);
        keyMap.put(KeyEvent.VK_F11, KEYCODE_F11);
        keyMap.put(KeyEvent.VK_F12, KEYCODE_F12);
    }

    /**
     * Map Java KeyEvent keycode to Android keycode
     * @param javaKeyCode Java KeyEvent.VK_* constant
     * @return Android keycode, or null if no mapping exists
     */
    public static Integer mapKeyCode(int javaKeyCode) {
        return keyMap.get(javaKeyCode);
    }

    /**
     * Check if a Java keycode should be handled as a keyevent (vs text input)
     */
    public static boolean isSpecialKey(int javaKeyCode) {
        return keyMap.containsKey(javaKeyCode);
    }
}

