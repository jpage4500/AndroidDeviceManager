#!/bin/bash
# install Android Device Manager on a mac where the jdeploy launcher can't verify github's TLS
# builds a self-contained .app that runs the release jar with a local JRE - no network at launch

set -o pipefail

REPO="jpage4500/AndroidDeviceManager"
APP_NAME="Android Device Manager"
# deliberately not the jdeploy bundle's name - this must never overwrite that install
BUNDLE_NAME="Android Device Manager (local)"
PKG_NAME="android-device-manager"
MAIN_CLASS_PATH='com/jpage4500/devicemanager/MainApplication.class'
DEST_DIR="$HOME/Applications"
LOG_FILE="$HOME/Library/Logs/AndroidDeviceManager-local.log"

TAG=""
LOCAL_JAR=""
SEED_JDEPLOY=0
DO_UNINSTALL=0

usage() {
    cat <<EOF
usage: ./$(basename "$0") [options]

  --version <tag>   install a specific release tag (default: newest release)
  --jar <path>      use a jar you already have instead of downloading
  --dest <dir>      where to create the .app (default: $DEST_DIR)
  --seed-jdeploy    also try to populate jdeploy's cache so the original .app works
  --uninstall       remove the .app this script created
  -h, --help        this message

Re-run any time to update to the newest release.
EOF
}

say() { printf '\033[1m==\033[0m %s\n' "$1"; }
die() { printf '\n!! %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
    case "$1" in
        --version) TAG="$2"; shift 2 ;;
        --jar) LOCAL_JAR="$2"; shift 2 ;;
        --dest) DEST_DIR="$2"; shift 2 ;;
        --seed-jdeploy) SEED_JDEPLOY=1; shift ;;
        --uninstall) DO_UNINSTALL=1; shift ;;
        -h | --help) usage; exit 0 ;;
        *) usage; exit 1 ;;
    esac
done

APP_DIR="$DEST_DIR/$BUNDLE_NAME.app"

# only ever delete a bundle this script made
assert_ours() {
    [ -d "$1" ] || return 0
    [ -f "$1/Contents/MacOS/run" ] && return 0
    die "$1 was not created by this script - refusing to touch it"
}

# --- uninstall ---------------------------------------------------------------

if [ $DO_UNINSTALL -eq 1 ]; then
    [ -d "$APP_DIR" ] || die "nothing installed at $APP_DIR"
    assert_ours "$APP_DIR"
    rm -rf "$APP_DIR" || die "could not remove $APP_DIR"
    say "removed $APP_DIR"
    exit 0
fi

# --- find a java 17+ ---------------------------------------------------------

java_major() {
    "$1" -version 2>&1 | head -1 |
        sed -n 's/.*version "\([0-9][0-9]*\)\..*/\1/p;s/.*version "1\.\([0-9][0-9]*\).*/\1/p' | head -1
}

find_java() {
    local host_arch="$(uname -m)" c m
    # a JRE jdeploy already downloaded, matching this mac's arch
    for c in $(find "$HOME/.jdeploy" -type f -name java -perm -u+x 2>/dev/null | sort -r); do
        case "$(lipo -archs "$c" 2>/dev/null)" in
            *"$host_arch"*) ;;
            *) continue ;;
        esac
        m=$(java_major "$c")
        [ -n "$m" ] && [ "$m" -ge 17 ] 2>/dev/null && { echo "$c"; return; }
    done
    for c in "$(/usr/libexec/java_home -v 17 2>/dev/null)/bin/java" "$(command -v java)"; do
        [ -x "$c" ] || continue
        m=$(java_major "$c")
        [ -n "$m" ] && [ "$m" -ge 17 ] 2>/dev/null && { echo "$c"; return; }
    done
}

say "looking for java 17+"
JAVA_BIN="$(find_java)"
[ -z "$JAVA_BIN" ] && die "no java 17+ found. install one (brew install openjdk@17) and re-run."
echo "   $JAVA_BIN ($("$JAVA_BIN" -version 2>&1 | head -1))"

