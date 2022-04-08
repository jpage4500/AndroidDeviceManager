#!/bin/bash

ADB_DEVICE=$1
PROP=$2

FILENAME=android_device_manager.properties
# use device specific name locally to make thread safe
LOCAL_FILE=/tmp/$FILENAME.$ADB_DEVICE

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

# NOTE: setprop values get reset after a device reboot
# - if device is rooted, we can use "persist.x.y" values
#${ADB} -s $ADB_DEVICE shell getprop $PROP

rm "$LOCAL_FILE" >/dev/null 2>&1
adb -s "$ADB_DEVICE" pull /sdcard/$FILENAME "$LOCAL_FILE" >/dev/null
if [ ! $? -eq 0 ]; then
    # echo "FILE NOT FOUND: $FILENAME" 1>&2
    echo ""
    exit 1
fi

while IFS="=" read -r key value; do
    if [[ "$key" == "$PROP" ]]; then
        echo "$value"
        exit 0
    fi
done <"$LOCAL_FILE"
