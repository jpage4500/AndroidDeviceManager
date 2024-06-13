#!/bin/bash

PREFS=com.apple.java.util.prefs.plist
PREFS_FULL=~/Library/Preferences/$PREFS

# clear out all Preferences (NOTE: only tested on a Mac)

if [ -f $PREFS_FULL ]; then
    echo "removing $PREFS_FULL.."
    rm $PREFS_FULL

    USER=$(id -un)
    echo "clearing cache for $USER.."
    killall -u $USER cfprefsd
else
    echo "Preferences not found: $PREFS_FULL"
fi