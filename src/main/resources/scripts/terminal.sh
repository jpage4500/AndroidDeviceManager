#!/bin/bash

ADB_DEVICE=$1

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

DIR=$(pwd)

function handleMacOSX() {
  if [[ -d /Applications/iTerm.app ]]; then
      echo "iTerm.app installed"
      osascript <<END
      tell application "iTerm2"
          activate
          tell current window
          create tab with default profile
              tell current session
                  write text "cd $DIR"
                  write text "shell.sh $ADB_DEVICE"
              end tell
          end tell
      end tell
END
  else
      echo "iTerm.app not installed"
      osascript <<END
      tell application "Terminal"
          activate
          tell application "System Events" to keystroke "t" using command down
          repeat while contents of selected tab of window 1 starts with linefeed
              delay 0.01
          end repeat
          do script "cd $DIR" in window 1
          do script "shell.sh $ADB_DEVICE" in window 1
      end tell
END
  fi
}

function handleLinux() {
    gnome-terminal -- bash -c "cd $DIR"; ${ADB} -s "$ADB_DEVICE" shell"; exec bash"
}

##
if [[ "$OSTYPE" == "darwin"* ]]; then
    # Mac OSX
    handleMacOSX
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    handleLinux
else
    echo "unknown OS: $OSTYPE"
    exit 1
fi

