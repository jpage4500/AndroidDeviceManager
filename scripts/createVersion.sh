#!/bin/bash

VERSION=1.0
COMMITS=$(git rev-list HEAD --count)

BUILD=src/main/java/com/jpage4500/devicemanager/Build.java

sed -i '' "s/versionName = .*/versionName = \"$VERSION.$COMMITS\";/g" $BUILD
sed -i '' "s/versionCode = .*/versionCode = $COMMITS;/g" $BUILD
