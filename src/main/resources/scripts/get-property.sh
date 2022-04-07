#!/bin/bash

ADB_DEVICE=$1
PROP=$2
filename=android_device_manager.properties

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

# NOTE: setprop values get reset after a device reboot
# - if device is rooted, we can use "persist.x.y" values
#${ADB} -s $ADB_DEVICE shell getprop $PROP

adb -s "$ADB_DEVICE" pull /sdcard/$filename . >/dev/null
if [ ! $? -eq 0 ]; then
    # echo "FILE NOT FOUND: $filename" 1>&2
    echo ""
    exit 1
fi

while IFS="=" read -r key value; do
    if [[ "$key" == "$PROP" ]]; then
        echo "$value"
    fi
done <"$filename"
