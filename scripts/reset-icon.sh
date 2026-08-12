#!/bin/bash
# clear macOS icon caches and rebuild the LaunchServices db
# optional arg: path to an .app bundle to re-register explicitly

APP="$1"
LSREGISTER=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister

CACHE_DIR="$(getconf DARWIN_USER_CACHE_DIR)"
CACHE_DIR="${CACHE_DIR%/}"
if [ -z "$CACHE_DIR" ]; then
    echo "can't resolve DARWIN_USER_CACHE_DIR"
    exit 1
fi

read -r -p "clear cached icons? [y/N] " answer
case "$answer" in
    [yY] | [yY][eE][sS]) ;;
    *)
        echo "aborted"
        exit 1
        ;;
esac

# stop the icon daemons first - they hold the store open and write stale entries back
sudo killall iconservicesd iconservicesagent 2>/dev/null

sudo rm -rf /Library/Caches/com.apple.iconservices.store
rm -rf "$CACHE_DIR/com.apple.iconservices.store"
rm -rf "$CACHE_DIR/com.apple.iconservicesagent"
rm -f "$CACHE_DIR/com.apple.dock.iconcache"

# Dock/task-switcher icons come from the LaunchServices db, not just the pixel cache
"$LSREGISTER" -kill -r -domain local -domain system -domain user
if [ -n "$APP" ]; then
    touch "$APP"
    "$LSREGISTER" -f "$APP"
fi

killall Dock
killall Finder
echo "done - if the icon is still stale, log out and back in"
