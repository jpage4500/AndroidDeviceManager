#!/bin/bash

cd ..

echo "** mvn package **"
mvn package # build the fat jar to target/

echo "** npx jdeploy package **"
npx jdeploy package

echo "** npx jdeploy install **"
npx jdeploy install # installs launchers + symlinks to your local jdeploy install dir
