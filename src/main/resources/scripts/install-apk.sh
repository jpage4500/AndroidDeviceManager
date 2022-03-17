#!/bin/bash

ADB_DEVICE=$1
PATH=$2

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "installing: ${PATH} to device: ${ADB_DEVICE}"

${ADB} -s "${ADB_DEVICE}" install -r "${PATH}"

#if [ $? -eq 0 ]; then
#    echo "Error"
#fi