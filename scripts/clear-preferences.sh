#!/bin/bash


if [[ "$OSTYPE" == "darwin"* ]]; then
    # Mac OSX
    PREFS_FILE=~/Library/Preferences/com.apple.java.util.prefs.plist
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    PREFS_FILE=~/.java/.userprefs/prefs.xml
else
    echo "unknown OS: $OSTYPE"
    exit 1
fi

# clear out all Java Preferences
if [ -f $PREFS_FILE ]; then
    echo "removing $PREFS_FILE.."
    rm $PREFS_FILE

    USER=$(id -un)
    echo "clearing cache for $USER.."
    killall -u $USER cfprefsd
else
    echo "Preferences not found: $PREFS_FILE"
fi
