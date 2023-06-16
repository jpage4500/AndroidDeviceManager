#!/bin/bash

export ADB=$(command -v adb)

# if ADB isn't set, find and set it
if [[ ${ADB} == "" ]]; then
    # try a few known paths to adb
    export ADB=$HOME/Library/Android/sdk/platform-tools/adb
    if [ ! -f $ADB ]; then
        echo "ADB not found!" 1>&2;
    fi
fi

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
