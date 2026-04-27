#!/bin/bash

cd ..

mvn compile exec:java -Dexec.args="logs"

# alternative ways to open logs only mode:
#open -a "Android Device Manager" --args logs
#open "adm://logs"
