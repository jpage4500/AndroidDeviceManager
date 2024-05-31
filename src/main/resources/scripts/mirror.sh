#!/bin/bash

ADB_DEVICE=$1
DEVICE_NAME=$2

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

export SCRCPY=$(command -v scrcpy)

# if scrcpy isn't set, find and set it
if [[ ${SCRCPY} == "" ]]; then
    # try a few known paths
    if [ -f /opt/homebrew/bin/scrcpy ]; then
        export SCRCPY=/opt/homebrew/bin/scrcpy
    elif [ -f /usr/local/bin/scrcpy ]; then
        export SCRCPY=/usr/local/bin/scrcpy
    else
        echo "scrcpy not found!" 1>&2;
    fi
fi

# use random port to allow multiple instances
PORT=$(jot -r 1  2000 65000)

# options: https://github.com/Genymobile/scrcpy?tab=readme-ov-file#user-documentation
# --stay-awake
# --always-on-top
# --encoder ['OMX.qcom.video.encoder.avc', 'c2.android.avc.encoder', 'OMX.google.h264.encoder']

# shellcheck disable=SC2086
${SCRCPY} -s "${ADB_DEVICE}" -p ${PORT} --window-title "${DEVICE_NAME}" --show-touches --stay-awake --no-audio
