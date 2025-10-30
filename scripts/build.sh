#!/bin/bash

cd ..

mvn -B package --file pom.xml

echo "**********************************"
ls -al ./target/AndroidDeviceManager.jar