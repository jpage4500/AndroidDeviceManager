#!/bin/bash
###############################################################################
# run custom script
# ARG1: device serial
# ARG2: script name (full path)
# ARG3: download directory
###############################################################################

ADB_DEVICE=$1
SCRIPT=$2
DOWNLOAD_DIR=$3

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

${SCRIPT} "${ADB_DEVICE}" "${DOWNLOAD_DIR}"
