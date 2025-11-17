#!/bin/bash

cd ..

mvn -B package --file pom.xml

echo "**********************************"
ls -al ./target/AndroidDeviceManager.jar

scp ./target/AndroidDeviceManager.jar jpage@192.168.0.95:/home/jpage

