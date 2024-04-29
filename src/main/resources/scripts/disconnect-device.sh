#!/bin/bash
###############################################################################
# disconnect a device connected via adb wireless
# param: ip
###############################################################################
IP=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "disconnecting from: ${IP}..."

${ADB} disconnect "${IP}"

