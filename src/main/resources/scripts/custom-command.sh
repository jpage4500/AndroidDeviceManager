#!/bin/bash

ADB_DEVICE=$1
ARGS=("$@")

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

${ADB} -s "$ADB_DEVICE" ${@:2}
