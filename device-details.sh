#!/bin/bash

ADB_DEVICE=$1

function serviceCall() {
    START=$1
    END=$2
    REGEX='^[0-9+]+$'

    while [ $START -le $END ]
    do
        VAL=$(adb -s $ADB_DEVICE shell "service call iphonesubinfo $START | cut -c 52-66 | tr -d '.[:space:]'")
        # make sure value is a valid number
        if [[ ${#VAL} -ge 10 ]] && [[ $VAL =~ $REGEX ]]; then
            echo $VAL
            break;
        fi
        #echo "ignoring $START - val:$VAL, len:${#VAL}"
        let START=($START + 1)
    done
    # echo "service:$ARG = $VAL"
}

IMEI=$(serviceCall 1 5)
PHONE=$(serviceCall 15 20)

echo "device: $ADB_DEVICE"
echo "phone: $PHONE"
echo "imei: $IMEI"
