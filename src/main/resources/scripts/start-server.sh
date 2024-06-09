#!/bin/bash

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

echo "############################################################"
echo "START SERVER"
echo "############################################################"
${ADB} start-server
