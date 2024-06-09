#!/bin/bash

export ADB=$(command -v adb)

# if ADB isn't set, find and set it
if [[ ${ADB} == "" ]]; then
    # try a few known paths to adb
    if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        export ADB=$HOME/Library/Android/sdk/platform-tools/adb
    elif [ -f "/opt/homebrew/bin/adb" ]; then
        export ADB=/opt/homebrew/bin/adb
    else
        echo "ADB not found!" 1>&2;
        exit 1
    fi
fi

function selectDevice() {
    # if ADB_DEVICE not set (ie: run from command-line) - set it to the first listed device
    if [[ ${ADB_DEVICE} == "" ]]; then
        DEVICES=$(${ADB} devices -l | grep -v "List" | grep -v "offline" | grep -v '^$' | sed 's/ device:.*//')
        NUM_DEVICES=`echo "${DEVICES}" | wc -l`
        IFS=$'\n'
        for device in $DEVICES
        do
            export ADB_DEVICE=$(echo "$device" | sed $'s/[ \t].*$//g')
            # echo "using device: ${ADB_DEVICE}"
            break
        done
    fi
}