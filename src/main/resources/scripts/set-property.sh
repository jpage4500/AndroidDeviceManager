#!/bin/bash

ADB_DEVICE=$1
PROP=$2
VALUE=$3
filename=android_device_manager.properties

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

# NOTE: setprop values get reset after a device reboot
# - if device is rooted, we can use "persist.x.y" values
#${ADB} -s ${ADB_DEVICE} shell "su -c setprop ${PROP} ${VALUE}"

# get properties file from device
adb -s "$ADB_DEVICE" pull /sdcard/$filename . >/dev/null
if [ ! $? -eq 0 ]; then
    echo "FILE NOT FOUND: $filename" 1>&2
    # create new file
    touch $filename
fi

# set or update file
if ! grep -R "^[#]*\s*${PROP}=.*" $filename >/dev/null; then
    echo "APPENDING because '${PROP}' not found"
    echo "$PROP=$VALUE" >>$filename
else
    echo "SETTING because '${PROP}' found already"
    sed -ir "s/^[#]*\s*${PROP}=.*/$PROP=$VALUE/" $filename
fi

# push file back to device
adb -s "$ADB_DEVICE" push $filename /sdcard/$filename >/dev/null

echo "set ${PROP} ${VALUE} on device ${ADB_DEVICE}"
