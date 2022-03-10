#!/bin/bash

ADB_DEVICE=$1
PROP=$2

adb shell setprop

function getProp() {
    PROP=$1
}

IMEI=$(serviceCall 1 5)
PHONE=$(serviceCall 15 20)

echo "device: $ADB_DEVICE"
echo "phone: $PHONE"
echo "imei: $IMEI"
echo "carrier: $(getProp gsm.sim.operator.alpha)"
echo "custom1: $(getProp debug.dm.custom1)"
echo "custom2: $(getProp debug.dm.custom2)"
