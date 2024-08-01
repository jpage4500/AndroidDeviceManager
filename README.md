# AndroidDeviceManager

## Description ##
Java desktop app to manage multiple Android devices via adb

## Features ##
- View all connected (and wireless) devices
- Populates device **phone number, free space, IMEI, carrier** (if available)
- **Remote control** selected devices (requires [scrcpy](https://github.com/Genymobile/scrcpy))
- Capture **screenshots** of selected devices
- **Drag and drop an apk** to **install** on selected devices
- **Drag and drop a file** to **copy** to selected devices
- **File Explorer** / Browse filesystem of device
  - download and view folders/files
  - delete folders/files
  - root mode supported
- View **Device Logs**
  - NOTE: this is a work in progress!
- **Restart** selected devices
- Run **user-defined adb commands**
- Set and display custom properties on each device
- Start an **adb shell** session with selected devices
- **View version** of user-defined list of apps

## Screenshots ##
<img src="resources/screenshot-main.jpg" width="600" alt="devices">
<br>
<img src="resources/screenshot-mirror.jpg" width="600" alt="devices">
<br>
<img src="resources/screenshot-browse.jpg" width="300" alt="file explorer">
<br>
<img src="resources/screenshot-logs.jpg" width="600" alt="logs">
<br>

## Requirements

- Java SDK
  - min version 17; I'm using openjdk 22.0.1 2024-04-16
  - MacOSX -> Homebrew -> `brew install openjdk`
  - Linux - [link](https://www.digitalocean.com/community/tutorials/how-to-install-java-with-apt-on-ubuntu-22-04)
- **adb**
  - MacOSX -> homebrew -> `brew cask install android-platform-tools`
  - Linux -> `sudo apt install adb fastboot`
  - standalone adb tools can be found [here](https://developer.android.com/tools/releases/platform-tools)
- **scrcpy** - mirror a connected Android device ([https://github.com/Genymobile/scrcpy](https://github.com/Genymobile/scrcpy))
  - MacOSX -> homebrew -> `brew install scrcpy`
  - Linux -> see [link](https://github.com/Genymobile/scrcpy/blob/master/doc/linux.md#latest-version)
- make sure both `adb` and `scrcpy` are in the current PATH

## Run

- Download the latest release from here: [https://github.com/jpage4500/AndroidDeviceManager/releases](https://github.com/jpage4500/AndroidDeviceManager/releases)
  - Mac OSX Users:
    - get the packaged .app version: `AndroidDeviceManager-VERSION-OSX.zip`, extract and move to `/Applications` folder
  - Windows/Linux Users:
    - get the .jar version: `AndroidDeviceManager.jar`
    - run via command-line: `java -jar AndroidDeviceManager.jar`

## Build

[Build Instructions](BUILD.md)

## Use Cases ##

We want to manage a lot of Android devices and had previously used MDM (mobile device management) software such as **AirDroid** and **ScaleFusion**. These tools aren't free ($$) but more importantly trying to remote control/view an Android device was often a very slow and choppy experience.

So, instead we took a different approach. Instead of running MDM software on every individual Android device, we connected all of the devices to a single macbook laptop using multiple 16-port USB hubs. The Macbook is running [Splashtop](https://www.splashtop.com/) remote control software. I can now remote login and using Android Device Manager control all of the devices with very little to no lag.

---

Tested with 45 Android devices connected to 1 Macbook laptop (using multiple 16-port USB hubs)
