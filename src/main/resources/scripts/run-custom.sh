#!/bin/bash
###############################################################################
# run custom script
# ARG1: script name (full path)
# ARG*: serial number
###############################################################################

SCRIPT=$1
shift
DEVICES="$@"

cd "$(/usr/bin/dirname $0)"
source ./env-vars.sh

function handleMacOSX() {
  if [[ -d /Applications/iTerm.app ]]; then
      echo "using iTerm"
      open /Applications/iTerm.app
      osascript <<END
      tell application "iTerm2"
          activate
          tell current window
          create tab with default profile
              tell current session
                  write text "${SCRIPT} ${DEVICES}"
              end tell
          end tell
      end tell
END
  else
      echo "using Terminal"
      osascript <<END
      open -a Terminal
      tell application "Terminal"
          activate
          tell application "System Events" to keystroke "t" using command down
          repeat while contents of selected tab of window 1 starts with linefeed
              delay 0.01
          end repeat
          do script "${SCRIPT} ${DEVICES}" in window 1
      end tell
END
  fi
}

function handleLinux() {
    # TODO: TEST
    gnome-terminal -- bash -c "${SCRIPT} ${DEVICES}"
}

###############################################################################
## START ##
###############################################################################

# check if script exists
if [[ ! -f "${SCRIPT}" ]]; then
    echo "script not found: ${SCRIPT}"
    exit 1
fi
# check if script is executable
if [[ ! -x "${SCRIPT}" ]]; then
    echo "script is not executable: ${SCRIPT}"
    chmod +x "${SCRIPT}"
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
    handleMacOSX
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    handleLinux
else
    echo "unknown OS: $OSTYPE"
    exit 1
fi
