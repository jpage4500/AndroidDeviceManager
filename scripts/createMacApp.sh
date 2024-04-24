#!/bin/bash

APP=AndroidDeviceManager
COMMITS=$(git rev-list HEAD --count)

cd "$(dirname $0)"
cd ..

cd target
rm -rf *.zip *.app *.jar
cd ..

# mvn clean
mvn install

if [ ! -d target/$APP.app ]; then
    echo "target/$APP.app not found!"
    exit 1
fi

cd target

zip -r $APP-1.0.$COMMITS.zip $APP.app

# copy to /Applications folder
cp -r $APP.app /Applications