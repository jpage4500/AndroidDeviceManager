#!/bin/bash

ADB_DEVICE=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

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

    ${ADB} -s "$ADB_DEVICE" shell screencap -p /sdcard/screen.png
    ${ADB} -s "$ADB_DEVICE" pull /sdcard/screen.png "$FILENAME"
    ${ADB} -s "$ADB_DEVICE" shell rm /sdcard/screen.png

    # open in background
    # open -g $FILENAME
    open "$FILENAME"
}

#########################################################
# SCRIPT START
#########################################################
FOLDER=~/Screenshots
mkdir -p $FOLDER >/dev/null 2>&1
cd $FOLDER

takeScreenShot
