package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.manager.RemoteConnection;
import com.jpage4500.devicemanager.utils.ExcludeFromSerialization;
import com.jpage4500.devicemanager.utils.TextUtils;
import se.vidstige.jadb.JadbDevice;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Device {
    // common device properties
    private final static String PROP_SDK = "ro.build.version.sdk";
    private final static String PROP_MODEL = "ro.product.model";
    private final static String PROP_OS = "ro.build.version.release";
    private final static String PROP_CARRIER = "gsm.sim.operator.alpha";
    private final static String PROP_CARRIER_2 = "gsm.operator.alpha";
    private final static String PROP_BRAND = "ro.product.brand";
    private final static String PROP_NAME = "ro.product.name";

    public final static String CUSTOM_PROP_X = "custom";
    public final static String CUST_PROP_1 = "custom1";
    public final static String CUST_PROP_2 = "custom2";
    public final static String CUST_PROP_PHONE = "phone_number";

    public enum PowerStatus {
        POWER_NONE,
        POWER_AC,
        POWER_USB,
        POWER_WIRELESS,
        POWER_DOCK,
    }

    public String serial;
    public String nickname;
    public String phone;
    public String imei;
    public Long freeSpace;
    public Integer batteryLevel;
    public PowerStatus powerStatus = PowerStatus.POWER_NONE;
    public String model;
    public String os;
    public String sdk;
    public String carrier;

    // optional status description (error message, etc)
    public String status;

    // true when device is online/ready
    public boolean isOnline;

    // true when device is fully booted
    public boolean isBooted;

    // last time device was seen (online or offline)
    public Long lastUpdateMs;

    // custom properties (saved on a file on device)
    public Map<String, String> customPropertyMap;

    // user-defined map of applications and versions
    public Map<String, String> customAppVersionList;

    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------

    // counter of running tasks like mirroring a device; used to show a 'busy' icon
    @ExcludeFromSerialization
    private final AtomicInteger busyCounter = new AtomicInteger(0);

    @ExcludeFromSerialization
    public JadbDevice jadbDevice;

    // set if device is part of a remote server
    @ExcludeFromSerialization
    public RemoteConnection remoteConnection;

    /**
     * @return best available device name
     */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (remoteConnection != null) {
            sb.append(remoteConnection.getServerConfig().name);
        }

        if (!sb.isEmpty()) sb.append(" - ");
        sb.append(getName());

        if (phone != null) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(phone);
        } else if (serial != null) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(serial);
        }
        return sb.toString();
    }

    public String getName() {
        if (TextUtils.notEmpty(nickname)) {
            return nickname;
        } else if (model != null) {
            return model;
        } else {
            // TODO: better fallback?
            return "";
        }
    }

    /**
     * if device is connected via adb wireless
     */
    public boolean isWireless() {
        return serial.indexOf(':') > 0;
    }

    /**
     * parse all device properties to pick out any we're interested in saving
     */
    public void parseProperties(Map<String, String> propMap) {
        // model (SM-2389)
        model = propMap.get(PROP_MODEL);
        // sdk (29)
        sdk = propMap.get(PROP_SDK);
        // os (14)
        os = propMap.get(PROP_OS);

        // carrier
        carrier = propMap.get(Device.PROP_CARRIER);
        // often we just get "," for the carrier
        carrier = cleanCarrierString(carrier);
        if (TextUtils.isEmpty(carrier)) {
            carrier = propMap.get(Device.PROP_CARRIER_2);
            carrier = cleanCarrierString(carrier);
        }
    }

    /**
     * often we just get "," for the carrier or "T-Mobile,"
     * - remove the comma
     */
    private String cleanCarrierString(String carrier) {
        int pos = TextUtils.indexOf(carrier, ",");
        if (pos == 0) return null;
        else if (pos > 0) return carrier.substring(0, pos);
        return carrier;
    }

    public String getCustomProperty(String key) {
        if (customPropertyMap == null) return null;
        else return customPropertyMap.get(key);
    }

    public void setCustomProperty(String key, String value) {
        if (customPropertyMap == null) customPropertyMap = new HashMap<>();
        if (TextUtils.isEmpty(value)) customPropertyMap.remove(key);
        else customPropertyMap.put(key, value);
    }

    public boolean isBusy() {
        return busyCounter.get() > 0;
    }

    public int getBusyCount() {
        return busyCounter.get();
    }

    /**
     * set device to BUSY state
     *
     * @return true if device is BUSY
     */
    public boolean setBusy(boolean isBusy) {
        int newValue;
        if (isBusy) newValue = busyCounter.incrementAndGet();
        else newValue = busyCounter.decrementAndGet();
        // safety-check
        if (newValue < 0) {
            newValue = 0;
            busyCounter.set(0);
        }
        return newValue > 0;
    }

    /**
     * @return an icon to represent this device
     */
    public Icons getDeviceIcon() {
        // TODO: if local device but connected via adb wireless (serial = 192.168.0.100:5555), use a different icon
        return remoteConnection != null ? Icons.DEVICE_REMOTE : Icons.DEVICE_LOCAL;
    }

    /**
     * @return color to use for this device
     */
    public Color getDeviceColor(boolean supportBusy) {
        if (!isOnline) return Colors.COLOR_OFFLINE;
        else if (supportBusy && isBusy()) return Colors.COLOR_BUSY;
        else if (remoteConnection != null) return new Color(remoteConnection.getServerConfig().color);
        return Colors.COLOR_ONLINE;
    }

}
