#!/bin/bash

ADB_DEVICE=$1

# options:
# --stay-awake
# --always-on-top
# --encoder ['OMX.qcom.video.encoder.avc', 'c2.android.avc.encoder', 'OMX.google.h264.encoder']

scrcpy -s $ADB_DEVICE -p $RANDOM --show-touches --stay-awake --encoder 'c2.android.avc.encoder'
