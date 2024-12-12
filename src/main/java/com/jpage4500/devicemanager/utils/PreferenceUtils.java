package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferenceUtils {
    private static final Logger log = LoggerFactory.getLogger(PreferenceUtils.class);

    /**
     * String value preferences
     */
    public enum Pref {
        PREF_CUSTOM_APPS,
        PREF_HIDDEN_COLUMNS,
        PREF_DOWNLOAD_FOLDER,
        PREF_GO_TO_FOLDER_LIST,
        PREF_RECENT_WIRELESS_DEVICES,
        PREF_LAST_DEVICE_IP,
        PREF_CUSTOM_COMMAND_LIST,
        PREF_RECENT_INPUT,
        PREF_MESSAGE_FILTERS,
        PREF_HIDDEN_TOOLBAR_ITEMS,
        PREF_LOGS_HIDDEN_COLUMNS,
        PREF_LOGS_CUSTOM_FILTER,              // logs: last custom filter
        PREF_LOGS_SELECTED_FILTERS,           // logs: last selected filters
        PREF_LOGS_FONT_NAME,
    }

    /**
     * Boolean value preferences
     */
    public enum PrefBoolean {
        PREF_CHECK_UPDATES,
        PREF_ALWAYS_ON_TOP,
        PREF_USE_ROOT,
        PREF_SHOW_BACKGROUND,
        PREF_AUTO_FORMAT_MESSAGE,
        PREF_WRAP_MESSAGE,
        PREF_EXIT_TO_TRAY,
    }

    /**
     * Boolean value preferences
     */
    public enum PrefInt {
        PREF_LOG_LEVEL,
        PREF_LAST_DEVICE_PORT,
        PREF_FONT_SIZE_OFFSET,
        PREF_LOGS_FONT_SIZE,
        PREF_LOGS_FONT_STYLE,
    }

    public static String getPreference(Pref pref) {
        return getPreference(pref.name(), null);
    }

    public static String getPreference(Pref pref, String defaultValue) {
        return getPreference(pref.name(), defaultValue);
    }

    public static boolean getPreference(PrefBoolean pref) {
        return getPreference(pref, false);
    }

    public static boolean getPreference(PrefBoolean pref, boolean defaultValue) {
        return getPreferenceBool(pref.name(), defaultValue);
    }

    public static int getPreference(PrefInt pref, int defaultValue) {
        return getPreferenceInt(pref.name(), defaultValue);
    }

    public static void setPreference(Pref pref, String value) {
        setPreference(pref.name(), value);
    }

    public static void setPreference(PrefBoolean key, boolean value) {
        setPreference(key.name(), value);
    }

    public static void setPreference(PrefInt key, int value) {
        setPreference(key.name(), value);
    }

    public static boolean togglePreference(PrefBoolean prefBoolean) {
        return togglePreference(prefBoolean, false);
    }

    public static boolean togglePreference(PrefBoolean prefBoolean, boolean defaultValue) {
        boolean toggleValue = !getPreference(prefBoolean, defaultValue);
        setPreference(prefBoolean, toggleValue);
        return toggleValue;
    }

    public static void resetAll() {
        Preferences preferences = Preferences.userRoot();
        try {
            preferences.clear();
        } catch (BackingStoreException e) {
            log.error("resetAll: Exception: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    private static String getPreference(String pref, String defaultValue) {
        return getPreferences().get(pref, defaultValue);
    }

    private static boolean getPreferenceBool(String pref, boolean defaultValue) {
        return getPreferences().getBoolean(pref, defaultValue);
    }

    private static int getPreferenceInt(String pref, int defaultValue) {
        return getPreferences().getInt(pref, defaultValue);
    }

    private static void setPreference(String key, String value) {
        if (value == null) {
            getPreferences().remove(key);
        } else {
            getPreferences().put(key, value);
        }
    }

    private static void setPreference(String key, boolean value) {
        getPreferences().putBoolean(key, value);
    }

    private static void setPreference(String key, int value) {
        getPreferences().putInt(key, value);
    }

    private static Preferences getPreferences() {
        return Preferences.userRoot();
    }

}
