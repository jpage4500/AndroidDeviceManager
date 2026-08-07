# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Java/Swing desktop app for managing many Android devices over `adb`. Packaged for Mac/Windows/Linux via jdeploy. Tested with up to 45 devices over USB hubs.

## Build & Run

Java 17+, Maven. Entry point: `com.jpage4500.devicemanager.MainApplication`.

- `mvn compile exec:java` — run from source (or `scripts/run.sh`)
- `mvn -B package` — build fat jar to `target/AndroidDeviceManager.jar` (or `scripts/build.sh`)
- `mvn install` — also bundles a native `.app` for macOS via `macosappbundler-maven-plugin`
- `scripts/createMacApp.sh` — full Mac packaging + zip + copy to `/Applications`
- `scripts/clear-preferences.sh` — wipes saved Java `Preferences`
- `scripts/gen_release_notes.sh <type> <format> [tag]` — release notes from git log (falls back to PR titles for merge commits); CI calls `tag brief <prev-tag>`
- No test suite is set up (`package.json`'s `test` script is a stub).

CI: `.github/workflows/jdeploy.yml`. Push to `develop` → job 1 tags `1.0.<git commit count>` → the tag push triggers job 2, which builds and publishes installers to GitHub Releases via the `shannah/jdeploy` action (`deploy_target: github`, no longer npm). The tag *is* the version: job 2 passes it as `-Drevision=`, which sets `${project.version}` and lands in `app.properties`. Local builds need the same flag (`scripts/build.sh` handles it); a bare `mvn package` falls back to `1.0.0`. Job 2 then attaches the fat jar and rewrites the release body as generated notes + the installer download links jDeploy wrote (don't swap the `gh release upload` step back to `softprops/action-gh-release` — it blanks the body). Requires the `ADM_JDEPLOY_GITHUB_TOKEN` secret (a PAT with `contents: write` — the default `GITHUB_TOKEN` can't trigger the tag-push job); setup steps are in README.md under "Releasing".

## External tooling assumed on PATH

- `adb` (Android platform-tools)
- `scrcpy` (for device mirroring)

The app shells out to platform-specific scripts in `src/main/resources/scripts/` (`mirror.sh`/`.bat`, `terminal.sh`/`.bat`, `start-server.sh`/`.bat`, `record-screen.sh`/`.bat`, `run-custom.sh`, `shell.sh`). They are extracted to a temp dir at runtime; see `DeviceManager` constants `SCRIPT_*`.

## Architecture

### Packages (`com.jpage4500.devicemanager`)
- `MainApplication` — boot, FlatLaf setup, taskbar icon, file-open handler (apk/xapk drag-onto-app), creates `DeviceScreen`.
- `ui/` — Swing screens. Each top-level screen extends `BaseScreen`: `DeviceScreen` (main list), `ExploreScreen` (file browser), `ViewLogsScreen`, `SaveLogsScreen`, `InputScreen`, `MessageViewScreen`. `ui/dialog/` and `ui/views/` hold dialogs and reusable Swing widgets.
- `manager/` — non-UI business logic. **`DeviceManager`** is a singleton coordinating `adb` via the bundled jadb library, holding the device list, scheduled refresh, and per-device logging state. **`RemoteConnectionManager`** + `RemoteConnection` connect to other ADM instances; **`RemoteServerManager`** + `RemoteHttpServer` (NanoHTTPD) expose this instance's devices over HTTP so another ADM instance can use them. **`NetworkDiscoveryManager`** uses jmDNS/SSDP to find peers.
- `data/` — POJOs (`Device`, `DeviceFile`, `LogEntry`, `RemoteServerConfig`, etc.). Annotate fields with `@ExcludeFromSerialization` to keep Gson from persisting them (see `AnnotationExclusionStrategy`).
- `table/` — `TableModel`s; `table/utils/` holds matching `CellRenderer`s, `RowSorter`s, `RowFilter`s.
- `logging/` — custom SLF4J binding (`AppLoggerFactory` registered via `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`). Levels are the Android-style ints in `Log` (VERBOSE=2 … ASSERT=7). File logs go to `~/.device_manager/device_manager_log.txt`.
- `utils/` — Swing helpers, file I/O, Gson wrapper, network/UPnP helpers, `PreferenceUtils`.

### Bundled adb client (`se.vidstige.jadb`)
A vendored copy of [jadb](https://github.com/vidstige/jadb) — a pure-Java ADB client. The app talks to the local `adb` server via `JadbConnection`/`JadbDevice` rather than spawning `adb` for every command. Treat this package as a third-party dep; prefer not to modify it.

### Remote-server feature (recent area of active work)
ADM can act as both client and server. `RemoteServerManager` runs `RemoteHttpServer` on port 8765 with bearer-token auth and exposes `/api/devices`, `/api/execute`, `/api/files/list|download|upload`, `/api/screenshot`. Headers `x-client-ip` / `x-client-name` / `authorization` identify the caller. `RemoteConnectionManager` is the matching client. `UpnpUtils` does optional port forwarding; jmDNS handles LAN discovery.

### Persistence
- Java `Preferences` API via `PreferenceUtils` — keys are enums (`Pref`, `PrefBoolean`, `PrefInt`). `scripts/clear-preferences.sh` wipes them.
- `~/.device_manager/` (from `Utils.getDeviceManagerFolder()`) — log file, downloaded files, etc.

### Threading
- All Swing work goes through `SwingUtilities.invokeLater`.
- `DeviceManager` owns two pools: `commandExecutorService` (blocking shell-outs like mirror/terminal) and `scheduledExecutorService` (periodic device refresh on `DEVICE_REFRESH_MINS`).

## Conventions

- **Keep comments very concise — one line.** Say what it is, not the reasoning behind it. Drop the follow-up `NOTE:`/rationale lines; if a comment needs a paragraph, the code needs the work instead. Example — keep only the first line of:
  ```java
  // -- keep a few days of battery/disk values so they can be graphed over time --
  // NOTE: this rides along on the refresh that just ran rather than asking the device for
  // anything; the not-booted path above returns before either fetch, so it can't record blanks
  ```
- Use SLF4J: `private static final Logger log = LoggerFactory.getLogger(Foo.class);` — call `log.debug/info/warn/error` with `{}` placeholders.
- Layouts use MigLayout (`net.miginfocom.swing.MigLayout`).
- `TextUtils` mirrors Android's helper (use `isEmpty`, `equalsIgnoreCase`, etc. instead of rolling your own).
- Gson via `GsonHelper`; respect `@ExcludeFromSerialization`.
- Log filter syntax (used in `ViewLogsScreen`) is documented in `LOGS.md`.
