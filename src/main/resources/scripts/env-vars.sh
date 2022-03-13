#!/bin/bash

export ADB=$(command -v adb)

if [[ ${ADB} == "" ]]; then
    # try a few known paths to adb
    export ADB=$HOME/Library/Android/sdk/platform-tools/adb
    if [ ! -f $ADB ]; then
        echo "ADB not found!" 1>&2;
    fi
fi