# --- get the jar -------------------------------------------------------------

TMP_DIR="$(mktemp -d)" || die "could not create a temp dir"
trap 'rm -rf "$TMP_DIR"' EXIT
JAR="$TMP_DIR/AndroidDeviceManager.jar"

if [ -n "$LOCAL_JAR" ]; then
    [ -f "$LOCAL_JAR" ] || die "no such jar: $LOCAL_JAR"
    cp "$LOCAL_JAR" "$JAR" || die "could not copy $LOCAL_JAR"
    say "using $LOCAL_JAR"
else
    if [ -n "$TAG" ]; then
        API="https://api.github.com/repos/$REPO/releases/tags/$TAG"
    else
        API="https://api.github.com/repos/$REPO/releases/latest"
    fi
    say "asking github for the release"
    JSON=$(curl -fsSL --retry 3 --max-time 60 -H 'Accept: application/vnd.github+json' "$API") ||
        die "could not reach $API - check the network, or pass --jar with a manual download"

    FIELDS=$(printf '%s' "$JSON" | tr ',' '\n')
    TAG=$(printf '%s' "$FIELDS" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
    URL=$(printf '%s' "$FIELDS" |
        sed -n 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"\([^"]*\.jar\)".*/\1/p' | head -1)
    [ -z "$URL" ] && die "release ${TAG:-?} has no .jar asset - download one manually and pass --jar"

    echo "   release : ${TAG:-?}"
    echo "   asset   : $URL"
    say "downloading"
    curl -fL --progress-bar --retry 3 --max-time 600 -o "$JAR" "$URL" || die "download failed"
fi

unzip -l "$JAR" "$MAIN_CLASS_PATH" >/dev/null 2>&1 || die "that jar has no $MAIN_CLASS_PATH in it"
VERSION=$(unzip -p "$JAR" app.properties 2>/dev/null | sed -n 's/^version=//p' | tr -d '\r')
[ -z "$VERSION" ] && VERSION="${TAG:-unknown}"
echo "   version : $VERSION"

# --- build the .app ----------------------------------------------------------

say "building $APP_DIR"
mkdir -p "$DEST_DIR" || die "cannot write to $DEST_DIR"
assert_ours "$APP_DIR"
rm -rf "$APP_DIR"
mkdir -p "$APP_DIR/Contents/MacOS" "$APP_DIR/Contents/Resources" || die "cannot create the bundle"
cp "$JAR" "$APP_DIR/Contents/Resources/AndroidDeviceManager.jar" || die "cannot copy the jar in"

# reuse the icon from the jdeploy-installed app if it is still around
ICON_LINE=""
ICNS=$(find "$HOME/Applications" /Applications -maxdepth 4 -name '*.icns' -path '*evice*anager*' 2>/dev/null | head -1)
if [ -n "$ICNS" ]; then
    cp "$ICNS" "$APP_DIR/Contents/Resources/icon.icns" 2>/dev/null &&
        ICON_LINE='    <key>CFBundleIconFile</key><string>icon.icns</string>'
fi

cat >"$APP_DIR/Contents/MacOS/run" <<EOF
#!/bin/bash
# generated by install-mac-app.sh - runs the bundled jar with a local JRE
RES="\$(cd "\$(dirname "\$0")/../Resources" && pwd)"
LOG="$LOG_FILE"
mkdir -p "\$(dirname "\$LOG")"

for J in "$JAVA_BIN" "\$(/usr/libexec/java_home -v 17 2>/dev/null)/bin/java" "\$(command -v java)"; do
    [ -x "\$J" ] && JAVA="\$J" && break
done

if [ -z "\$JAVA" ]; then
    osascript -e 'display alert "Android Device Manager" message "No Java 17 runtime found on this Mac."' 2>/dev/null
    exit 1
fi

