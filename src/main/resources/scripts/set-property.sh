#!/bin/bash

ADB_DEVICE=$1
PROP=$2
VALUE=$3

FILENAME=android_device_manager.properties
# use device specific name locally to make thread safe
LOCAL_FILE=/tmp/$FILENAME.$ADB_DEVICE

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

# NOTE: setprop values get reset after a device reboot
# - if device is rooted, we can use "persist.x.y" values
#${ADB} -s ${ADB_DEVICE} shell "su -c setprop ${PROP} ${VALUE}"

# get properties file from device
rm "$LOCAL_FILE" >/dev/null 2>&1
adb -s "$ADB_DEVICE" pull /sdcard/$FILENAME "$LOCAL_FILE" >/dev/null
if [ ! $? -eq 0 ]; then
    echo "FILE NOT FOUND: $FILENAME" 1>&2
    # create new file
    touch "$LOCAL_FILE"
fi

if grep -R "^[#]*\s*${PROP}=.*" "$LOCAL_FILE" >/dev/null; then
    #echo "SETTING because '${PROP}' found already"
    # TODO: ensure only 1 instnace of PROP exists
    grep -v "^[#]*\s*${PROP}=.*" "$LOCAL_FILE" > "$LOCAL_FILE".bak
    mv "$LOCAL_FILE".bak "$LOCAL_FILE"
fi

echo "$PROP=$VALUE" >>"$LOCAL_FILE"

# push file back to device
adb -s "$ADB_DEVICE" push "$LOCAL_FILE" /sdcard/$FILENAME >/dev/null

echo "set ${PROP} ${VALUE} on device ${ADB_DEVICE}"
