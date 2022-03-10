#!/bin/bash

ADB_DEVICE=$1

function takeScreenShot() {
    NOW=$(date +"%Y%m%d-%H%M%p")
    # find unused filename
    for i in {1..100}; do
        FILENAME=$NOW-$i.png
        if [ ! -f $FILENAME ]; then
            break
        fi
    done

    echo "saving to $FILENAME.."

    adb -s $ADB_DEVICE shell screencap -p /sdcard/screen.png
    adb -s $ADB_DEVICE pull /sdcard/screen.png $FILENAME
    adb -s $ADB_DEVICE shell rm /sdcard/screen.png

    # open in background
    # open -g $FILENAME
    open $FILENAME
}

#########################################################
# SCRIPT START
#########################################################
FOLDER=~/Screenshots
mkdir -p $FOLDER >/dev/null 2>&1
cd $FOLDER

takeScreenShot
