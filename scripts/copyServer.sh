#!/bin/bash

cd ..

mvn -B package --file pom.xml

echo "**********************************"
ls -al ./target/AndroidDeviceManager.jar

echo "copying jar to remote servers.."
# local network
scp ./target/AndroidDeviceManager.jar jpage@192.168.0.160:/home/jpage

# tailscale
#scp ./target/AndroidDeviceManager.jar jpage@100.92.248.93:/home/jpage

# Telegrabber #2
scp ./target/AndroidDeviceManager.jar tg-manager@100.127.250.116:/Users/tg-manager