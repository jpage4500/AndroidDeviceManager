package com.jpage4500.devicemanager.data;

/**
 * represents all the images used in the app
 * NOTE: all icons are in png format
 */
public enum Icons {
    // Device and Status Icons
    ADB("adb"),
    ANDROID("android"),
    DEVICE_LOCAL("device_local"),
    DEVICE_REMOTE("device_remote"),

    // Battery Icons
    BATTERY_LEVEL0("battery_level0"),
    BATTERY_LEVEL1("battery_level1"),
    BATTERY_LEVEL2("battery_level2"),
    BATTERY_LEVEL3("battery_level3"),
    BATTERY_LEVEL4("battery_level4"),
    CHARGING("charging"),

    // Arrow Icons
    ARROW_DOWN("arrow_down"),
    ARROW_RIGHT("arrow_right"),
    ARROW_UP("arrow_up"),

    // File Type Icons
    FILE_APK("file_apk"),
    FILE_JSON("file_json"),
    FILE_LOGS("file_logs"),
    FILE_SAVE("file_save"),
    FILE_SCRIPT("file_script"),
    FILE_XML("file_xml"),

    // General Icons
    ICON_ADD("icon_add"),
    ICON_ADB("icon_adb"),
    ICON_BOOKMARK("icon_bookmark"),
    ICON_CLOSE("icon_close"),
    ICON_COPY("copy"),
    ICON_DELETE("icon_delete"),
    ICON_DOWNLOAD("icon_download"),
    ICON_EDIT("icon_edit"),
    ICON_ERROR("icon_error"),
    ICON_FILE("icon_file"),
    ICON_FILE_LINK("icon_file_link"),
    ICON_FILE_READONLY("icon_file_readonly"),
    ICON_FILTER("icon_filter"),
    ICON_FOLDER("icon_folder"),
    ICON_FOLDER_LINK("icon_folder_link"),
    ICON_FOLDER_NEW("icon_folder_new"),
    ICON_FOLDER_READONLY("icon_folder_readonly"),
    ICON_FOLDER_UP("icon_folder_up"),
    ICON_LOGS("icon_logs"),
    ICON_MORE("icon_more"),
    ICON_OPEN("icon_open"),
    ICON_OPEN_FOLDER("icon_open_folder"),
    ICON_OVERFLOW("icon_overflow"),
    ICON_PAUSE("icon_pause"),
    ICON_PLAY("icon_play"),
    ICON_POWER("icon_power"),
    ICON_SAVE("icon_save"),
    ICON_SCRCPY("icon_scrcpy"),
    ICON_SCREENSHOT("icon_screenshot"),
    ICON_SCRIPT("icon_script"),
    ICON_SETTINGS("icon_settings"),
    ICON_STAR("icon_star"),
    ICON_STOP("icon_stop"),
    ICON_SUCCESS("icon_success"),
    ICON_TERMINAL("icon_terminal"),
    ICON_TRASH("icon_trash"),
    ICON_UPDATE("icon_update"),
    ICON_VARIABLE("icon_variable"),

    // UI Icons
    BROWSE("browse"),
    CLEAR_FILTER("clear_filter"),
    EMPTY_IMAGE("empty_image"),
    EYE_CLOSED("eye_closed"),
    EYE_OPEN("eye_open"),
    KEYBOARD("keyboard"),
    LOGO("logo"),
    MEMORY("memory"),
    MIRROR("mirror"),
    NETWORK("network"),
    REFRESH("refresh"),
    RESTART("restart"),
    ROOT("root"),
    ROOT_ENABLED("root_enabled"),
    SCREEN_RECORD("screen_record"),
    SCREENSHOT("screenshot"),
    SERVER("server"),
    SHARE_OFF("share_off"),
    SHARE_ON("share_on"),
    SIZE("size"),
    STATUS_BUSY("status_busy"),
    STATUS_ERROR("status_error"),
    STATUS_OFFLINE("status_offline"),
    STATUS_ONLINE("status_online"),
    SYSTEM_TRAY("system_tray"),
    TRAY_ICON("tray_icon"),
    WRAP("wrap"),

    ;

    private final String name;

    Icons(String name) {
        this.name = name;
    }

    public String getName() {
        return name + ".png";
    }
}
