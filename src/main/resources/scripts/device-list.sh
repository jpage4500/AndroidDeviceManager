#!/bin/bash

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

$ADB devices -l
