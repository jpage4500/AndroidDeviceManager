#!/bin/bash

cd ..

mvn -B package --file pom.xml -Drevision=1.0.$(git rev-list --count HEAD)

echo "**********************************"
ls -al ./target/AndroidDeviceManager.jar