set ADB_DEVICE=%1

REM options:
REM --stay-awake
REM --always-on-top
REM --encoder ['OMX.qcom.video.encoder.avc', 'c2.android.avc.encoder', 'OMX.google.h264.encoder']

REM ${SCRCPY} -s "$ADB_DEVICE" -p $RANDOM --window-title "$DEVICE_NAME" --show-touches --stay-awake

start cmd /C scrcpy -s %ADB_DEVICE% --show-touches --stay-awake

