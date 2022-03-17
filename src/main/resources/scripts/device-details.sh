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

IMEI=$(serviceCall 1 5)
PHONE=$(serviceCall 15 20)

echo "device: $ADB_DEVICE"
echo "phone: $PHONE"
echo "imei: $IMEI"
echo "carrier: $(getProp gsm.sim.operator.alpha)"
echo "custom1: $(getProp debug.dm.custom1)"
echo "custom2: $(getProp debug.dm.custom2)"

# loop through all other arguments (app package name's)
for APP_NAME in "${@:2}"
do
    echo "$APP_NAME: $(getAppVersion $APP_NAME)"
done