#!/bin/bash

ADB_DEVICE=$1
DEVICE_NAME=$2

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

# options:
# --stay-awake
# --always-on-top
# --encoder ['OMX.qcom.video.encoder.avc', 'c2.android.avc.encoder', 'OMX.google.h264.encoder']

scrcpy -s "$ADB_DEVICE" -p $RANDOM --window-title "$DEVICE_NAME" --show-touches --stay-awake
