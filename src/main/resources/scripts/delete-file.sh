#!/bin/bash

ADB_DEVICE=$1
SRC_PATH=$2
SRC_NAME=$3

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "deleting file: ${SRC_PATH}/${SRC_NAME}"

# need to escape quotes (https://android.stackexchange.com/a/85228)
${ADB} -s "${ADB_DEVICE}" shell "rm -rf \"${SRC_PATH}/${SRC_NAME}\""
