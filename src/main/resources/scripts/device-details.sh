#!/bin/bash

ADB_DEVICE=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

function serviceCall() {
    START=$1
    END=$2
    REGEX='^[0-9+]+$'

    while [ $START -le $END ]; do
        VAL=$(${ADB} -s $ADB_DEVICE shell "service call iphonesubinfo $START | cut -c 52-66 | tr -d '.[:space:]'")
        # make sure value is a valid number
        if [[ ${#VAL} -ge 10 ]] && [[ $VAL =~ $REGEX ]]; then
            echo $VAL
            break
        fi
        #echo "ignoring $START - val:$VAL, len:${#VAL}"
        let START=($START + 1)
    done
    # echo "service:$ARG = $VAL"
}

function printPhoneNumber() {
    ${ADB} -s $ADB_DEVICE shell "service call iphonesubinfo 15" | cut -c 50-66 | tr -d '.[:space:]' | tr -d -c 0-9
}

function getProp() {
    PROP=$1
    ${ADB} -s $ADB_DEVICE shell getprop $PROP
}

function getAppVersion() {
    PACKAGE=$1
    NAME=$(${ADB} -s $ADB_DEVICE shell dumpsys package $PACKAGE | grep versionName | sed 's/    versionName=//')
    CODE=$(${ADB} -s $ADB_DEVICE shell dumpsys package $PACKAGE | grep versionCode | sed 's/    versionCode=//' | sed 's/ .*//')
    echo "$NAME $CODE"
}

function getFreeSpace() {
    # get available/free space in readable format
    #${ADB} -s $ADB_DEVICE shell df -H | grep '^/dev/fuse' | sed 's/  */ /g' | cut -d ' ' -f 4
    ${ADB} -s $ADB_DEVICE shell df -H | tail -1 | sed 's/  */ /g' | cut -d ' ' -f 4
}

IMEI=$(serviceCall 1 5)
PHONE=$(printPhoneNumber)
FREE_SPACE=$(getFreeSpace)
CUSTOM1=$(./get-property.sh $ADB_DEVICE custom1)
CUSTOM2=$(./get-property.sh $ADB_DEVICE custom2)

echo "device: $ADB_DEVICE"
echo "phone: $PHONE"
echo "imei: $IMEI"
echo "carrier: $(getProp gsm.sim.operator.alpha)"
echo "release: $(getProp ro.build.version.release)"
echo "sdk: $(getProp ro.build.version.sdk)"
echo "free: ${FREE_SPACE}"
echo "custom1: $CUSTOM1"
echo "custom2: $CUSTOM2"

# loop through all other arguments (app package name's)
for APP_NAME in "${@:2}"
do
    echo "$APP_NAME: $(getAppVersion $APP_NAME)"
done