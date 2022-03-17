#!/bin/bash

ADB_DEVICE=$1
PATH=$2

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "copying file: ${PATH} to device: ${ADB_DEVICE}"

${ADB} -s ${ADB_DEVICE} push ${PATH} /sdcard
