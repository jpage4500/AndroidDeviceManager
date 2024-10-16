#!/bin/bash
###############################################################################
# run custom script
# ARG1: script name (full path)
# ARG2-N: selected device serial numbers
###############################################################################

SCRIPT=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

${SCRIPT} "${@:2}"
