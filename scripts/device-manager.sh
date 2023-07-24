#!/bin/bash

JAR=AndroidDeviceManager.jar

# check if app is already running and if so, restart it
PID=$(ps -ef | grep ${JAR} | grep -v grep | tr -s ' ' | cut -d ' ' -f3)
if [ "${PID}" -eq "${PID}" ] 2>/dev/null
then
    echo "** running - restarting (${PID}) **"
    kill ${PID}
else
    echo "not running"
fi


# use the latest build version (if found)
if [ -f ~/Downloads/${JAR} ]; then
    echo "using latest version.."
    cd ~/Downloads
else
    # run from script directory
    cd "$(dirname $0)"
fi

java -jar ${JAR}


