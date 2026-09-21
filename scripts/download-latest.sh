#!/usr/bin/env bash
#
# Download the latest AndroidDeviceManager.jar from GitHub releases.
# Intended for headless servers - the jar runs in server mode; the jDeploy
# installers on the same release page are for desktop use.
#
#   ./download-latest.sh [-d DIR] [-t TAG] [-f]
#
set -euo pipefail

REPO="jpage4500/AndroidDeviceManager"
ASSET="AndroidDeviceManager.jar"
INSTALL_DIR="${ADM_HOME:-$HOME/android-device-manager}"
TAG=""
FORCE=0

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

  -d, --dir DIR    install into DIR (default: $INSTALL_DIR, or \$ADM_HOME)
  -t, --tag TAG    download a specific release tag (default: latest)
  -f, --force      re-download even if that version is already installed
  -h, --help       show this help

Set GITHUB_TOKEN to raise the API rate limit or reach a private repo.
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        -d|--dir)   INSTALL_DIR="${2:?--dir needs a directory}"; shift 2 ;;
        -t|--tag)   TAG="${2:?--tag needs a tag}"; shift 2 ;;
        -f|--force) FORCE=1; shift ;;
        -h|--help)  usage; exit 0 ;;
        *)          echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
done

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }

api() {
    if [ -n "${GITHUB_TOKEN:-}" ]; then
        curl -fsSL -H "Authorization: Bearer $GITHUB_TOKEN" "$@"
    else
        curl -fsSL "$@"
    fi
}

# resolve the release tag
if [ -z "$TAG" ]; then
    echo "looking up latest release of $REPO..."
    JSON=$(api "https://api.github.com/repos/$REPO/releases/latest") \
        || { echo "failed to reach the GitHub API" >&2; exit 1; }
    if command -v jq >/dev/null; then
        TAG=$(printf '%s' "$JSON" | jq -r '.tag_name')
    else
        TAG=$(printf '%s' "$JSON" | grep -o '"tag_name"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
    fi
fi
[ -n "$TAG" ] && [ "$TAG" != "null" ] || { echo "could not determine the release tag" >&2; exit 1; }

JAR="$INSTALL_DIR/$ASSET"
STAMP="$INSTALL_DIR/.version"

# already have it?
if [ "$FORCE" -eq 0 ] && [ -f "$JAR" ] && [ -f "$STAMP" ] && [ "$(cat "$STAMP")" = "$TAG" ]; then
    echo "$TAG is already installed at $JAR (use --force to re-download)"
    exit 0
fi

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

# warn about anything the server needs but doesn't have
command -v adb >/dev/null || echo "WARNING: adb not found on PATH - install android-tools-adb"
if command -v java >/dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*version "\([0-9]*\).*/\1/')
    case "$JAVA_VER" in
        ''|*[!0-9]*) ;;
        *) [ "$JAVA_VER" -ge 17 ] || echo "WARNING: java $JAVA_VER found - 17 or newer is required" ;;
    esac
else
    echo "WARNING: java not found on PATH - install openjdk-17-jre-headless or newer"
fi

cat <<EOF

Run it:
  java -Djava.awt.headless=true -jar $JAR --server --port 8765 --token <shared-secret>

The port and token are saved after the first run, so later starts need only --server.
The token is printed to the log along with an adm:// connection string for each network.
EOF
