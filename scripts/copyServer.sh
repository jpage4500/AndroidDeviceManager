#!/bin/bash

cd ..

mvn -B package --file pom.xml

echo "**********************************"
ls -al ./target/AndroidDeviceManager.jar

# local network
#scp ./target/AndroidDeviceManager.jar jpage@192.168.0.95:/home/jpage

# tailscale
scp ./target/AndroidDeviceManager.jar jpage@100.92.248.93:/home/jpage