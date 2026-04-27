package com.jpage4500.devicemanager.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Detects whether the OS desktop is in dark or light mode, and notifies listeners on changes.
 *
 * <pre>
 * OsThemeDetector detector = OsThemeDetector.getInstance();
 * boolean dark = detector.isDark();
 * detector.registerListener(isDark -&gt; { ... });
 * </pre>
 */
public class OsThemeDetector {
    private static final Logger log = LoggerFactory.getLogger(OsThemeDetector.class);

    private static final long POLL_INTERVAL_MS = 3000;

    private static OsThemeDetector instance;

    private final CopyOnWriteArrayList<Consumer<Boolean>> listeners = new CopyOnWriteArrayList<>();
    private final Os os;
    private volatile boolean isDark;
    private volatile Thread pollThread;

    private enum Os {MAC, WINDOWS, LINUX, OTHER}

    private OsThemeDetector() {
        this.os = detectOs();
        this.isDark = detect();
    }

    public static synchronized OsThemeDetector getInstance() {
        if (instance == null) instance = new OsThemeDetector();
        return instance;
    }

    /**
     * @return true if the OS is currently in dark mode.
     */
    public boolean isDark() {
        return isDark;
    }

    /**
     * Register a listener invoked when the theme changes. The boolean argument is the new
     * dark-mode state. Callbacks fire on a background thread.
     */
    public void registerListener(Consumer<Boolean> listener) {
        if (listener == null) return;
        boolean wasEmpty = listeners.isEmpty();
        listeners.addIfAbsent(listener);
        if (wasEmpty) startPolling();
    }

    public void removeListener(Consumer<Boolean> listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) stopPolling();
    }

    private synchronized void startPolling() {
        if (pollThread != null) return;
        Thread t = new Thread(this::pollLoop, "os-theme-detector");
        t.setDaemon(true);
        pollThread = t;
        t.start();
    }

    private synchronized void stopPolling() {
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
    }

    private void pollLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            boolean current = detect();
            if (current != isDark) {
                isDark = current;
                for (Consumer<Boolean> l : listeners) {
                    try {
                        l.accept(current);
                    } catch (Exception e) {
                        log.warn("listener threw: {}", e.getMessage());
                    }
                }
            }
        }
    }

    private boolean detect() {
        switch (os) {
            case MAC:
                return detectMac();
            case WINDOWS:
                return detectWindows();
            case LINUX:
                return detectLinux();
            default:
                return false;
        }
    }

    /**
     * {@code defaults read -g AppleInterfaceStyle} exits 0 only when dark mode is on.
     */
    private boolean detectMac() {
        Result r = run(300, "defaults", "read", "-g", "AppleInterfaceStyle");
        return r != null && r.exitCode == 0;
    }

    /**
     * Registry value {@code AppsUseLightTheme} = 0 means dark mode.
     */
    private boolean detectWindows() {
        Result r = run(500,
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme");
        if (r == null || r.exitCode != 0) return false;
        // output line looks like: "    AppsUseLightTheme    REG_DWORD    0x0"
        return r.stdout.contains("0x0");
    }

    /**
     * GNOME: color-scheme = 'prefer-dark', else fall back to gtk-theme name containing "dark".
     */
    private boolean detectLinux() {
        Result r = run(500, "gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
        if (r != null && r.exitCode == 0 && r.stdout.toLowerCase(Locale.ROOT).contains("prefer-dark")) {
            return true;
        }
        Result fallback = run(500, "gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
        return fallback != null && fallback.exitCode == 0
                && fallback.stdout.toLowerCase(Locale.ROOT).contains("dark");
    }

    private static Os detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) return Os.MAC;
        if (name.contains("win")) return Os.WINDOWS;
        if (name.contains("nix") || name.contains("nux")) return Os.LINUX;
        return Os.OTHER;
    }

    private static Result run(long timeoutMs, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return new Result(p.exitValue(), out.toString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    private static final class Result {
        final int exitCode;
        final String stdout;

        Result(int exitCode, String stdout) {
            this.exitCode = exitCode;
            this.stdout = stdout;
        }
    }
}
