#!/bin/bash

cd ..

mvn compile exec:java -Dexec.args="logs"
