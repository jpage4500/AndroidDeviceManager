#!/bin/bash

ADB_DEVICE=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "############################################################"
echo "Device: $ADB_DEVICE"
echo "############################################################"
${ADB} -s "$ADB_DEVICE" shell
