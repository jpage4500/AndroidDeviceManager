#!/bin/bash

APP=AndroidDeviceManager

cd "$(dirname $0)"
cd ..

COMMITS=$(git rev-list HEAD --count)

cd target
rm -rf *.zip *.app *.jar
cd ..

# mvn clean
mvn install -Drevision=1.0.$COMMITS

if [ ! -d target/$APP.app ]; then
    echo "target/$APP.app not found!"
    exit 1
fi

cd target

VERSION=$(cat classes/app.properties | grep version | cut -d= -f2)
echo "version: $VERSION"

zip -r $APP-$VERSION-OSX.zip $APP.app

# copy to /Applications folder
rm -rf /Applications/$APP.app
cp -Rf $APP.app /Applications