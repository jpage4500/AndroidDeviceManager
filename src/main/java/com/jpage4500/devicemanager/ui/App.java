package com.jpage4500.devicemanager.ui;

import com.jpage4500.devicemanager.data.Device;

import java.io.File;
import java.util.List;

/**
 * Implemented by AppController (which owns app-lifecycle: ADB, tray, update checks, child windows).
 */
public interface App {

    // ------------------------------------------------------------------
    // navigation — used by menu items in every screen
    // ------------------------------------------------------------------
    void showDeviceList();

    void showLogs(Device device);

    void showFileBrowser(Device device);

    void showSaveLogs(List<Device> devices);

    void showInput(Device device);

    // ------------------------------------------------------------------
    // cleanup callbacks — fired by screens from their close handlers
    // ------------------------------------------------------------------
    void onLogsClosed(String serial);

    void onBrowseClosed(String serial);

    void onInputClosed(String serial);

    void onSaveLogsClosed();

    // ------------------------------------------------------------------
    // device state
    // ------------------------------------------------------------------
    void setDeviceBusy(Device device, boolean isBusy);

    // ------------------------------------------------------------------
    // settings hooks (used by SettingsDialog so it doesn't reach into DeviceScreen)
    // ------------------------------------------------------------------
    void scheduleUpdateChecks();

    /**
     * soft refresh — fire table data changed on the device list
     */
    void refreshDeviceListView();

    /**
     * full rebuild of the device table (column setup + repopulate)
     */
    void rebuildDeviceTable();

    /**
     * restore device table layout from saved preferences
     */
    void restoreDeviceTable();

    /**
     * rebuild the device-list toolbar (after a hidden-button toggle)
     */
    void rebuildDeviceToolbar();

    /**
     * custom columns list changed — refresh device data
     */
    void notifyCustomColumnsChanged();

    // ------------------------------------------------------------------
    // file-drop forwarding (apk drag onto app icon)
    // ------------------------------------------------------------------
    void handleFilesOpened(List<File> files);

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------
    void exit(boolean forceQuit);

    /**
     * true when no DeviceScreen is shown (e.g., logs-only launch mode)
     */
    boolean isHeadlessMode();
}
