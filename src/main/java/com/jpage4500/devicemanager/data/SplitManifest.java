package com.jpage4500.devicemanager.data;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SplitManifest {
    @SerializedName("package_name")
    public String packageName;
    @SerializedName("total_size")
    public long totalSize;

    @SerializedName("split_apks")
    public List<SplitApk> splitApkList;

    public class SplitApk {
        public String file;
        public String id;
    }
}

/*

{
    "xapk_version": 2,
    "package_name": "com.DB.playinstore",
    "name": "D&B Rewards",
    "version_code": "964595",
    "version_name": "3.9.0",
    "min_sdk_version": "26",
    "target_sdk_version": "34",
    "permissions":
    [
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.USE_BIOMETRIC",
        "android.permission.USE_FINGERPRINT",
        "com.google.android.providers.gsf.permission.READ_GSERVICES",
        "android.permission.READ_USER_DICTIONARY",
        "android.permission.WRITE_USER_DICTIONARY",
        "android.permission.BLUETOOTH",
        "android.permission.BLUETOOTH_ADMIN",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.GET_PACKAGE_SIZE",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_PHONE_NUMBERS",
        "android.permission.VIBRATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.WAKE_LOCK",
        "com.google.android.c2dm.permission.RECEIVE",
        "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
        "com.google.android.gms.permission.AD_ID",
        "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
        "android.permission.ACCESS_ADSERVICES_AD_ID",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.REORDER_TASKS",
        "com.DB.playinstore.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.RECORD_AUDIO",
        "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.ACCESS_COARSE_UPDATES",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.QUERY_ALL_PACKAGES"
    ],
    "split_configs":
    [
        "config.mdpi",
        "config.en",
        "config.armeabi_v7a"
    ],
    "total_size": 111255552,
    "icon": "icon.png",
    "split_apks":
    [
        {
            "file": "com.DB.playinstore.apk",
            "id": "base"
        },
        {
            "file": "config.mdpi.apk",
            "id": "config.mdpi"
        },
        {
            "file": "config.en.apk",
            "id": "config.en"
        },
        {
            "file": "config.armeabi_v7a.apk",
            "id": "config.armeabi_v7a"
        }
    ]
}

*/
