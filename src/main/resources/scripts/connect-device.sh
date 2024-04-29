#!/bin/bash
###############################################################################
# connect a device using adb wireless
# param: ip
###############################################################################
IP=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "connecting to: ${IP}..."

${ADB} connect "${IP}"

