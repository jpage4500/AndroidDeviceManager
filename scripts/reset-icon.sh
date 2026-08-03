#!/bin/bash

echo "clear cached icons?"
read n

sudo rm -rf /Library/Caches/com.apple.iconservices.store
sudo find /private/var/folders -name com.apple.dock.iconcache -delete
sudo find /private/var/folders -name com.apple.iconservices -exec rm -rf {} +
killall Dock; killall Finder
