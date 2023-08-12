#!/bin/bash

ADB_DEVICE=$1
SRC_PATH=$2
SRC_NAME=$3

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "deleting file: ${SRC_PATH}/${SRC_NAME}"

${ADB} -s "${ADB_DEVICE}" shell "rm -rf ${SRC_PATH}/${SRC_NAME}"
