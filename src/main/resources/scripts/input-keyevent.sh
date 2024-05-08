#!/bin/bash
###############################################################################
# input key events
# param: KEYCODE
###############################################################################
ADB_DEVICE=$1
KEYCODE=$2

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "sending: ${KEYCODE}"

${ADB} -s "${ADB_DEVICE}" shell input keyevent ${KEYCODE}

