#!/bin/bash

ADB_DEVICE=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

function getPhoneNumber() {
    var=$(${ADB} -s $ADB_DEVICE shell "service call iphonesubinfo 15 s16 'com.android.shell'" | cut -d "'" -f '2' -s | tr -d -s '.[:cntrl:]' '[:space:]')
    if [[ "$var" == "" ]]; then
        var=$(${ADB} -s $ADB_DEVICE shell "service call iphonesubinfo 12 s16 'com.android.shell'" | cut -d "'" -f '2' -s | tr -d -s '.[:cntrl:]' '[:space:]')
        if [[ "$var" == "" ]]; then
            # alternate way to get phone
            var=$(getProp debug.app.phone)
        fi
    fi
    echo $var
}

function getImei() {
    var=$(${ADB} -s $ADB_DEVICE shell service call iphonesubinfo 1 s16 'com.android.shell' | cut -d "'" -f '2' -s | tr -d -s '.[:cntrl:]' '[:space:]')
    if [[ "$var" == "" ]]; then
        # alternate way to get IMEI
        var=$(getProp debug.app.imei)
    fi
    echo $var
}

function getProp() {
    PROP=$1
    ${ADB} -s $ADB_DEVICE shell getprop $PROP
}

function getAppVersion() {
    PACKAGE=$1
    NAME=$(${ADB} -s $ADB_DEVICE shell dumpsys package $PACKAGE | grep versionName | sed 's/    versionName=//')
    # CODE=$(${ADB} -s $ADB_DEVICE shell dumpsys package $PACKAGE | grep versionCode | sed 's/    versionCode=//' | sed 's/ .*//')
    # echo "$NAME $CODE"
    echo "$NAME"
}

function getFreeSpace() {
    # get available/free space in readable format
    ${ADB} -s $ADB_DEVICE shell df -H | tail -1 | sed 's/  */ /g' | cut -d ' ' -f 4
}

IMEI=$(getImei)
PHONE=$(getPhoneNumber)
FREE_SPACE=$(getFreeSpace)
CUSTOM1=$(./get-property.sh $ADB_DEVICE custom1)
CUSTOM2=$(./get-property.sh $ADB_DEVICE custom2)

echo "device: $ADB_DEVICE"
echo "phone: $PHONE"
echo "imei: $IMEI"
echo "carrier: $(getProp gsm.sim.operator.alpha)"
echo "release: $(getProp ro.build.version.release)"
echo "model: $(getProp ro.product.model)"
echo "sdk: $(getProp ro.build.version.sdk)"
echo "free: ${FREE_SPACE}"
echo "custom1: $CUSTOM1"
echo "custom2: $CUSTOM2"

# loop through all other arguments (app package name's)
for APP_NAME in "${@:2}"
do
    echo "$APP_NAME: $(getAppVersion $APP_NAME)"
done