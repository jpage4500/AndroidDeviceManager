#!/bin/bash

ADB_DEVICE=$1
SRC_PATH=$2
SRC_NAME=$3
DEST=$4

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "downloading: ${SRC_PATH}/${SRC_NAME} to ${DEST}"

${ADB} -s "${ADB_DEVICE}" pull "${SRC_PATH}/${SRC_NAME}" "${DEST}"
