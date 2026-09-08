# Android Device Manager — Server API

Reference for the HTTP + WebSocket API exposed by Android Device Manager (ADM) when it shares its
locally-attached devices. ADM's own client (`RemoteConnection`) speaks this API, and so can anything
else that can make HTTP requests.

- API version: **1.0** (`RemoteHttpServer.VERSION`)
- Default port: **8765** (`RemoteServerManager.DEFAULT_PORT`)
- Implemented by [`RemoteHttpServer`](../src/main/java/com/jpage4500/devicemanager/manager/server/RemoteHttpServer.java) (NanoHTTPD/NanoWSD)
- Reference client: [`RemoteConnection`](../src/main/java/com/jpage4500/devicemanager/manager/client/RemoteConnection.java)

For how to start a server and connect to it from the ADM UI, see [SERVER.md](../SERVER.md) and
[CLIENT_SERVER.md](../CLIENT_SERVER.md).

---

## Contents

- [Connecting](#connecting)
- [Authentication](#authentication)
- [Conventions](#conventions)
- [HTTP endpoints](#http-endpoints)
- [WebSocket: `/ws/logs`](#websocket-wslogs)
- [WebSocket: `/ws/screen`](#websocket-wsscreen)
- [Data types](#data-types)
- [Running a server](#running-a-server)
- [Gotchas](#gotchas)

---

## Connecting

| | |
|---|---|
| Base URL | `http://<host>:<port>` |
| WebSocket URL | `ws://<host>:<port>` |
| TLS | not provided by the server — put it behind a tunnel (Tailscale, ngrok) or a reverse proxy |
| Socket timeout | 60s, long enough to keep WebSockets alive between messages |

The server has no TLS and no user accounts: a single shared token is the only thing protecting it.
Treat it as a LAN/VPN service.

### Connection strings

The UI's "Copy Connection String" button produces a single string that carries everything a client
needs:

```
adm://<base64 of {"host":"1.2.3.4","port":8765,"token":"aBc123...","name":"joes-mac"}>
```

Base64 of a compact JSON object, prefixed with `adm://`. See `RemoteConnectionUtils.generateConnectionString()` /
`parseConnectionString()`. The same string is what the Share Devices dialog renders as a QR code.

---

## Authentication

Every HTTP request and every WebSocket upgrade must present the server's auth token. Two ways,
checked in this order:

1. **Query parameter** — `?token=<token>` (this wins if present)
2. **Header** — `Authorization: Bearer <token>`

Comparison is exact string equality. On failure:

- HTTP → `401` with `Unauthorized` as `text/plain`
- WebSocket → the upgrade *succeeds*, then the server immediately sends
  `{"type":"error","message":"Unauthorized"}` and closes with `1008` (PolicyViolation)

Use the header for HTTP. Browsers and most WebSocket clients can't set headers on an upgrade, which
is why `token` is accepted in the query string — but note it will then show up in proxy and server
logs.

### Optional client identification

Sent by ADM clients on every request; used only for the server's "connected clients" list.

| Header | Purpose |
|---|---|
| `x-client-ip` | client's own view of its IP; fallback when the server can't read the socket address |
| `x-client-name` | display name, usually the client's hostname |

Both are trimmed, stripped of CR/LF and truncated to 128 characters.

---

## Conventions

- **Device selection** — every device-scoped call takes a `serial` (query parameter, or a field in
  the JSON body for POSTs). It must be a serial the server reports from `/api/devices`.
- **JSON** — responses are `application/json`, serialized with Gson. **Null fields are omitted**, so
  clients must treat every non-primitive field as optional.
- **Errors** — non-200 responses are `text/plain` with a short message, not JSON.

| Status | Meaning |
|---|---|
| `200` | success |
| `400` | missing/invalid parameters, or an install that failed on at least one device |
| `401` | bad or missing token |
| `404` | unknown endpoint, unknown `serial`, or a device-side error (e.g. `Error: permission denied`) |
| `500` | unhandled exception; message is the exception text |

---

## HTTP endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | [`/api/info`](#get-apiinfo) | server name, version, device count |
| GET | [`/api/devices`](#get-apidevices) | list devices |
| GET | [`/api/properties`](#get-apiproperties) | full `getprop` map for a device |
| POST | [`/api/setproperty`](#post-apisetproperty) | set an ADM custom property |
| POST | [`/api/execute`](#post-apiexecute) | run a shell command |
| POST | [`/api/install`](#post-apiinstall) | install an APK on one or more devices |
| GET | [`/api/files/list`](#get-apifileslist) | list a directory |
| GET | [`/api/files/download`](#get-apifilesdownload) | download a file |
| POST | [`/api/files/upload`](#post-apifilesupload) | upload a file |
| GET | [`/api/screenshot`](#get-apiscreenshot) | capture a PNG screenshot |

---

### GET `/api/info`

Health check and handshake. ADM clients poll this every 30 seconds and only re-fetch the device list
when `deviceCount` changes.

```bash
curl -H "Authorization: Bearer $TOKEN" http://HOST:8765/api/info
```

```json
{
  "version": "1.0",
  "deviceCount": 3,
  "serverName": "joes-mac"
}
```

`serverName` is the user-configured server name, falling back to the machine's hostname.

---

### GET `/api/devices`

All devices physically attached to the server, as a JSON array of [Device](#device) objects.

Devices the server itself reached through *another* ADM server are filtered out, so servers don't
re-advertise each other's devices.

```bash
curl -H "Authorization: Bearer $TOKEN" http://HOST:8765/api/devices
```

---

### GET `/api/properties`

Every Android system property for a device — the equivalent of `adb shell getprop`, returned as a
flat JSON object.

| Parameter | Required | Notes |
|---|---|---|
| `serial` | yes | |

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://HOST:8765/api/properties?serial=39121FDJG003TR"
```

```json
{
  "ro.product.model": "Pixel 7",
  "ro.build.version.release": "14",
  "ro.build.version.sdk": "34"
}
```

Returns `404` if the device is gone or the property read failed.

---

### POST `/api/setproperty`

Sets an **ADM custom property** — a user-defined key/value that ADM stores in
`/sdcard/android_device_manager.properties` on the device and shows in custom columns.

> This is **not** `adb shell setprop`. It does not touch Android system properties. Use
> [`/api/execute`](#post-apiexecute) for those.

Body (`application/json`), all fields required:

```json
{ "serial": "39121FDJG003TR", "key": "team", "value": "lab-a" }
```

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"serial":"39121FDJG003TR","key":"team","value":"lab-a"}' \
  http://HOST:8765/api/setproperty
```

```json
{ "success": true }
```

`400` if any field is missing. `success: false` means the write to the device failed.

---

### POST `/api/execute`

Runs a command in the device's shell and returns its output.

Body (`application/json`):

```json
{ "serial": "39121FDJG003TR", "command": "dumpsys battery" }
```

`command` is the command **as you would type it after `adb shell`** — no `adb` and no `shell`
prefix. It is split on whitespace, honouring single and double quotes, so
`input text "hello world"` arrives as one argument.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"serial":"39121FDJG003TR","command":"getprop ro.product.model"}' \
  http://HOST:8765/api/execute
```

```json
{
  "isSuccess": true,
  "resultList": ["Pixel 7"]
}
```

`isSuccess` reports whether ADM could *run* the command, not whether the command succeeded — a
command that exits non-zero still returns `isSuccess: true` with the error text in `resultList`.
Check the output when that distinction matters.

Returns `400` if `serial` or `command` is missing, `404` if the device is unknown or is itself a
remote device.

---

### POST `/api/install`

Installs an APK. This is the one endpoint that accepts multiple devices — repeat `serial` to install
the same APK everywhere in one request.

| Parameter | Required | Notes |
|---|---|---|
| `serial` | yes | repeatable: `?serial=A&serial=B` |

The APK is the request body, either as a `multipart/form-data` part named `file` (what ADM's client
sends) or as the raw body.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@app-debug.apk" \
  "http://HOST:8765/api/install?serial=A&serial=B"
```

Response is `text/plain`, one entry per device:

```
✅ 39121FDJG003TR, ✅ R5CT10ABCDE
```

`200` when every install succeeded, `400` when any of them failed — **use the status code, not the
emoji**, to decide (see [Gotchas](#gotchas)).

---

### GET `/api/files/list`

Lists a directory on the device (`ls -alZ` under the hood).

| Parameter | Required | Notes |
|---|---|---|
| `serial` | yes | |
| `path` | no | URL-encoded; omitted or empty lists `/` |

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://HOST:8765/api/files/list?serial=39121FDJG003TR&path=%2Fsdcard"
```

Returns a JSON array of [DeviceFile](#devicefile). On a device-side failure the response is `404`
with `Error: permission denied` or `Error: Not a directory`.

Root (`su`) listings are not available through the API.

---

### GET `/api/files/download`

Pulls one file off the device.

| Parameter | Required | Notes |
|---|---|---|
| `serial` | yes | |
| `path` | yes | directory holding the file, URL-encoded |
| `file` | yes | filename, URL-encoded |

```bash
curl -H "Authorization: Bearer $TOKEN" -OJ \
  "http://HOST:8765/api/files/download?serial=39121FDJG003TR&path=%2Fsdcard&file=trace.txt"
```

Responds with the file bytes, a MIME type guessed from the extension, and a
`Content-Disposition: attachment; filename="..."` header.

Directories aren't supported. A missing or zero-length file comes back as `404 File not found on device`.

---

### POST `/api/files/upload`

Pushes a file to the device.

| Parameter | Required | Notes |
|---|---|---|
| `serial` | yes | |
| `path` | yes | destination directory, URL-encoded |
| `file` | yes | destination filename, URL-encoded |

Body is the file content — `multipart/form-data` part named `file`, or the raw body.

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@config.json" \
  "http://HOST:8765/api/files/upload?serial=39121FDJG003TR&path=%2Fsdcard&file=config.json"
```

```json
{
  "success": true,
  "message": "File uploaded successfully",
  "path": "/sdcard/config.json"
}
```

`400 No file data received` if the body was empty.

---

### GET `/api/screenshot`

Wakes the device, captures the screen and returns a full-resolution PNG.

| Parameter | Required | Notes |
|---|---|---|
| `serial` | yes | |

```bash
curl -H "Authorization: Bearer $TOKEN" -o shot.png \
  "http://HOST:8765/api/screenshot?serial=39121FDJG003TR"
```

Responds `image/png` with `Content-Disposition: attachment; filename="screenshot_<serial>.png"`.

For anything more than the occasional frame, use [`/ws/screen`](#websocket-wsscreen) — it reuses one
connection and can scale and JPEG-compress frames.

---

## WebSocket: `/ws/logs`

Live logcat for one device, filtered server-side.

```
ws://HOST:8765/ws/logs?token=<token>&serial=<serial>&filter=<filter>
```

| Parameter | Required | Notes |
|---|---|---|
| `token` | yes | unless sent as an `Authorization` header |
| `serial` | yes | |
| `filter` | no | URL-encoded filter expression; syntax in [LOGS.md](../LOGS.md) |

If the device is unknown or the filter doesn't parse, the socket opens and is then closed with
`1008` after an `{"type":"error"}` message.

### Server → client

**Log batches are binary frames**: gzip-compressed UTF-8 JSON containing an array of formatted log
lines. Batches are flushed every 500ms, and only when there's something to send.

```
gunzip → ["09-08 14:03:54.308 1234 1300 D MyTag: hello", ...]
```

Each line is `date pid tid level tag: message`.

**Everything else is a JSON text frame** with a `type` field:

| `type` | Payload | Meaning |
|---|---|---|
| `connected` | `device`, `message` | stream is live |
| `processMap` | `map` — pid → process name | lets a client resolve pids to app names |
| `status` | `state`: `paused` \| `resumed` \| `filter_updated` (+ `filter`) | acknowledges a control message |
| `error` | `message` | filter error, device error, etc. |
| `pong` | `timestamp` | reply to a client `ping` action |

The server also sends a WebSocket-level ping every 30 seconds; standard clients answer it
automatically.

### Client → server

JSON text frames with an `action`:

```json
{"action": "pause"}
{"action": "resume"}
{"action": "filter", "filterText": "level:E && tag:*MyTag*"}
{"action": "ping"}
```

`pause` stops logcat on the device entirely (not just the sending), so nothing is buffered while
paused. `filter` with an empty or missing `filterText` clears the filter. Close the socket normally
to stop.

---

## WebSocket: `/ws/screen`

Mirrors a device as a stream of still images, and carries input back the other way. This is what the
built-in mirror window uses for remote devices instead of `scrcpy`.

```
ws://HOST:8765/ws/screen?token=<token>&serial=<serial>&compress=true
```

| Parameter | Required | Notes |
|---|---|---|
| `token` | yes | unless sent as an `Authorization` header |
| `serial` | yes | |
| `compress` | no | `true` → JPEG, anything else → PNG (default) |

The device is woken on connect. On disconnect the server clears the screen stay-on setting it used.

### Frame format (binary)

Every frame is one binary message:

```
┌────────────┬──────────────────┬───────────────────┐
│ 4 bytes    │ headerLen bytes  │ rest of message   │
│ headerLen  │ header JSON      │ image bytes       │
│ (int32 BE) │ (UTF-8)          │ (PNG or JPEG)     │
└────────────┴──────────────────┴───────────────────┘
```

Header:

```json
{
  "type": "frame",
  "frameId": 42,
  "width": 1080,
  "height": 2400,
  "timestamp": 1757352000000,
  "format": "jpeg",
  "size": 84213
}
```

`width`/`height` are the **device's** dimensions. At `medium` or `low` quality the transmitted image
is smaller than that — scale input coordinates against the header values, not the decoded bitmap.

### Status and errors (text)

```json
{"type": "status", "status": "connected", "message": "Screen stream started"}
{"type": "error",  "error": "Capture error: ..."}
```

`status` values: `connected`, `paused`, `resumed`, `interval_changed`, `compression_changed`,
`quality_changed`.

### Control messages (client → server)

```json
{"action": "pause"}
{"action": "resume"}
{"action": "close"}
{"action": "setInterval",    "intervalMs": 500}
{"action": "setCompression", "useCompression": true}
{"action": "setQuality",     "quality": "medium"}
```

- `setInterval` — capture interval, **50–5000ms**; out of range gets an `error` message and is
  ignored. Default is **250ms**.
- `setQuality` — `high` | `medium` | `low`:

  | quality | scale | JPEG quality |
  |---|---|---|
  | `high` | 1.0 | 0.70 |
  | `medium` | 0.75 | 0.50 |
  | `low` | 0.5 | 0.30 |

  Scale applies to PNG as well; the JPEG quality column only matters when compression is on.

### Input messages (client → server)

All use `"action": "input"` plus a `type`:

```json
{"action": "input", "type": "tap",      "x": 540, "y": 1200}
{"action": "input", "type": "swipe",    "x1": 540, "y1": 1800, "x2": 540, "y2": 600, "duration": 300}
{"action": "input", "type": "text",     "text": "hello world"}
{"action": "input", "type": "keyevent", "keycode": 4}
```

Coordinates are in device pixels. `duration` is optional (default 300ms). `keycode` is an Android
`KEYCODE_*` integer (4 = BACK, 3 = HOME). Text is passed to `input text`, with spaces escaped as
`%s` by the server — send the plain string.

Input is fire-and-forget: the server doesn't acknowledge it, and errors come back as `error`
messages.

---

## Data types

### Device

Serialized from [`Device`](../src/main/java/com/jpage4500/devicemanager/data/Device.java). Null
fields are omitted.

```json
{
  "serial": "39121FDJG003TR",
  "nickname": "lab phone 3",
  "phone": "+15555550123",
  "imei": "350000000000000",
  "freeSpace": 51539607552,
  "totalSpace": 128849018880,
  "batteryLevel": 87,
  "powerStatus": "POWER_AC",
  "batteryInfo": {
    "level": 87,
    "tempC": 29.4,
    "voltageMv": 4210,
    "currentUa": -152000,
    "powerStatus": "POWER_AC"
  },
  "model": "Pixel 7",
  "os": "14",
  "sdk": "34",
  "carrier": "Verizon",
  "timezone": "America/Chicago",
  "status": null,
  "isOnline": true,
  "isBooted": true,
  "lastUpdateMs": 1757352000000,
  "bootTimeMs": 1757300000000,
  "customPropertyMap": { "team": "lab-a" },
  "customAppVersionList": { "com.example.app": "1.4.2" }
}
```

| Field | Type | Notes |
|---|---|---|
| `serial` | string | the device id used by every other endpoint |
| `nickname` | string | user-set name, stored on the device |
| `phone`, `imei` | string | when available |
| `freeSpace`, `totalSpace` | long | bytes |
| `batteryLevel` | int | 0–100 |
| `powerStatus` | enum | `POWER_NONE`, `POWER_AC`, `POWER_USB`, `POWER_WIRELESS`, `POWER_DOCK` |
| `batteryInfo` | object | latest temperature/voltage/current reading |
| `model`, `os`, `sdk`, `carrier`, `timezone` | string | from `getprop` |
| `status` | string | error text when something's wrong |
| `isOnline`, `isBooted` | bool | always present |
| `lastUpdateMs`, `bootTimeMs` | long | host epoch millis |
| `customPropertyMap` | map | ADM custom properties (see [`/api/setproperty`](#post-apisetproperty)) |
| `customAppVersionList` | map | package → version, for the app-version columns |

### DeviceFile

```json
{
  "name": "trace.txt",
  "size": 20481,
  "dateMs": 1757352000000,
  "permissions": "-rw-rw----",
  "user": "shell",
  "group": "sdcard_rw",
  "security": "u:object_r:sdcardfs:s0",
  "isDirectory": false,
  "isSymbolicLink": false,
  "isReadOnly": false
}
```

### ShellResult

```json
{ "isSuccess": true, "resultList": ["line 1", "line 2"] }
```

### ServerInfo

```json
{ "version": "1.0", "deviceCount": 3, "serverName": "joes-mac" }
```

---

## Running a server

**From the UI** — SERVER toolbar button → "Start Server". Port and auth token are editable before
starting, and both are remembered, so the server restarts automatically next launch.

**Headless** — useful for a dedicated device host:

```bash
java -jar AndroidDeviceManager.jar --server --port 8765 --token "my-secret-token"
```

- `--port` and `--token` are optional; both are saved to preferences and reused next time.
- Without a saved token, one is generated (16 random alphanumeric characters).
- On start the log prints the token, the port and an `adm://` connection string per network interface.
- ADM also switches to server mode automatically when it can't reach a display.

The server needs `adb` on `PATH` like any other ADM instance.

---

## Gotchas

Behaviour worth knowing before you build against this.

- **`intervalMs` in the `/ws/screen` URL is ignored.** The server reads only `serial` and `compress`
  at handshake, so a stream always starts at 250ms. Send `setInterval` once connected. (ADM's own
  client passes it in the URL and it has no effect.)
- **`/api/install` markers are unreliable.** The ✅/❌ per device is written before that device's
  result is known, so it reflects the run so far rather than that device. The HTTP status is
  accurate: `200` = all succeeded, `400` = at least one failed.
- **`/api/setproperty` is not `setprop`.** It writes an ADM custom property to a file on the device.
- **Rejections arrive after the WebSocket opens.** Bad token, unknown device and unknown endpoint all
  complete the upgrade first, then send an `error` message and close with `1008`. Don't treat "opened
  then closed immediately" as a transport problem.
- **Device-scoped GETs don't refuse proxied devices.** `/api/execute` and `/api/setproperty` return
  `404` for a device the server itself reached remotely, but `/api/properties`, the file endpoints,
  `/api/screenshot` and `/ws/logs` will chain the request onward (they only log a warning). Since
  `/api/devices` never lists those serials, this only comes up if a client supplies one itself.
- **The log stream's `logs`/`entries` JSON message doesn't exist.** ADM's client can parse it, but
  the current server always sends batches as gzipped binary. Implement the binary path.
- **A `token` in a query string ends up in logs.** Prefer the `Authorization` header wherever your
  client can set one.
