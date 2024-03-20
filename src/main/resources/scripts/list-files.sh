#!/bin/bash

ADB_DEVICE=$1
FOLDER=$2

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

#if [[ ${FOLDER} == "" ]]; then
#    echo "invalid folder"
#    exit 1
#fi

#echo "LISTING: ${FOLDER}"

${ADB} -s "$ADB_DEVICE" shell "ls -al ${FOLDER}"
# ${ADB} -s "$ADB_DEVICE" shell "su -c ls -al ${FOLDER}"

#if [ $? == 1 ]; then
#  # check if root exists on device
#  echo "PERMISSION DENIED!"
#  ${ADB} -s "$ADB_DEVICE" shell "su -c ls -al ${FOLDER}"
#fi


