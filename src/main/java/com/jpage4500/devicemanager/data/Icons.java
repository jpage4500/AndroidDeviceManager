package com.jpage4500.devicemanager.data;

/**
 * represents all the images used in the app
 * NOTE: all icons are in png format
 */
public enum Icons {
    ICON_APK("file_apk"),
    ICON_COPY("copy"),

    ;
    private String name;

    Icons(String name) {
        this.name = name;
    }

    public String getName() {
        return name + ".png";
    }
}
