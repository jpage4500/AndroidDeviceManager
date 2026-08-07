package com.jpage4500.devicemanager.manager;

import com.jpage4500.devicemanager.data.Device;
import com.jpage4500.devicemanager.data.StatSample;
import com.jpage4500.devicemanager.utils.FileUtils;
import com.jpage4500.devicemanager.utils.GsonHelper;
import com.jpage4500.devicemanager.utils.PreferenceUtils;
import com.jpage4500.devicemanager.utils.TextUtils;
import com.jpage4500.devicemanager.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * a few days of battery/storage readings per device, 1 JSON object per line in
 * ~/.device_manager/device_stats.jsonl
 * <p>
 * samples piggyback on {@link DeviceManager#fetchDeviceDetails}, so the sample rate is whatever
 * PREF_REFRESH_TIME_MINS is set to (60 mins by default).
 */
public class DeviceStatsManager {
    private static final Logger log = LoggerFactory.getLogger(DeviceStatsManager.class);

    private static final String STATS_FILENAME = "device_stats.jsonl";

    // how long samples are kept; user-configurable via PREF_STATS_RETENTION_DAYS
    public static final int DEFAULT_RETENTION_DAYS = 3;
    public static final int MIN_RETENTION_DAYS = 1;
    public static final int MAX_RETENTION_DAYS = 30;

    // never record 2 samples for the same device closer together than this; a manual refresh any
    // further apart than this does record a sample
    private static final long MIN_SAMPLE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

    // rewrite the file to drop expired samples at most this often
    private static final long PRUNE_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);

    // backstop for a file that grows faster than pruning drops it (constant manual refreshing)
    private static final long MAX_FILE_SIZE = 32 * 1024 * 1024;

    private static volatile DeviceStatsManager instance;

    // guards all reads/writes of the stats file (devices refresh on a thread pool)
    private final Object fileLock = new Object();

    // serial -> time the last sample was written
    private final Map<String, Long> lastSampleMap = new HashMap<>();

    // 0 until the first prune; the file is pruned once on startup (whichever of record/load runs first)
    private long lastPruneMs;

    public static DeviceStatsManager getInstance() {
        if (instance == null) {
            synchronized (DeviceStatsManager.class) {
                if (instance == null) {
                    instance = new DeviceStatsManager();
                }
            }
        }
        return instance;
    }

    private DeviceStatsManager() {
    }

    public static File getStatsFile() {
        return new File(Utils.getDeviceManagerFolder(), STATS_FILENAME);
    }

    /**
     * record the device's current battery/storage values (rate-limited per device)
     */
    public void recordSample(Device device) {
        if (device == null || !device.isOnline || TextUtils.isEmpty(device.serial)) return;

        StatSample sample = new StatSample();
        sample.serial = device.serial;
        sample.timeMs = System.currentTimeMillis();
        sample.level = device.batteryLevel;
        if (device.batteryInfo != null) sample.tempC = device.batteryInfo.tempC;
        sample.freeSpace = device.freeSpace;
        sample.totalSpace = device.totalSpace;

        // a device that reported nothing (not booted yet, or a failed shell) isn't worth a line
        if (!sample.hasAnyValue()) return;

        synchronized (fileLock) {
            Long lastMs = lastSampleMap.get(sample.serial);
            if (lastMs != null && sample.timeMs - lastMs < MIN_SAMPLE_INTERVAL_MS) return;
            lastSampleMap.put(sample.serial, sample.timeMs);

            File file = getStatsFile();
            FileUtils.writeToFile(file, true, GsonHelper.toJson(sample) + "\n");
            log.trace("recordSample: {}: {}", device.getDisplayName(), sample);

            pruneIfNeeded(sample.timeMs);
            if (file.length() > MAX_FILE_SIZE) FileUtils.truncateFile(file);
        }
    }

    /**
     * every sample still on disk, grouped by device and oldest first
     *
     * @return serial -> samples; empty (never null) when nothing has been recorded yet
     */
    public Map<String, List<StatSample>> loadSamples() {
        synchronized (fileLock) {
            pruneIfNeeded(System.currentTimeMillis());

            Map<String, List<StatSample>> sampleMap = new HashMap<>();
            for (StatSample sample : readSamples(getExpiredBeforeMs())) {
                sampleMap.computeIfAbsent(sample.serial, key -> new ArrayList<>()).add(sample);
            }
            // the chart needs each device's points in order
            for (List<StatSample> sampleList : sampleMap.values()) {
                sampleList.sort(Comparator.comparingLong(sample -> sample.timeMs));
            }
            log.debug("loadSamples: {} devices", sampleMap.size());
            return sampleMap;
        }
    }

    /**
     * delete all recorded history
     */
    public void clearSamples() {
        synchronized (fileLock) {
            File file = getStatsFile();
            boolean isDeleted = !file.exists() || file.delete();
            lastSampleMap.clear();
            log.debug("clearSamples: {}", isDeleted);
        }
    }

    /**
     * how many days of samples are kept
     */
    public static int getRetentionDays() {
        int days = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_STATS_RETENTION_DAYS, DEFAULT_RETENTION_DAYS);
        if (days < MIN_RETENTION_DAYS) return MIN_RETENTION_DAYS;
        else if (days > MAX_RETENTION_DAYS) return MAX_RETENTION_DAYS;
        return days;
    }

    /**
     * how far apart samples are expected to land, which is just how often devices are refreshed
     */
    public static long getSampleIntervalMs() {
        int refreshMins = PreferenceUtils.getPreference(PreferenceUtils.PrefInt.PREF_REFRESH_TIME_MINS, DeviceManager.DEVICE_REFRESH_MINS);
        return TimeUnit.MINUTES.toMillis(refreshMins);
    }

    private static long getExpiredBeforeMs() {
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(getRetentionDays());
    }

    /**
     * NOTE: caller holds fileLock
     */
    private void pruneIfNeeded(long nowMs) {
        if (nowMs - lastPruneMs < PRUNE_INTERVAL_MS) return;
        lastPruneMs = nowMs;

        File file = getStatsFile();
        if (!file.exists()) return;

        long expiredBeforeMs = getExpiredBeforeMs();
        List<StatSample> keepList = readSamples(expiredBeforeMs);

        StringBuilder sb = new StringBuilder();
        for (StatSample sample : keepList) {
            sb.append(GsonHelper.toJson(sample));
            sb.append('\n');
        }
        FileUtils.writeToFile(file, false, sb.toString());
        log.debug("pruneIfNeeded: kept {} samples, {} bytes", keepList.size(), file.length());
    }

    /**
     * read every sample recorded at or after the given time (NOTE: caller holds fileLock)
     * <p>
     * a line that won't parse is skipped; killing the app mid-append leaves a partial line behind
     */
    private List<StatSample> readSamples(long fromMs) {
        List<StatSample> sampleList = new ArrayList<>();
        String contents = FileUtils.readFile(getStatsFile());
        if (TextUtils.isEmpty(contents)) return sampleList;

        int numBad = 0;
        for (String line : contents.split("\n")) {
            if (TextUtils.isEmpty(line.trim())) continue;
            StatSample sample = GsonHelper.fromJson(line, StatSample.class);
            if (sample == null || TextUtils.isEmpty(sample.serial) || sample.timeMs <= 0) {
                numBad++;
                continue;
            }
            if (sample.timeMs < fromMs) continue;
            sampleList.add(sample);
        }
        if (numBad > 0) log.debug("readSamples: skipped {} bad lines", numBad);
        return sampleList;
    }
}
