set ADB_DEVICE=%1
set DEVICE_NAME=%2

@REM options:
@REM --stay-awake
@REM --always-on-top
@REM --encoder ['OMX.qcom.video.encoder.avc', 'c2.android.avc.encoder', 'OMX.google.h264.encoder']
@REM ${SCRCPY} -s "$ADB_DEVICE" -p $RANDOM --window-title "$DEVICE_NAME" --show-touches --stay-awake

echo "running scrcpy with DEVICE:%ADB_DEVICE%, NAME:%DEVICE_NAME%"

start cmd /C scrcpy -s %ADB_DEVICE% --window-title "%DEVICE_NAME%" --show-touches --stay-awake --no-audio
