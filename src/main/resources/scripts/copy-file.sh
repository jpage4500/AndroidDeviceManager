#!/bin/bash

ADB_DEVICE=$1
PATH=$2
DEST=$3

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "copying file: ${PATH} to ${DEST}"

${ADB} -s "${ADB_DEVICE}" push "${PATH}" "${DEST}"
