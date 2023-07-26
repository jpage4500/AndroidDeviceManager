#!/bin/bash

APP=AndroidDeviceManager.app

cd "$(dirname $0)"
cd ..

rm -rf target/$APP

# mvn clean
mvn install

if [ ! -d target/$APP ]; then
    echo "target/$APP not found!"
    exit 1
fi


