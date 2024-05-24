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

VERSION=$(ls -t ${APP}*.jar | head -1 | sed "s/${APP}-//g" | sed 's/.jar//g')

zip -r $APP-$VERSION.zip $APP.app

# copy to /Applications folder
rm -rf /Applications/$APP.app
cp -Rf $APP.app /Applications