# AndroidDeviceManager

## Description ##
Java desktop app to manage many connected Android devices

## Features ##
- View all connected (and wireless) devices
- Populates device **phone number, free space, IMEI, carrier** (when available)
- **Remote control** selected devices (requires [scrcpy](https://github.com/Genymobile/scrcpy))
- Capture **screenshots** of selected devices
- **Drag and drop a apk** to **install** on selected devices
- **Drag and drop a file** to **copy** to selected devices
- **Restart** selected devices
- Run **user-defined adb commands**
- Set and display custom properties on each device (ie: label each device using device properties)
- Start an **adb shell** session with selected devices
- **View version** of user-defined list of apps

## Requirements ##
- java to run this app
- adb must be installed
- [scrcpy](https://github.com/Genymobile/scrcpy) installed to remote control devices
- Mac OS needed but could be modified to support other platforms

## Usage ##
- download the latest release and unzip to a folder. There should be 2 files: AndroidDeviceManager.jar and run-device-manager.sh
- run `./run-device-manager.sh`

## Screenshots ##
![](resources/screenshot-mirror.jpg)
![](resources/screenshot-install.jpg)
![](resources/screenshot-copy.jpg)
![](resources/screenshot-rightclick.jpg)

---
Tested with 45 Android devices connected to 1 Macbook laptop (using multiple 16-port hubs)
