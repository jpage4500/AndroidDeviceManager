#!/usr/bin/env bash
#
# Download the latest AndroidDeviceManager.jar from GitHub releases and run it
# in headless server mode. Safe to re-run - it only downloads when the installed
# version is out of date.
#
#   curl -fsSL https://raw.githubusercontent.com/jpage4500/AndroidDeviceManager/develop/scripts/run-headless.sh | bash -s -- --port 8765
#
set -euo pipefail

REPO="jpage4500/AndroidDeviceManager"
ASSET="AndroidDeviceManager.jar"
SELF="run-headless.sh"
INSTALL_DIR=""
TAG=""
FORCE=0
DOWNLOAD_ONLY=0
PORT=""
TOKEN=""
LOG_LEVEL=""
APP_ARGS=()

usage() {
    cat <<EOF
Usage: $SELF [options] [-- extra app args]

  -d, --dir DIR        install into DIR (default: the folder the jar is already in,
                       else \$ADM_HOME, else \$HOME/android-device-manager)
      --port PORT      port to serve on (default: 8765, or whatever was used last)
      --token TOKEN    shared secret clients authenticate with (generated if never set)
      --tag TAG        install a specific release tag (default: latest)
      --log-level LVL  log file detail: verbose, debug, info (default), warn, error
  -f, --force          re-download even if that version is already installed
      --download-only  install the jar but don't start it
  -h, --help           show this help

Set GITHUB_TOKEN to raise the API rate limit or reach a private repo.
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        -d|--dir)        INSTALL_DIR="${2:?--dir needs a directory}"; shift 2 ;;
        --port)          PORT="${2:?--port needs a port}"; shift 2 ;;
        --token)         TOKEN="${2:?--token needs a token}"; shift 2 ;;
        --tag)           TAG="${2:?--tag needs a tag}"; shift 2 ;;
        --log-level)     LOG_LEVEL="${2:?--log-level needs a level}"; shift 2 ;;
        -f|--force)      FORCE=1; shift ;;
        --download-only) DOWNLOAD_ONLY=1; shift ;;
        -h|--help)       usage; exit 0 ;;
        --)              shift; APP_ARGS+=("$@"); break ;;
        *)               echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

# install next to an existing jar so updates land where the app already runs from
if [ -z "$INSTALL_DIR" ]; then
    if [ -n "${ADM_HOME:-}" ]; then
        INSTALL_DIR="$ADM_HOME"
    else
        # piped from curl - there's no script file, so use the current directory
        if [ -n "${BASH_SOURCE[0]:-}" ] && [ -f "${BASH_SOURCE[0]}" ]; then
            SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
        else
            SCRIPT_DIR="$PWD"
        fi
        if [ -f "$SCRIPT_DIR/$ASSET" ]; then
            INSTALL_DIR="$SCRIPT_DIR"
            echo "found an existing install in $INSTALL_DIR"
        else
            INSTALL_DIR="$HOME/android-device-manager"
        fi
    fi
fi

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }

api() {
    if [ -n "${GITHUB_TOKEN:-}" ]; then
        curl -fsSL -H "Authorization: Bearer $GITHUB_TOKEN" "$@"
    else
        curl -fsSL "$@"
    fi
}

JAR="$INSTALL_DIR/$ASSET"
STAMP="$INSTALL_DIR/.version"

# resolve the release tag
if [ -z "$TAG" ]; then
    echo "looking up latest release of $REPO..."
    if JSON=$(api "https://api.github.com/repos/$REPO/releases/latest"); then
        if command -v jq >/dev/null; then
            TAG=$(printf '%s' "$JSON" | jq -r '.tag_name')
        else
            TAG=$(printf '%s' "$JSON" | grep -o '"tag_name"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
        fi
    elif [ -f "$JAR" ]; then
        # offline at boot - keep running whatever is already installed
        echo "WARNING: couldn't reach GitHub, staying on the installed version"
    else
        echo "failed to reach the GitHub API and nothing is installed yet" >&2
        exit 1
    fi
fi

if [ -n "$TAG" ] && [ "$TAG" != "null" ]; then
    if [ "$FORCE" -eq 0 ] && [ -f "$JAR" ] && [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$TAG" ]; then
        echo "$TAG is already installed at $JAR"
    else
        mkdir -p "$INSTALL_DIR"
        URL="https://github.com/$REPO/releases/download/$TAG/$ASSET"
        TMP=$(mktemp "$INSTALL_DIR/.$ASSET.XXXXXX")
        trap 'rm -f "$TMP"' EXIT

        echo "downloading $TAG..."
        api -o "$TMP" "$URL" || { echo "download failed: $URL" >&2; exit 1; }

        # a jar is a zip - anything else means we got an error page
        [ "$(head -c 2 "$TMP")" = "PK" ] || { echo "downloaded file is not a jar" >&2; exit 1; }

        mv -f "$TMP" "$JAR"
        chmod 644 "$JAR"
        trap - EXIT
        printf '%s\n' "$TAG" > "$STAMP"
        echo "installed $TAG -> $JAR"
    fi
elif [ ! -f "$JAR" ]; then
    echo "could not determine the release tag" >&2
    exit 1
fi

command -v adb >/dev/null || echo "WARNING: adb not found on PATH - install android-tools-adb"

if [ "$DOWNLOAD_ONLY" -eq 1 ]; then
    echo "run it with: java -Djava.awt.headless=true -jar $JAR --server"
    exit 0
fi

# java 17+ is required to run the jar
command -v java >/dev/null || { echo "java not found on PATH - install openjdk-17-jre-headless or newer" >&2; exit 1; }
JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
case "$JAVA_VER" in
    ''|*[!0-9]*) echo "WARNING: could not determine the java version" ;;
    *) [ "$JAVA_VER" -ge 17 ] || { echo "java $JAVA_VER found - 17 or newer is required" >&2; exit 1; } ;;
esac

[ -n "$LOG_LEVEL" ] && APP_ARGS=(--log-level "$LOG_LEVEL" "${APP_ARGS[@]+"${APP_ARGS[@]}"}")
[ -n "$PORT" ] && APP_ARGS=(--port "$PORT" "${APP_ARGS[@]+"${APP_ARGS[@]}"}")
[ -n "$TOKEN" ] && APP_ARGS=(--token "$TOKEN" "${APP_ARGS[@]+"${APP_ARGS[@]}"}")

echo "starting server (ctrl-c to stop)..."
exec java -Djava.awt.headless=true -jar "$JAR" --server "${APP_ARGS[@]+"${APP_ARGS[@]}"}"
