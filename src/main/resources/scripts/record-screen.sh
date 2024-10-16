#!/bin/bash

ADB_DEVICE=$1
OUTPUT_FILE=$2
DEVICE_NAME=$3

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
if [ -x "$(command -v shuf)" ]; then
    PORT="-p $(shuf -i 2000-65000 -n 1)"
elif [ -x "$(command -v gshuf)" ]; then
    PORT="-p $(gshuf -i 2000-65000 -n 1)"
fi

FOLDER=$(dirname "${OUTPUT_FILE}")
if [ ! -d "${FOLDER}" ]; then
    # TODO: we could create the folder..
    # mkdir -p $FOLDER >/dev/null 2>&1
    echo "folder ${FOLDER} does not exist!"
    exit 1
fi

# options: https://github.com/Genymobile/scrcpy?tab=readme-ov-file#user-documentation
# --stay-awake
# --always-on-top
# --encoder ['OMX.qcom.video.encoder.avc', 'c2.android.avc.encoder', 'OMX.google.h264.encoder']

echo "running scrcpy RECORD with DEVICE:${ADB_DEVICE}, PORT:${PORT}, NAME:${DEVICE_NAME}, FILE:${OUTPUT_FILE}"

# shellcheck disable=SC2086
${SCRCPY} -s "${ADB_DEVICE}" ${PORT} --window-title "Recording ${DEVICE_NAME}" \
    --show-touches --stay-awake --no-audio \
    --video-codec=h264 --video-encoder='OMX.google.h264.encoder' \
    --always-on-top \
    --window-x 1100 --window-y 10 --record "${OUTPUT_FILE}"
