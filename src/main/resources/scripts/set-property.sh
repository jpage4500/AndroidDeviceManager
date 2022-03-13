#!/bin/bash

ADB_DEVICE=$1
PROP=$2
VALUE=$3

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

${ADB} -s ${ADB_DEVICE} shell setprop ${PROP} ${VALUE}

echo "set ${PROP} ${VALUE} on device ${ADB_DEVICE}"