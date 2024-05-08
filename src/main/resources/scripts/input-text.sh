#!/bin/bash
###############################################################################
# enter text input
# param: TEXT
###############################################################################
ADB_DEVICE=$1
TEXT=$2

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "sending: ${TEXT}"

${ADB} -s "${ADB_DEVICE}" shell input text \"${TEXT}\"