echo "--- \$(date): \$JAVA" >>"\$LOG"
exec "\$JAVA" -Xdock:name="$APP_NAME" -jar "\$RES/AndroidDeviceManager.jar" "\$@" >>"\$LOG" 2>&1
EOF
chmod +x "$APP_DIR/Contents/MacOS/run" || die "could not make the launcher executable"

cat >"$APP_DIR/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>$APP_NAME</string>
    <key>CFBundleDisplayName</key><string>$APP_NAME</string>
    <key>CFBundleExecutable</key><string>run</string>
    <key>CFBundleIdentifier</key><string>com.jpage4500.devicemanager.local</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>$VERSION</string>
    <key>CFBundleVersion</key><string>$VERSION</string>
    <key>NSHighResolutionCapable</key><true/>
$ICON_LINE
    <key>CFBundleDocumentTypes</key>
    <array>
        <dict>
            <key>CFBundleTypeName</key><string>apk</string>
            <key>CFBundleTypeRole</key><string>Viewer</string>
            <key>CFBundleTypeExtensions</key><array><string>apk</string><string>xapk</string></array>
        </dict>
    </array>
</dict>
</plist>
EOF

xattr -cr "$APP_DIR" 2>/dev/null
codesign -s - --force --deep "$APP_DIR" >/dev/null 2>&1
touch "$APP_DIR"

# --- optional: seed jdeploy's own cache --------------------------------------

if [ $SEED_JDEPLOY -eq 1 ]; then
    say "seeding jdeploy's cache (experimental)"
    JD_APP=$(find "$HOME/Applications" /Applications -maxdepth 2 -iname '*device*manager*.app' 2>/dev/null |
        grep -v "^$APP_DIR\$" | head -1)
    if [ -z "$JD_APP" ]; then
        echo "   no jdeploy-installed .app found - skipping"
    else
        ID=$(defaults read "$JD_APP/Contents/Info" CFBundleIdentifier 2>/dev/null)
        MD5=$(echo "$ID" | sed -n 's/.*apps\.\([0-9a-f]\{32\}\)\..*/\1/p')
        if [ -z "$MD5" ]; then
            echo "   could not read the cache hash from $ID - skipping"
        else
            CACHE="$HOME/.jdeploy/gh-packages-x64/$MD5.$PKG_NAME"
            mkdir -p "$CACHE/$VERSION/jdeploy-bundle"
            cp "$JAR" "$CACHE/$VERSION/jdeploy-bundle/AndroidDeviceManager.jar"
            PKG_JSON="{\"name\":\"$PKG_NAME\",\"version\":\"$VERSION\",\"jdeploy\":{\"jar\":\"jdeploy-bundle/AndroidDeviceManager.jar\",\"javaVersion\":\"17\",\"jdk\":false,\"title\":\"$APP_NAME\"}}"
            echo "$PKG_JSON" >"$CACHE/$VERSION/package.json"
            echo "$PKG_JSON" >"$CACHE/package.json"
            echo "   wrote $CACHE"

            # pin the version so the launcher stops resolving 'latest' over the network
            if [ -f "$JD_APP/Contents/app.xml" ]; then
                [ -f "$JD_APP/Contents/app.xml.bak" ] ||
                    cp "$JD_APP/Contents/app.xml" "$JD_APP/Contents/app.xml.bak"
                sed -i '' "s/version='[^']*'/version='$VERSION'/" "$JD_APP/Contents/app.xml" 2>/dev/null &&
                    echo "   pinned app.xml to $VERSION (backup: app.xml.bak)"
            fi
            echo "   now try opening the original app; if it still fails, use the one below"
        fi
    fi
fi

# --- done --------------------------------------------------------------------

say "installed"
echo "   app     : $APP_DIR"
echo "   version : $VERSION"
echo "   java    : $JAVA_BIN"
echo "   log     : $LOG_FILE"
echo
echo "   open it from Finder, or:  open -a \"$APP_DIR\""
echo "   update later:             ./$(basename "$0")"
echo "   remove:                   ./$(basename "$0") --uninstall"
