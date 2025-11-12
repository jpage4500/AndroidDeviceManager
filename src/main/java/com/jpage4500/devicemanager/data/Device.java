package com.jpage4500.devicemanager.data;

import com.jpage4500.devicemanager.utils.ExcludeFromSerialization;
import com.jpage4500.devicemanager.utils.TextUtils;
import se.vidstige.jadb.JadbDevice;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Device {
    // common device properties
    public final static String PROP_SDK = "ro.build.version.sdk";
    public final static String PROP_MODEL = "ro.product.model";
    public final static String PROP_OS = "ro.build.version.release";
    private final static String PROP_CARRIER = "gsm.sim.operator.alpha";
    private final static String PROP_CARRIER_2 = "gsm.operator.alpha";
    public final static String PROP_BRAND = "ro.product.brand";
    public final static String PROP_NAME = "ro.product.name";

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

    // optional status description (error message, etc)
    public String status;

    // true when device is online/ready
    public boolean isOnline;

    // true when device is fully booted
    public boolean isBooted;

    // counter of running tasks like mirroring a device; used to show a 'busy' icon
    @ExcludeFromSerialization
    private final AtomicInteger busyCounter = new AtomicInteger(0);

    // last time device was seen (online or offline)
    public Long lastUpdateMs;

    // map of property name -> key
    @ExcludeFromSerialization
    public Map<String, String> propMap;

    // custom properties (saved on a file on device)
    @ExcludeFromSerialization
    public Map<String, String> customPropertyMap;

    // user-defined map of applications and versions
    @ExcludeFromSerialization
    public Map<String, String> customAppVersionList;

    @ExcludeFromSerialization
    public JadbDevice jadbDevice;

    // Remote device properties
    public boolean isRemote = false;                    // true if from remote server
    public String remoteServerId;                       // unique ID of remote server
    public String remoteServerName;                     // display name of server
    @ExcludeFromSerialization
    public transient Object remoteConnection;           // connection instance (not serialized)

    /**
     * @return best available device name
     */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (TextUtils.notEmpty(nickname)) sb.append(nickname);
        else {
            String model = getProperty(PROP_MODEL);
            if (model != null) sb.append(model);
        }
        if (phone != null) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(phone);
        } else if (serial != null) {
            if (!sb.isEmpty()) sb.append(" - ");
            sb.append(serial);
        }
        return sb.toString();
    }

    /**
     * if device is connected via adb wireless
     */
    public boolean isWireless() {
        return serial.indexOf(':') > 0;
    }

    public String getProperty(String key) {
        if (propMap == null) return null;
        else return propMap.get(key);
    }

    /**
     * get phone carrier (ie: T-Mobile)
     */
    public String getCarrier() {
        String prop = getProperty(Device.PROP_CARRIER);
        // often we just get "," for the carrier
        prop = cleanCarrierString(prop);
        if (TextUtils.isEmpty(prop)) {
            prop = getProperty(Device.PROP_CARRIER_2);
            prop = cleanCarrierString(prop);
            if (TextUtils.isEmpty(prop)) {
                // TODO: try some other properties..
            }
        }
        return prop;
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
        customPropertyMap.put(key, value);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Device device = (Device) o;
        return Objects.equals(serial, device.serial) && Objects.equals(phone, device.phone) && Objects.equals(imei, device.imei) && Objects.equals(freeSpace, device.freeSpace) && Objects.equals(propMap, device.propMap) && Objects.equals(customPropertyMap, device.customPropertyMap) && Objects.equals(customAppVersionList, device.customAppVersionList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serial, phone, imei, freeSpace, propMap, customPropertyMap, customAppVersionList);
    }
}
